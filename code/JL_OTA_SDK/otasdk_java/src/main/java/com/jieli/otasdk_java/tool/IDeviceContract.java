package com.jieli.otasdk_java.tool;

import android.bluetooth.BluetoothDevice;

import com.jieli.otasdk_java.base.BasePresenter;
import com.jieli.otasdk_java.base.BaseView;

/**
 * @ClassName: IDeviceContract
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:55
 */
public interface IDeviceContract {
    public interface IDevScanPresenter extends BasePresenter {

        public boolean isScanning();

        public void startScan();

        public void stopScan();

        public BluetoothDevice getConnectedDevice();

        public void connectBtDevice(BluetoothDevice device);

        public void disconnectBtDevice(BluetoothDevice device);
    }

    public interface IDevScanView extends BaseView<IDevScanPresenter> {
        public void onScanStatus(int status, BluetoothDevice device);

        public void onConnectStatus(BluetoothDevice device, int status);

        public void onMandatoryUpgrade();

        public void onErrorCallback(int code, String message);

    }
}
