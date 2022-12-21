package com.jieli.otasdk_java.tool;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.ParcelUuid;
import android.text.TextUtils;

import com.jieli.component.utils.PreferencesHelper;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_bt_ota.util.CommonUtil;
import com.jieli.otasdk_java.MainApplication;
import com.jieli.otasdk_java.tool.ota.OTAManager;
import com.jieli.otasdk_java.tool.ota.ble.BleManager;
import com.jieli.otasdk_java.tool.ota.ble.interfaces.BleEventCallback;
import com.jieli.otasdk_java.tool.ota.ble.model.BleScanInfo;
import com.jieli.otasdk_java.tool.ota.spp.SppManager;
import com.jieli.otasdk_java.tool.ota.spp.interfaces.SppEventCallback;
import com.jieli.otasdk_java.util.AppUtil;
import com.jieli.otasdk_java.util.JL_Constant;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @ClassName: DevScanPresenter
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 10:04
 */
public class DevScanPresenter implements IDeviceContract.IDevScanPresenter {
    private BleManager bleManager = BleManager.getInstance();
    private SppManager sppManager = SppManager.getInstance();
    private int interval = 0;

    private int communicationWay =
            PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
                    .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);
    private boolean useSppPrivateChannel =
            PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
                    .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL);
    public static final int MAX_INTERVAL = 12;
    private IDeviceContract.IDevScanView view;

    public DevScanPresenter(IDeviceContract.IDevScanView view) {
        this.view = view;
    }

    @Override
    public void start() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.registerBleEventCallback(mBleEventCallback);
        } else {
            sppManager.registerSppEventCallback(mSppEventCallback);
        }

    }

    @Override
    public void destroy() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.unregisterBleEventCallback(mBleEventCallback);
        } else {
            sppManager.unregisterSppEventCallback(mSppEventCallback);
        }
    }

    @Override
    public boolean isScanning() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.isBleScanning();
        } else {
            return sppManager.isScanning();
        }
    }

    @Override
    public void startScan() {
        if (BluetoothUtil.isBluetoothEnable()) {
            if (communicationWay == JL_Constant.PROTOCOL_BLE) {
                bleManager.startLeScan(12000);
            } else {
                sppManager.startDeviceScan(12000);
            }
        } else {
            AppUtil.enableBluetooth();
        }
    }

    @Override
    public void stopScan() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.stopLeScan();
        } else {
            sppManager.stopDeviceScan();
        }
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.getConnectedBtDevice();
        } else {
            return sppManager.getConnectedSppDevice();
        }
    }

    @Override
    public void connectBtDevice(BluetoothDevice device) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            if (bleManager.getConnectedBtDevice() != null) {
                bleManager.disconnectBleDevice(bleManager.getConnectedBtDevice());
            } else {
                bleManager.connectBleDevice(device);
            }
        } else {
            sppManager.connectSpp(device);
        }
    }

    @Override
    public void disconnectBtDevice(BluetoothDevice device) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.disconnectBleDevice(device);
        } else {
            sppManager.disconnectSpp(device, null);
        }
    }

    private void stopEdrScan() {
        if (interval > 0) {
            interval = 0;
            view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null);
            CommonUtil.getMainHandler().removeCallbacks(mScanSppDevice);
        }
    }

    public boolean deviceHasProfile(BluetoothDevice device, UUID uuid) {
        if (!BluetoothUtil.isBluetoothEnable()) {
            return false;
        } else if (device == null) {
            return false;
        } else if (TextUtils.isEmpty(uuid.toString())) {
            return false;
        } else {
            boolean ret = false;
            ParcelUuid[] uuids = device.getUuids();
            if (uuids == null) {
                return false;
            }
            for (ParcelUuid uid : uuids) {
                if (uuid.toString().toLowerCase(Locale.getDefault()) == uid.toString()) {
                    ret = true;
                    break;
                }
            }
            return ret;
        }
    }

    private Runnable mScanSppDevice = new Runnable() {
        @Override
        public void run() {
            if (interval == 0) {
                view.onScanStatus(JL_Constant.SCAN_STATUS_SCANNING, null);
            }
            List<BluetoothDevice> connectedDeviceList = BluetoothUtil.getSystemConnectedBtDeviceList();
            if (connectedDeviceList != null && !connectedDeviceList.isEmpty()) {
                ArrayList edrList = new ArrayList<BluetoothDevice>();
                for (BluetoothDevice device : connectedDeviceList) {
                    int devType = device.getType();
                    boolean isHasA2dp = deviceHasProfile(device, JL_Constant.UUID_A2DP);
                    boolean isHasSpp = deviceHasProfile(device, JL_Constant.UUID_SPP);
                    if (devType != BluetoothDevice.DEVICE_TYPE_LE
                            && isHasA2dp && isHasSpp
                    ) {
                        boolean isContains = edrList.contains(device);
                        if (!isContains) {
                            edrList.add(device);
                            view.onScanStatus(JL_Constant.SCAN_STATUS_FOUND_DEV, device);
                        }
                    }
                }
            }
            if (interval >= MAX_INTERVAL) {
                interval = 0;
                view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null);
            } else {
                interval++;
                CommonUtil.getMainHandler().postDelayed(this, 1000);
            }
        }
    };


    private void handleAdapterStatus(boolean bEnabled) {
        if (bEnabled) {
            startScan();
        } else {
            stopEdrScan();
            view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null);
            view.onConnectStatus(
                    getConnectedDevice(),
                    StateCode.CONNECTION_DISCONNECT
            );
        }
    }

    private void handleDiscoveryStatus(boolean bStart) {
        view.onScanStatus(bStart ? JL_Constant.SCAN_STATUS_SCANNING : JL_Constant.SCAN_STATUS_IDLE, null);
        if (bStart && getConnectedDevice() != null) {
            view.onScanStatus(
                    JL_Constant.SCAN_STATUS_FOUND_DEV,
                    getConnectedDevice()
            );
        }
    }

    private void handleDiscoveryDevice(BluetoothDevice device) {
        if (device != null && BluetoothUtil.isBluetoothEnable()) {
            view.onScanStatus(JL_Constant.SCAN_STATUS_FOUND_DEV, device);
        }
    }

    private void handleConnection(BluetoothDevice device, int status) {
        view.onConnectStatus(device, status);
    }

    private BleEventCallback mBleEventCallback = new BleEventCallback() {
        @Override
        public void onAdapterChange(boolean bEnabled) {
            handleAdapterStatus(bEnabled);
        }

        @Override
        public void onDiscoveryBleChange(boolean bStart) {
            handleDiscoveryStatus(bStart);
        }

        @Override
        public void onDiscoveryBle(BluetoothDevice device, BleScanInfo bleScanMessage) {
            handleDiscoveryDevice(device);
        }

        @Override
        public void onBleConnection(BluetoothDevice device, int status) {
            handleConnection(device, OTAManager.changeConnectStatus(status));
        }
    };

    private SppEventCallback mSppEventCallback = new SppEventCallback() {
        @Override
        public void onDiscoveryDeviceChange(boolean bStart) {
            handleDiscoveryStatus(bStart);
        }

        @Override
        public void onDiscoveryDevice(BluetoothDevice device, int rssi) {
            handleDiscoveryDevice(device);
        }

        @Override
        public void onSppConnection(BluetoothDevice device, UUID uuid, int status) {
            if (useSppPrivateChannel && status == BluetoothProfile.STATE_CONNECTED && SppManager.UUID_CUSTOM_SPP != uuid) {
//                sppManager.connectSpp(device, SppManager.UUID_RCSP_SPP)
                return;
            }
            handleConnection(device, OTAManager.changeConnectStatus(status));
        }
    };
}
