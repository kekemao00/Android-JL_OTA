package com.jieli.otasdk.tool.ota.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jieli.jl_bt_ota.constant.BluetoothConstant;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_bt_ota.util.CHexConver;
import com.jieli.jl_bt_ota.util.CommonUtil;
import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.jl_bt_ota.util.PreferencesHelper;
import com.jieli.otasdk.MainApplication;
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback;
import com.jieli.otasdk.tool.ota.ble.interfaces.IBleOp;
import com.jieli.otasdk.tool.ota.ble.interfaces.OnThreadStateListener;
import com.jieli.otasdk.tool.ota.ble.interfaces.OnWriteDataCallback;
import com.jieli.otasdk.tool.ota.ble.model.BleScanInfo;
import com.jieli.otasdk.util.AppUtil;
import com.jieli.otasdk.util.JL_Constant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Ble连接管理类
 *
 * @author zqjasonZhong
 * @since 2020/7/16
 */
public class BleManager implements IBleOp {
    private final static String TAG = BleManager.class.getSimpleName();
    private final Context mContext;
    @SuppressLint("StaticFieldLeak")
    private volatile static BleManager instance;

    private BaseBtAdapterReceiver mAdapterReceiver;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothDevice mConnectingBtDevice;
    private BluetoothDevice mConnectedBtDevice;
    private volatile BluetoothGatt mConnectedBtGatt;
    private final List<BluetoothDevice> mDiscoveredBleDevices = new ArrayList<>();
    private final BleEventCallbackManager mCallbackManager = new BleEventCallbackManager();

    private volatile boolean isBleScanning;
    private volatile int mBleMtu = BluetoothConstant.BLE_MTU_MIN;
    private SendBleDataThread mSendBleDataThread;
    private NotifyCharacteristicRunnable mNotifyCharacteristicRunnable;
    private String mReconnectDevAddr;

    public final static UUID BLE_UUID_SERVICE = BluetoothConstant.UUID_SERVICE;
    public final static UUID BLE_UUID_WRITE = BluetoothConstant.UUID_WRITE;
    public final static UUID BLE_UUID_NOTIFICATION = BluetoothConstant.UUID_NOTIFICATION;

    /**
     * 发送数据最大超时 - 8 秒
     */
    public final static int SEND_DATA_MAX_TIMEOUT = 8000; //8 s
    private final static int SCAN_BLE_TIMEOUT = 8 * 1000;
    private final static int CONNECT_BLE_TIMEOUT = 40 * 1000;
    private static final long DELAY_WAITING_TIME = 5000L;
    private final static int CALLBACK_TIMEOUT = 3000;
    private final static int RECONNECT_BLE_DELAY = 2000;

