//package com.jieli.otasdk_java.tool.modifyOTA;
//
//import android.bluetooth.BluetoothDevice;
//
//import com.jieli.jl_bt_ota.util.JL_Log;
//import com.jieli.jl_rcsp.impl.RcspAuth;
//import com.jieli.otasdk.tool.ota.ble.BleManager;
//import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback;
//
//import java.util.UUID;
//
///**
// * @ClassName: ConnectAuthHandler
// * @Description: java类作用描述
// * @Author: ZhangHuanMing
// * @CreateDate: 2021/12/21 15:45
// */
//public class ConnectAuthHandler implements RcspAuth.IRcspAuthOp, RcspAuth.OnRcspAuthListener {
//    private String TAG = this.getClass().getSimpleName();
//    private RcspAuth mRcspAuth = new RcspAuth(this, this);
//    private RcspAuth.OnRcspAuthListener mOnRcspAuthListener;
//    private BleManager bleManager;
//    private BleEventCallback bleEventCallback = new BleEventCallback() {
//        @Override
//        public void onBleDataNotification(BluetoothDevice device, UUID serviceUuid, UUID characteristicsUuid, byte[] data) {
//            super.onBleDataNotification(device, serviceUuid, characteristicsUuid, data);
//            receiveAuthData(device, data);
//        }
//    };
//
//    public ConnectAuthHandler(BleManager bleManager) {
//        this.bleManager = bleManager;
//        bleManager.registerBleEventCallback(bleEventCallback);
//    }
//
//    @Override
//    public boolean sendAuthDataToDevice(BluetoothDevice bluetoothDevice, byte[] bytes) {
//        bleManager.writeDataByBleAsync(
//                bluetoothDevice,
//                BleManager.BLE_UUID_SERVICE,
//                BleManager.BLE_UUID_WRITE,
//                bytes, (device, serviceUUID, characteristicUUID, result, data) -> {
//                    JL_Log.i(TAG, "sendAuthDataToDevice");
//                }
//        );
//        return false;
//    }
//
//    @Override
//    public void onInitResult(boolean result) {
//        if (mOnRcspAuthListener != null) {
//            mOnRcspAuthListener.onInitResult(result);
//        }
//    }
//
//    @Override
//    public void onAuthSuccess(BluetoothDevice bluetoothDevice) {
//        if (mOnRcspAuthListener != null) {
//            mOnRcspAuthListener.onAuthSuccess(bluetoothDevice);
//        }
//    }
//
//    @Override
//    public void onAuthFailed(BluetoothDevice device, int errorCode, String errorMsg) {
//        if (mOnRcspAuthListener != null) {
//            mOnRcspAuthListener.onAuthFailed(device, errorCode, errorMsg);
//        }
//    }
//
//    public void setOnRcspAuthListener(RcspAuth.OnRcspAuthListener onRcspAuthListener) {
//        this.mOnRcspAuthListener = onRcspAuthListener;
//    }
//
//    public void startAuth(BluetoothDevice device) {
//        mRcspAuth.startAuth(device);
//    }
//
//    public void receiveAuthData(BluetoothDevice device, byte[] data) {
//        mRcspAuth.handleAuthData(device, data);
//    }
//
//    public void release() {
//        bleManager.unregisterBleEventCallback(bleEventCallback);
//    }
//}
