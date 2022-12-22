package com.jieli.otasdk.tool.ota;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.jieli.jl_bt_ota.util.PreferencesHelper;
import com.jieli.jl_bt_ota.constant.BluetoothConstant;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager;
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure;
import com.jieli.jl_bt_ota.model.base.BaseError;
import com.jieli.jl_bt_ota.tool.DeviceReConnectManager;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_bt_ota.util.CHexConver;
import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.otasdk.MainApplication;
import com.jieli.otasdk.tool.ota.ble.BleManager;
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback;
import com.jieli.otasdk.tool.ota.ble.interfaces.OnWriteDataCallback;
import com.jieli.otasdk.tool.ota.spp.SppManager;
import com.jieli.otasdk.tool.ota.spp.interfaces.OnWriteSppDataCallback;
import com.jieli.otasdk.tool.ota.spp.interfaces.SppEventCallback;
import com.jieli.otasdk.util.JL_Constant;

import java.util.UUID;

/**
 * @ClassName: OTAManager
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 10:29
 */
public class OTAManager extends BluetoothOTAManager {
    private String TAG = this.getClass().getSimpleName();
    private BleManager bleManager = BleManager.getInstance();
    private SppManager sppManager = SppManager.getInstance();

    private int communicationWay =
            PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).
                    getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);

    public static int changeConnectStatus(int status) {
        int changeStatus = StateCode.CONNECTION_DISCONNECT;
        switch (status) {
            case BluetoothProfile.STATE_DISCONNECTED:
            case BluetoothProfile.STATE_DISCONNECTING:
                changeStatus =
                        StateCode.CONNECTION_DISCONNECT;
                break;
            case BluetoothProfile.STATE_CONNECTED:
                changeStatus = StateCode.CONNECTION_OK;
                break;
            case BluetoothProfile.STATE_CONNECTING:
                changeStatus = StateCode.CONNECTION_CONNECTING;
                break;
        }
        return changeStatus;
    }

    private BleEventCallback bleEventCallback = new BleEventCallback() {
        @Override
        public void onBleConnection(BluetoothDevice device, int status) {
            super.onBleConnection(device, status);
            JL_Log.i(TAG, "onBleConnection >>> device : " + BluetoothUtil.printBtDeviceInfo(device) + ", status ：" + status + ", change status : " + changeConnectStatus(status));
            onBtDeviceConnection(device, changeConnectStatus(status));
        }

        @Override
        public void onBleDataNotification(BluetoothDevice device, UUID serviceUuid, UUID characteristicsUuid, byte[] data) {
            super.onBleDataNotification(device, serviceUuid, characteristicsUuid, data);
            JL_Log.i(TAG, "onBleDataNotification >>> " + BluetoothUtil.printBtDeviceInfo(device) + ", data ：" + CHexConver.byte2HexStr(data));
            onReceiveDeviceData(device, data);

        }

        @Override
        public void onBleDataBlockChanged(BluetoothDevice device, int block, int status) {
            super.onBleDataBlockChanged(device, block, status);
            onMtuChanged(bleManager.getConnectedBtGatt(), block, status);
        }
    };
    private SppEventCallback sppEventCallback = new SppEventCallback() {
        @Override
        public void onSppConnection(BluetoothDevice device, UUID uuid, int status) {
            if (SppManager.UUID_SPP == uuid) {
                int newStatus = changeConnectStatus(status);
                JL_Log.i(TAG, "-onSppConnection- device = " + BluetoothUtil.printBtDeviceInfo(device) + ", uuid = " + uuid + ", status = " + newStatus);
                onBtDeviceConnection(device, newStatus);
            }
        }

        @Override
        public void onReceiveSppData(BluetoothDevice device, UUID uuid, byte[] data) {
            if (SppManager.UUID_SPP == uuid) {
                onReceiveDeviceData(device, data);
            }
        }
    };

    public OTAManager(Context context) {
        super(context);
        BluetoothOTAConfigure bluetoothOption = new BluetoothOTAConfigure();
        //选择通讯方式
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bluetoothOption.setPriority(BluetoothOTAConfigure.PREFER_BLE);
        } else {
            bluetoothOption.setPriority(BluetoothOTAConfigure.PREFER_SPP);
            //是否需要自定义回连方式(默认不需要，如需要自定义回连方式，需要客户自行实现)
            bluetoothOption.setUseReconnect((PreferencesHelper.getSharedPreferences(context)
                    .getBoolean(
                            JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                            JL_Constant.NEED_CUSTOM_RECONNECT_WAY
                    ) && PreferencesHelper.getSharedPreferences(context)
                    .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)));
        }
        //是否启用设备认证流程(与固件工程师确认)
        bluetoothOption.setUseAuthDevice(PreferencesHelper.getSharedPreferences(context)
                .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH));
        //设置BLE的MTU
        bluetoothOption.setMtu(BluetoothConstant.BLE_MTU_MIN);
        //是否需要改变BLE的MTU
        bluetoothOption.setNeedChangeMtu(false);
        //是否启用杰理服务器(暂时不支持)
        bluetoothOption.setUseJLServer(false);
        //配置OTA参数
        configure(bluetoothOption);
        bleManager.registerBleEventCallback(bleEventCallback);
        sppManager.registerSppEventCallback(sppEventCallback);
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            if (bleManager.getConnectedBtDevice() != null) {
                onBtDeviceConnection(bleManager.getConnectedBtDevice(), StateCode.CONNECTION_OK);
                onMtuChanged(
                        bleManager.getConnectedBtGatt(), bleManager.getBleMtu() + 3,
                        BluetoothGatt.GATT_SUCCESS
                );
            }
        } else {
            if (sppManager.getConnectedSppDevice() != null) {
                onBtDeviceConnection(sppManager.getConnectedSppDevice(), StateCode.CONNECTION_OK);
            }
        }
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        if (bleManager.getConnectedBtDevice()!=null) {
            return bleManager.getConnectedBtDevice();
        } else {
            return sppManager.getConnectedSppDevice();
        }