    private final static int MSG_SCAN_BLE_TIMEOUT = 0x1010;
    private final static int MSG_CONNECT_BLE_TIMEOUT = 0x1011;
    private final static int MSG_SCAN_HID_DEVICE = 0X1012;
    private final static int MSG_NOTIFY_BLE_TIMEOUT = 0x1013;
    private final static int MSG_CHANGE_BLE_MTU_TIMEOUT = 0x1014;
    private final static int MSG_BLE_DISCOVER_SERVICES_CALLBACK_TIMEOUT = 0x1015;
    private final static int MSG_RECONNECT_BLE = 0x1016;
    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_SCAN_BLE_TIMEOUT:
                    if (isBleScanning) {
                        stopLeScan();
                    }
                    break;
                case MSG_CONNECT_BLE_TIMEOUT:
                    if (mConnectedBtDevice == null) {
                        handleBleConnection(mConnectingBtDevice, BluetoothProfile.STATE_DISCONNECTED);
                        setConnectingBtDevice(null);
                    }
                    break;
                case MSG_SCAN_HID_DEVICE:
                    List<BluetoothDevice> lists = BluetoothUtil.getSystemConnectedBtDeviceList();
                    if (null != lists) {
                        for (BluetoothDevice device : lists) {
                            if (device.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC &&
                                    device.getBondState() == BluetoothDevice.BOND_BONDED) {
                                handleDiscoveryBle(device, null);
                            }
                        }
                    }
                    mHandler.sendEmptyMessageDelayed(MSG_SCAN_HID_DEVICE, 1000);
                    break;
                case MSG_NOTIFY_BLE_TIMEOUT:
                    if (mConnectedBtDevice == null) {
                        disconnectBleDevice(mConnectingBtDevice);
                    }
                    break;
                case MSG_CHANGE_BLE_MTU_TIMEOUT:
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    JL_Log.i(TAG, "-MSG_CHANGE_BLE_MTU_TIMEOUT- request mtu timeout, device : " + BluetoothUtil.printBtDeviceInfo(device));
                    if (BluetoothUtil.deviceEquals(device, getConnectedBtDevice())) {
                        handleBleConnectedEvent(device);
                    } else {
                        handleBleConnection(device, BluetoothProfile.STATE_DISCONNECTED);
                    }
                    break;
                case MSG_BLE_DISCOVER_SERVICES_CALLBACK_TIMEOUT:
                    if (msg.obj instanceof BluetoothDevice) {
                        BluetoothDevice connectedBleDev = (BluetoothDevice) msg.obj;
                        if (BluetoothUtil.deviceEquals(connectedBleDev, mConnectedBtDevice)) {
                            boolean isNeedDisconnect = true;
                            if (mConnectedBtGatt != null) {
                                List<BluetoothGattService> services = mConnectedBtGatt.getServices();
                                if (services != null && services.size() > 0) {
                                    mBluetoothGattCallback.onServicesDiscovered(mConnectedBtGatt, BluetoothGatt.GATT_SUCCESS);
                                    isNeedDisconnect = false;
                                }
                            }
                            if (isNeedDisconnect) {
                                JL_Log.d(TAG, "discover services timeout.");
                                disconnectBleDevice(connectedBleDev);
                                setReconnectDevAddr(connectedBleDev.getAddress());
                            }
                        }
                    }
                    break;
                case MSG_RECONNECT_BLE:
                    if (msg.obj instanceof BluetoothDevice) {
                        BluetoothDevice reconnectBleDev = (BluetoothDevice) msg.obj;
                        if (reconnectDevice(reconnectBleDev)) {
                            setReconnectDevAddr(null);
                        }
                    }
                    break;
            }
            return false;
        }
    });


    private BleManager(Context context) {
        mContext = CommonUtil.checkNotNull(context);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        registerReceiver();
    }

    public static BleManager getInstance() {
        if (instance == null) {
            synchronized (BleManager.class) {
                if (instance == null) {
                    instance = new BleManager(MainApplication.getInstance());
                }
            }
        }
        return instance;
    }

    /**
     * 获取已连接的BLE设备列表
     *
     * @param context 上下文
     * @return 已连接的BLE设备列表
     */
    public static List<BluetoothDevice> getConnectedBleDeviceList(Context context) {
        if (context == null) return null;
        BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager != null) {
            return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        }
        return null;
    }

    public void destroy() {
        JL_Log.w(TAG, ">>>>>>>>>>>>>>destroy >>>>>>>>>>>>>>> ");
        if (isBleScanning()) {
            stopLeScan();
        }
        disconnectBleDevice(getConnectedBtDevice());
        unregisterReceiver();
        stopConnectTimeout();
        stopSendDataThread();
        isBleScanning(false);
        mDiscoveredBleDevices.clear();
        mCallbackManager.release();
        mHandler.removeCallbacksAndMessages(null);
        instance = null;
    }

    public void registerBleEventCallback(BleEventCallback callback) {
        mCallbackManager.registerBleEventCallback(callback);
    }

    public void unregisterBleEventCallback(BleEventCallback callback) {
        mCallbackManager.unregisterBleEventCallback(callback);
    }

    public boolean isBluetoothEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public boolean enableBluetooth() {
        if (null == mBluetoothAdapter) return false;
        boolean ret = mBluetoothAdapter.isEnabled();
        if (!ret) {
            ret = mBluetoothAdapter.enable();
        }
        return ret;
    }

    public boolean disableBluetooth() {
        if (null == mBluetoothAdapter) return false;
        boolean ret = !mBluetoothAdapter.isEnabled();
        if (!ret) {
            ret = mBluetoothAdapter.disable();
        }
        return ret;
    }

    public boolean isBleScanning() {
        return isBleScanning;
    }

    public boolean startLeScan(long timeout) {
        if (null == mBluetoothAdapter) return false;
        if (!isBluetoothEnable()) return false;
        if (timeout <= 0) {
            timeout = SCAN_BLE_TIMEOUT;
        }
        if (isBleScanning) {
            JL_Log.i(TAG, "scanning ble .....");
            if (mBluetoothLeScanner != null) {
                mBluetoothLeScanner.flushPendingScanResults(mScanCallback);
            }
            mDiscoveredBleDevices.clear();
            mHandler.removeMessages(MSG_SCAN_BLE_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_SCAN_BLE_TIMEOUT, timeout);
            syncSystemBleDevice();
            return true;
        }
        if (!AppUtil.isHasLocationPermission(mContext)) {
            JL_Log.i(TAG, "App does not have location permission.");
            return false;
        }
        boolean ret;
        if (Build.VERSION.SDK_INT >= LOLLIPOP && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.startScan(mScanCallback);
            ret = true;
        } else {
            ret = mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        isBleScanning(ret);
        if (ret) {
            mDiscoveredBleDevices.clear();
            mHandler.removeMessages(MSG_SCAN_BLE_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_SCAN_BLE_TIMEOUT, timeout);
            syncSystemBleDevice();
        }
        return ret;
    }

    public void stopLeScan() {
        if (null == mBluetoothAdapter || !isBluetoothEnable()) return;
        try {
            if (Build.VERSION.SDK_INT >= LOLLIPOP && mBluetoothLeScanner != null) {
                mBluetoothLeScanner.stopScan(mScanCallback);
            } else {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mHandler.removeMessages(MSG_SCAN_BLE_TIMEOUT);
        mHandler.removeMessages(MSG_SCAN_HID_DEVICE);
        isBleScanning(false);
    }

    public BluetoothDevice getConnectedBtDevice() {
        return mConnectedBtDevice;
    }

    public BluetoothGatt getConnectedBtGatt() {
        return mConnectedBtGatt;
    }

    public void setReconnectDevAddr(String mReconnectDevAddr) {
        this.mReconnectDevAddr = mReconnectDevAddr;
        if (mReconnectDevAddr == null) {
            mHandler.removeMessages(MSG_RECONNECT_BLE);
        }
        if (isBluetoothEnable() && !isBleScanning()) {
            startLeScan(12000);
        }
        if (PreferencesHelper.getSharedPreferences(MainApplication.getInstance().getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)
                && BluetoothAdapter.checkBluetoothAddress(mReconnectDevAddr)) {
            List<BluetoothDevice> list = BluetoothUtil.getSystemConnectedBtDeviceList();
            if (null != list) {
                for (BluetoothDevice device : list) {
                    if (reconnectDevice(device)) {
                        JL_Log.i(TAG, "reconnect device start. 22222 ");
                        break;
                    }
                }
            }
        }
    }

    public int getBleMtu() {
        return mBleMtu;
    }

    public void connectBleDevice(BluetoothDevice device) {
        if (null == device) return;
        if (mConnectedBtDevice != null) {
            JL_Log.e(TAG, "BleDevice is connected, please call disconnectBleDevice method at first.");
            setReconnectDevAddr(null);
            return;
        }
        if (mConnectingBtDevice != null) {
            JL_Log.e(TAG, "BleDevice is connecting, please wait.");
            return;
        }
        if (isBleScanning()) {
            stopLeScan();
        }
        BluetoothGatt gatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(
                    mContext,
                    false,
                    mBluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE
            );
        } else {
            gatt = device.connectGatt(
                    mContext,
                    false,
                    mBluetoothGattCallback
            );
        }
        if (gatt != null) {
            setConnectedBtGatt(gatt);
            setConnectingBtDevice(device);
            handleBleConnection(device, BluetoothProfile.STATE_CONNECTING);
            startConnectTimeout();
            JL_Log.d(TAG, "connect start....");
        }
    }

    public void disconnectBleDevice(BluetoothDevice device) {
        if (null == device) return;
        if (BluetoothUtil.deviceEquals(device, mConnectedBtDevice)) {
            synchronized (this) {
                if (mConnectedBtGatt != null) {
                    mConnectedBtGatt.disconnect();
                    mConnectedBtGatt.close();
                }
                setConnectedBtGatt(null);
            }
            setConnectedBtDevice(null);
            handleBleConnection(device, BluetoothProfile.STATE_DISCONNECTED);
        }
    }

    public boolean writeDataByBle(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] data) {
        if (gatt == null || null == serviceUUID || null == characteristicUUID || null == data || data.length == 0) {
            JL_Log.d(TAG, "writeDataByBle : 1111111");
            return false;
        }
        BluetoothGattService gattService = gatt.getService(serviceUUID);
        if (null == gattService) {
            JL_Log.d(TAG, "writeDataByBle : 22222");
            return false;
        }
        BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(characteristicUUID);
        if (null == gattCharacteristic) {
            JL_Log.d(TAG, "writeDataByBle : 3333");
            return false;
        }
        boolean ret = false;
        try {
            gattCharacteristic.setValue(data);
            ret = gatt.writeCharacteristic(gattCharacteristic);
        } catch (Exception e) {
            e.printStackTrace();
        }
        JL_Log.d(TAG, "writeDataByBle : " + CHexConver.byte2HexStr(data) + ", ret : " + ret);
        return ret;
    }

    public void writeDataByBleAsync(BluetoothDevice device, UUID serviceUUID, UUID characteristicUUID, byte[] data, OnWriteDataCallback callback) {
        addSendTask(device, serviceUUID, characteristicUUID, data, callback);
    }

    private void isBleScanning(boolean isScanning) {
        isBleScanning = isScanning;
        mCallbackManager.onDiscoveryBleChange(isScanning);
        if (isBleScanning && PreferencesHelper.getSharedPreferences(MainApplication.getInstance().getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)) {
            mHandler.sendEmptyMessage(MSG_SCAN_HID_DEVICE);
        }
    }

    private void setConnectingBtDevice(BluetoothDevice mConnectingBtDevice) {
        this.mConnectingBtDevice = mConnectingBtDevice;
    }

    private void setConnectedBtDevice(BluetoothDevice mConnectedBtDevice) {
        this.mConnectedBtDevice = mConnectedBtDevice;
    }

    private void setConnectedBtGatt(BluetoothGatt mConnectedBtGatt) {
        synchronized (this) {
            this.mConnectedBtGatt = mConnectedBtGatt;
        }
    }

    private boolean isReConnectDevice(BluetoothDevice device) {
        return device != null && BluetoothAdapter.checkBluetoothAddress(mReconnectDevAddr) && mReconnectDevAddr.equals(device.getAddress());
    }

    private boolean reconnectDevice(BluetoothDevice device) {
        if (isReConnectDevice(device)) {
            if (mConnectingBtDevice == null) {
                JL_Log.i(TAG, "reconnect device start.");
                connectBleDevice(device);
                return true;
            }
        }
        return false;
    }

    private void filterDevice(BluetoothDevice device, int rssi, byte[] scanRecord, boolean isBleEnableConnect) {
        if (isBluetoothEnable() && !TextUtils.isEmpty(device.getName()) && !mDiscoveredBleDevices.contains(device)) {
            JL_Log.d(TAG, "notify device : " + BluetoothUtil.printBtDeviceInfo(device));
            mDiscoveredBleDevices.add(device);
            handleDiscoveryBle(device, new BleScanInfo().setRawData(scanRecord).setRssi(rssi).setEnableConnect(isBleEnableConnect));
        }
    }

    private void startConnectTimeout() {
        if (!mHandler.hasMessages(MSG_CONNECT_BLE_TIMEOUT)) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CONNECT_BLE_TIMEOUT, mConnectingBtDevice), CONNECT_BLE_TIMEOUT);
        }
    }

    private void stopConnectTimeout() {
        if (mHandler.hasMessages(MSG_CONNECT_BLE_TIMEOUT)) {
            mHandler.removeMessages(MSG_CONNECT_BLE_TIMEOUT);
        }
    }

    private void syncSystemBleDevice() {
        List<BluetoothDevice> mSysConnectedBleList = getConnectedBleDeviceList(mContext);
        if (mSysConnectedBleList != null && !mSysConnectedBleList.isEmpty()) {
            for (BluetoothDevice bleDev : mSysConnectedBleList) {
                if (!BluetoothUtil.deviceEquals(bleDev, mConnectedBtDevice)) {
                    if (!mDiscoveredBleDevices.contains(bleDev)) {
                        mDiscoveredBleDevices.add(bleDev);
                        handleDiscoveryBle(bleDev, new BleScanInfo().setEnableConnect(true));
                    }
                }
            }
        }
    }

    private void startSendDataThread() {
        if (mSendBleDataThread == null) {
            mSendBleDataThread = new SendBleDataThread(this, new OnThreadStateListener() {
                @Override
                public void onStart(long id, String name) {

                }

                @Override
                public void onEnd(long id, String name) {
                    mSendBleDataThread = null;
                }
            });
            mSendBleDataThread.start();
        }
    }

    private void stopSendDataThread() {
        if (mSendBleDataThread != null) {
            mSendBleDataThread.stopThread();
            mSendBleDataThread = null;
        }
    }

    private void addSendTask(BluetoothDevice device, UUID serviceUUID, UUID characteristicUUID, byte[] data, OnWriteDataCallback callback) {
        boolean ret = false;
        if (mSendBleDataThread != null && mConnectedBtGatt != null && BluetoothUtil.deviceEquals(device, mConnectedBtGatt.getDevice())) {
            ret = mSendBleDataThread.addSendTask(getConnectedBtGatt(), serviceUUID, characteristicUUID, data, callback);
        }
        if (!ret) {
            callback.onBleResult(device, serviceUUID, characteristicUUID, false, data);
        }
    }

    private void wakeupSendThread(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, int status, byte[] data) {
        if (mSendBleDataThread != null) {
            SendBleDataThread.BleSendTask task = new SendBleDataThread.BleSendTask(gatt, serviceUUID, characteristicUUID, data, null);
            task.setStatus(status);
            mSendBleDataThread.wakeupSendThread(task);
        }
    }

    private void handleDiscoveryBle(final BluetoothDevice device, final BleScanInfo bleScanInfo) {
        if (!PreferencesHelper.getSharedPreferences(MainApplication.getInstance().getApplicationContext()).
                getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)
                && PreferencesHelper.getSharedPreferences(MainApplication.getInstance().getApplicationContext()).
                getBoolean(JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY, JL_Constant.NEED_CUSTOM_RECONNECT_WAY)) {
            if (reconnectDevice(device)) {
                JL_Log.i(TAG, "reconnect device start...3333");
            }
        }
        mCallbackManager.onDiscoveryBle(device, bleScanInfo);
    }

    private void handleBleConnection(final BluetoothDevice device, final int status) {
        if (status == BluetoothProfile.STATE_DISCONNECTED || status == BluetoothProfile.STATE_CONNECTED) {
            mHandler.removeMessages(MSG_NOTIFY_BLE_TIMEOUT);
            if (isReConnectDevice(device)) {
                setReconnectDevAddr(null);
            }
        }
        JL_Log.i(TAG, "handleBleConnection >> device : " + BluetoothUtil.printBtDeviceInfo(device) + ", status : " + status);
        mCallbackManager.onBleConnection(device, status);
    }

    /* ---- BroadcastReceiver Handler ---- */
    private void registerReceiver() {
        if (mAdapterReceiver == null) {
            mAdapterReceiver = new BaseBtAdapterReceiver();
            IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            mContext.registerReceiver(mAdapterReceiver, intentFilter);
        }
    }

    private void unregisterReceiver() {
        if (mAdapterReceiver != null) {
            mContext.unregisterReceiver(mAdapterReceiver);
            mAdapterReceiver = null;
        }
    }

    /**
     * 用于开启蓝牙BLE设备Notification服务
     *
     * @param gatt               被连接的ble Gatt服务对象
     * @param serviceUUID        服务UUID
     * @param characteristicUUID characteristic UUID
     * @return 结果 true 则等待系统回调BLE服务
     */
    private boolean enableBLEDeviceNotification(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID) {
        if (null == gatt) {
            JL_Log.w(TAG, "bluetooth gatt is null....");
            return false;
        }
        BluetoothGattService gattService = gatt.getService(serviceUUID);
        if (null == gattService) {
            JL_Log.w(TAG, "bluetooth gatt service is null....");
            return false;
        }
        BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUUID);
        if (null == characteristic) {
            JL_Log.w(TAG, "bluetooth characteristic is null....");
            return false;
        }
        boolean bRet = gatt.setCharacteristicNotification(characteristic, true);
        if (bRet) {
            List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
            for (BluetoothGattDescriptor descriptor : descriptors) {
                bRet = tryToWriteDescriptor(gatt, descriptor, 0, false);
                if (!bRet) {
                    JL_Log.w(TAG, "tryToWriteDescriptor failed....");
                }
            }
        } else {
            JL_Log.w(TAG, "setCharacteristicNotification is failed....");
        }
        JL_Log.w(TAG, "enableBLEDeviceNotification ret : " + bRet + ", serviceUUID : " + serviceUUID + ", characteristicUUID : " + characteristicUUID);
        return bRet;
    }

    private boolean tryToWriteDescriptor(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor descriptor, int retryCount, boolean isSkipSetValue) {
        boolean ret = isSkipSetValue;
        if (!ret) {
            ret = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            JL_Log.i(TAG, "..descriptor : .setValue  ret : " + ret);
            if (!ret) {
                retryCount++;
                if (retryCount >= 3) {
                    return false;
                } else {
                    JL_Log.i(TAG, "-tryToWriteDescriptor- : retryCount : " + retryCount + ", isSkipSetValue :  false");
                    SystemClock.sleep(50);
                    tryToWriteDescriptor(bluetoothGatt, descriptor, retryCount, false);
                }
            } else {
                retryCount = 0;
            }
        }
        if (ret) {
            ret = bluetoothGatt.writeDescriptor(descriptor);
            JL_Log.i(TAG, "..bluetoothGatt : .writeDescriptor  ret : " + ret);
            if (!ret) {
                retryCount++;
                if (retryCount >= 3) {
                    return false;
                } else {
                    JL_Log.i(TAG, "-tryToWriteDescriptor- 2222 : retryCount : " + retryCount + ", isSkipSetValue :  true");
                    SystemClock.sleep(50);
                    tryToWriteDescriptor(bluetoothGatt, descriptor, retryCount, true);
                }
            }
        }
        return ret;
    }


    //开始调整BLE协议MTU
    private void startChangeMtu(BluetoothGatt gatt, int mtu) {
        if (gatt == null) return;
        BluetoothDevice device = gatt.getDevice();
        if (device == null) return;
        if (mHandler.hasMessages(MSG_CHANGE_BLE_MTU_TIMEOUT)) {
            JL_Log.w(TAG, "-startChangeMtu- Adjusting the MTU for BLE");
            return;
        }
        boolean ret = false;
        if (mtu > BluetoothConstant.BLE_MTU_MIN) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ret = gatt.requestMtu(mtu + 3);
            } else {
                ret = true;
            }
        }
        if (ret) { //调整成功，开始超时任务
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHANGE_BLE_MTU_TIMEOUT, device), DELAY_WAITING_TIME);
        } else {
            handleBleConnectedEvent(device);
        }
    }

    //回收调整MTU的超时任务
    private void stopChangeMtu() {
        mHandler.removeMessages(MSG_CHANGE_BLE_MTU_TIMEOUT);
    }

    private void handleBleConnectedEvent(final BluetoothDevice device) {
        if (device == null) {
            JL_Log.e(TAG, "-handleBleConnectedEvent- device is null.");
            return;
        }
        stopChangeMtu();
        handleBleConnection(device, BluetoothProfile.STATE_CONNECTED);
    }

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = (device, rssi, scanRecord) -> filterDevice(device, rssi, scanRecord, true);

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result != null && result.getScanRecord() != null) {
                BluetoothDevice device = result.getDevice();
                boolean isBleEnableConnect = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    isBleEnableConnect = result.isConnectable();
                }
