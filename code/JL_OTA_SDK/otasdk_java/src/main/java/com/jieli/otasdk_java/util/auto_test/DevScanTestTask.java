package com.jieli.otasdk_java.util.auto_test;

import android.bluetooth.BluetoothDevice;

import com.jieli.component.thread.ThreadManager;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.otasdk_java.tool.IDeviceContract;
import com.jieli.otasdk_java.util.JL_Constant;

/**
 * @ClassName: DevScanTestTask
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2022/2/21 14:30
 */
public class DevScanTestTask extends TestTask implements IDeviceContract.IDevScanView {
    private BluetoothDevice mDevice;
    private IDeviceContract.IDevScanPresenter mIDevScanPresenter;
    private boolean mDeviceIsDisconnect = true;

    public DevScanTestTask(BluetoothDevice device, IDeviceContract.IDevScanPresenter presenter, TestTaskFinishListener finishListener) {
        super(finishListener);
        mDevice = device;
        mIDevScanPresenter = presenter;
    }

    @Override
    public void start() {
        if (BluetoothUtil.deviceEquals(mDevice, mIDevScanPresenter.getConnectedDevice())) {//设备未断开
            taskFinish();
        } else {
            ThreadManager.getInstance().postRunnable(() -> mIDevScanPresenter.startScan());
        }
    }

    private void taskFinish() {
        finishListener.onFinish();
        ThreadManager.getInstance().postRunnable(() ->
                mIDevScanPresenter.stopScan());
        mIDevScanPresenter = null;
    }


    @Override
    public void onScanStatus(int status, BluetoothDevice device) {
        if (!mDeviceIsDisconnect) return;
        if (status == JL_Constant.SCAN_STATUS_FOUND_DEV) {
            if (BluetoothUtil.deviceEquals(device, mDevice)) {
                mDeviceIsDisconnect = false;
                mIDevScanPresenter.connectBtDevice(device);
            }
        } else if (status == JL_Constant.SCAN_STATUS_IDLE) {//扫描停止了
            mIDevScanPresenter.startScan();
        }
    }

    @Override
    public void onConnectStatus(BluetoothDevice device, int status) {
        if (BluetoothUtil.deviceEquals(device, this.mDevice)) {
            if (status == StateCode.CONNECTION_CONNECTING || status == StateCode.CONNECTION_CONNECTED) {
                mDeviceIsDisconnect = false;
            } else if (status == StateCode.CONNECTION_OK) {
                mDeviceIsDisconnect = false;
                taskFinish();
            } else if (status == StateCode.CONNECTION_FAILED || status == StateCode.CONNECTION_DISCONNECT) {
                mDeviceIsDisconnect = true;
            }
        }
    }

    @Override
    public void onMandatoryUpgrade() {

    }

    @Override
    public void onErrorCallback(int code, String message) {

    }

    @Override
    public void setPresenter(IDeviceContract.IDevScanPresenter presenter) {

    }
}