//        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
//            return bleManager.getConnectedBtDevice();
//        } else {
//            return sppManager.getConnectedSppDevice();
//        }
    }

    @Override
    public BluetoothGatt getConnectedBluetoothGatt() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.getConnectedBtGatt();
        } else {
            return null;
        }
    }

    @Override
    public void connectBluetoothDevice(BluetoothDevice device) {
//        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.connectBleDevice(device);
//        } else {
//            sppManager.connectSpp(device, SppManager.UUID_SPP);
//        }
    }

    @Override
    public void disconnectBluetoothDevice(BluetoothDevice bluetoothDevice) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.disconnectBleDevice(bluetoothDevice);
        } else {
            sppManager.disconnectSpp(bluetoothDevice, null);
        }
    }

    @Override
    public boolean sendDataToDevice(BluetoothDevice bluetoothDevice, byte[] bytes) {
//        val ret = bluetoothClient.jlBtManager.sendDataToDevice(bluetoothDevice, bytes)
//        JL_Log.d("zzc_bluetooth", "sendDataToDevice : ret = $ret")
        if (bluetoothDevice == null || bytes == null) return false;
        if (bleManager.getConnectedBtDevice()!=null/*communicationWay == JL_Constant.PROTOCOL_BLE*/) {
            bleManager.writeDataByBleAsync(bluetoothDevice,
                    BleManager.BLE_UUID_SERVICE,
                    BleManager.BLE_UUID_WRITE,
                    bytes, new OnWriteDataCallback() {
                        @Override
                        public void onBleResult(BluetoothDevice device, UUID serviceUUID, UUID characteristicUUID, boolean result, byte[] data) {
                            JL_Log.i(TAG, "-writeDataByBleAsync- result = " + result + ", device:" + BluetoothUtil.printBtDeviceInfo(device) + ", data:" + CHexConver.byte2HexStr(data));
                        }
                    });
        } else {
            sppManager.writeDataToSppAsync(
                    bluetoothDevice,
                    SppManager.UUID_SPP,
                    bytes, new OnWriteSppDataCallback() {
                        @Override
                        public void onSppResult(BluetoothDevice device, UUID sppUUID, boolean result, byte[] data) {
                            JL_Log.i(TAG, "-writeDataToSppAsync- device = " + BluetoothUtil.printBtDeviceInfo(device) + ", uuid = " + sppUUID + ", result = " + result + ", data = " + CHexConver.byte2HexStr(data));
                        }
                    }
            );
        }
        return true;
    }

    @Override
    public void errorEventCallback(BaseError error) {

    }

    @Override
    public void  release() {
        super.release();
        bleManager.unregisterBleEventCallback(bleEventCallback);
        sppManager.unregisterSppEventCallback(sppEventCallback);
//        bleManager.destroy()
    }

    public void setReconnectAddr(String addr) {
        if (BluetoothAdapter.checkBluetoothAddress(addr)) {
            DeviceReConnectManager.getInstance(this).setReconnectAddress(addr);
        }
    }
}