//                JL_Log.i("onScanResult",BluetoothUtil.printBtDeviceInfo(device));
                filterDevice(device, result.getRssi(), result.getScanRecord().getBytes(), isBleEnableConnect);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {

        }

        @Override
        public void onScanFailed(int errorCode) {
            JL_Log.d(TAG, "onScanFailed : " + errorCode);
            stopLeScan();
        }
    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        public void onConnectionUpdated(BluetoothGatt gatt, int interval, int latency, int timeout, int status) {
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            JL_Log.e(TAG, "onConnectionUpdated >> device : " + BluetoothUtil.printBtDeviceInfo(device) + ", interval : "
                    + interval + ", latency : " + latency + ", timeout : " + timeout + ", status : " + status);
            mCallbackManager.onConnectionUpdated(device, interval, latency, timeout, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (null == gatt) return;
            final BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            JL_Log.i(TAG, "onConnectionStateChange >> device : " + BluetoothUtil.printBtDeviceInfo(device)
                    + ", status : " + status + ", newState : " + newState);
            if (newState == BluetoothProfile.STATE_DISCONNECTED || newState == BluetoothProfile.STATE_DISCONNECTING
                    || newState == BluetoothProfile.STATE_CONNECTED) {
                stopConnectTimeout();
                setConnectingBtDevice(null);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    boolean ret = gatt.discoverServices();
                    JL_Log.d(TAG, "onConnectionStateChange >> discoverServices : " + ret);
                    setConnectedBtDevice(device);
                    if (ret) {
                        startSendDataThread();
                        mHandler.removeMessages(MSG_BLE_DISCOVER_SERVICES_CALLBACK_TIMEOUT);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_BLE_DISCOVER_SERVICES_CALLBACK_TIMEOUT, device), CALLBACK_TIMEOUT);
                    } else {
                        disconnectBleDevice(device);
                    }
                    return;
                } else {
                    setConnectedBtDevice(null);
                    setConnectedBtGatt(null);
                    gatt.close();
                    stopSendDataThread();

                    if (isReConnectDevice(device)) { //确认是回连设备
                        mHandler.removeMessages(MSG_RECONNECT_BLE);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RECONNECT_BLE, device), RECONNECT_BLE_DELAY);
                        return;
                    }
                }
            }
            handleBleConnection(device, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            mHandler.removeMessages(MSG_BLE_DISCOVER_SERVICES_CALLBACK_TIMEOUT);
            mCallbackManager.onBleServiceDiscovery(device, status, gatt.getServices());
            boolean ret = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothUtil.printBleGattServices(device, gatt, status);
                for (BluetoothGattService service : gatt.getServices()) {
                    if (BLE_UUID_SERVICE.equals(service.getUuid())
                            && null != service.getCharacteristic(BLE_UUID_WRITE)
                            && null != service.getCharacteristic(BLE_UUID_NOTIFICATION)) {
                        JL_Log.i(TAG, "start NotifyCharacteristicRunnable...");
                        mNotifyCharacteristicRunnable = new NotifyCharacteristicRunnable(gatt,
                                BLE_UUID_SERVICE, BLE_UUID_NOTIFICATION);
                        mHandler.post(mNotifyCharacteristicRunnable);
                        ret = true;
                        break;
                    }
                }
            }
            JL_Log.i(TAG, "onServicesDiscovered : " + ret);
            if (!ret) {
                disconnectBleDevice(device);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            if (null == characteristic) return;
            UUID serviceUUID = null;
            UUID characteristicUUID = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            BluetoothGattService gattService = characteristic.getService();
            if (gattService != null) {
                serviceUUID = gattService.getUuid();
            }
            JL_Log.d(TAG, "onCharacteristicChanged >> deice : " + BluetoothUtil.printBtDeviceInfo(device) + ", serviceUuid : " + serviceUUID
                    + ", characteristicUuid : " + characteristicUUID + ",\n data : " + CHexConver.byte2HexStr(data));
            mCallbackManager.onBleDataNotification(device, serviceUUID, characteristicUUID, data);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            if (null == characteristic) return;
            UUID serviceUUID = null;
            UUID characteristicUUID = characteristic.getUuid();
            BluetoothGattService gattService = characteristic.getService();
            if (gattService != null) {
                serviceUUID = gattService.getUuid();
            }
            JL_Log.d(TAG, "onCharacteristicWrite : status : " + status);
            wakeupSendThread(gatt, serviceUUID, characteristicUUID, status, characteristic.getValue());
            mCallbackManager.onBleWriteStatus(device, serviceUUID, characteristicUUID, characteristic.getValue(), status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            JL_Log.e(TAG, "onDescriptorWrite : gatt : " + gatt + ", descriptor : " + descriptor + ", status : " + status);
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            if (null == descriptor) return;
            UUID serviceUuid = null;
            UUID characteristicUuid = null;
            BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
            if (null != characteristic) {
                characteristicUuid = characteristic.getUuid();
                BluetoothGattService bluetoothGattService = characteristic.getService();
                if (null != bluetoothGattService) {
                    serviceUuid = bluetoothGattService.getUuid();
                }
            }
            mCallbackManager.onBleNotificationStatus(device, serviceUuid, characteristicUuid, status);
            if (mNotifyCharacteristicRunnable != null && BluetoothUtil.deviceEquals(device, mNotifyCharacteristicRunnable.getBleDevice())
                    && serviceUuid != null && serviceUuid.equals(mNotifyCharacteristicRunnable.getServiceUUID())
                    && characteristicUuid != null && characteristicUuid.equals(mNotifyCharacteristicRunnable.getCharacteristicUUID())) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mNotifyCharacteristicRunnable = null;
                    startChangeMtu(gatt, BluetoothConstant.BLE_MTU_MAX);
//                    handleBleConnectedEvent(device);
                } else {
                    int num = mNotifyCharacteristicRunnable.getRetryNum();
                    if (num < 3) {
                        mNotifyCharacteristicRunnable.setRetryNum(++num);
                        mHandler.postDelayed(mNotifyCharacteristicRunnable, 100);
                    } else {
                        disconnectBleDevice(device);
                    }
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (null == gatt) return;
            BluetoothDevice device = gatt.getDevice();
            if (null == device) return;
            if (BluetoothGatt.GATT_SUCCESS == status) {
                // 需要减去3个字节的数据包头部信息
                mBleMtu = mtu - 3;
            }
            JL_Log.d(TAG, "onMtuChanged - status = " + status + ", mtu = " + mtu + ",\ndevice: " + BluetoothUtil.printBtDeviceInfo(device));
            mCallbackManager.onBleDataBlockChanged(device, mtu, status);
            if (mHandler.hasMessages(MSG_CHANGE_BLE_MTU_TIMEOUT)) { //调整MTU的回调
                stopChangeMtu();
                JL_Log.i(TAG, "-onMtuChanged- handleBleConnectedEvent");
                handleBleConnectedEvent(device);
            }
        }
    };

    private class BaseBtAdapterReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (action == null) return;
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED: {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
                        if (mBluetoothAdapter != null && state == -1) {
                            state = mBluetoothAdapter.getState();
                        }
                        if (state == BluetoothAdapter.STATE_OFF) {
                            mCallbackManager.onAdapterChange(false);
                        } else if (state == BluetoothAdapter.STATE_ON) {
                            mCallbackManager.onAdapterChange(true);
                        }
                        break;
                    }
                    case BluetoothDevice.ACTION_ACL_CONNECTED: {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        JL_Log.i(TAG, "BaseBtAdapterReceiver: ACTION_ACL_CONNECTED, device : "
                                + BluetoothUtil.printBtDeviceInfo(device));
                        if (reconnectDevice(device)) {
                            JL_Log.i(TAG, "reconnectDevice start...1111");
                        }
                        break;
                    }
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        JL_Log.i(TAG, "BaseBtAdapterReceiver: ACTION_ACL_DISCONNECTED, device : "
                                + BluetoothUtil.printBtDeviceInfo(device));
                        break;
                    }

                }
            }
        }
    }

    private class NotifyCharacteristicRunnable implements Runnable {
        private final BluetoothGatt mGatt;
        private final UUID mServiceUUID;
        private final UUID mCharacteristicUUID;
        private int retryNum = 0;

        private NotifyCharacteristicRunnable(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID) {
            this.mGatt = gatt;
            this.mServiceUUID = serviceUUID;
            this.mCharacteristicUUID = characteristicUUID;
        }

        private void setRetryNum(int retryNum) {
            this.retryNum = retryNum;
        }

        private int getRetryNum() {
            return retryNum;
        }

        private BluetoothDevice getBleDevice() {
            if (mGatt == null) return null;
            return mGatt.getDevice();
        }

        private UUID getServiceUUID() {
            return mServiceUUID;
        }

        private UUID getCharacteristicUUID() {
            return mCharacteristicUUID;
        }

        @Override
        public void run() {
            boolean ret = enableBLEDeviceNotification(mGatt, mServiceUUID, mCharacteristicUUID);
            JL_Log.w(TAG, "enableBLEDeviceNotification ===> " + ret);
            if (!ret) {
                if (mGatt != null) {
                    disconnectBleDevice(mGatt.getDevice());
                }
            } else {
                mHandler.sendEmptyMessageDelayed(MSG_NOTIFY_BLE_TIMEOUT, 3000);
            }
        }
    }
}
