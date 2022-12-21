package com.jieli.otasdk_java.util.auto_test;

import android.bluetooth.BluetoothDevice;

import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.interfaces.BtEventCallback;
import com.jieli.otasdk_java.tool.IOtaContract;

/**
 * @ClassName: OTATestTask
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2022/2/21 14:40
 */
public class OTATestTask extends TestTask implements IOtaContract.IOtaView {
    private String mFilePath;
    private IOtaContract.IOtaPresenter mOtaPresenter;

    public OTATestTask(String filePath, IOtaContract.IOtaPresenter presenter, TestTaskFinishListener listener) {
        super(listener);
        mFilePath = filePath;
        mOtaPresenter = presenter;
    }

    @Override
    public void start() {
        if (mOtaPresenter.getOtaManager() != null) {

            if (mOtaPresenter.getOtaManager().getDeviceInfo() != null) {
                mOtaPresenter.startOTA(mFilePath);
            } else {
                mOtaPresenter.getOtaManager().registerBluetoothCallback(new BtEventCallback() {
                    @Override
                    public void onConnection(BluetoothDevice device, int status) {
                        if (status == StateCode.CONNECTION_OK) {
                            mOtaPresenter.getOtaManager().unregisterBluetoothCallback(this);
                            mOtaPresenter.startOTA(mFilePath);
                        }
                    }
                });
            }
        }
    }

    private void taskFinish() {
        finishListener.onFinish();
        mOtaPresenter = null;
    }

    @Override
    public boolean isViewShow() {
        return true;
    }

    @Override
    public void onConnection(BluetoothDevice device, int status) {

    }

    @Override
    public void onMandatoryUpgrade() {

    }

    @Override
    public void onOTAStart() {

    }

    @Override
    public void onOTAReconnect(String btAddr) {

    }

    @Override
    public void onOTAProgress(int type, float progress) {

    }

    @Override
    public void onOTAStop() {
        taskFinish();
    }

    @Override
    public void onOTACancel() {

    }

    @Override
    public void onOTAError(int code, String message) {

    }

    @Override
    public void setPresenter(IOtaContract.IOtaPresenter presenter) {

    }


}
