package com.jieli.otasdk_java.tool;

import android.bluetooth.BluetoothDevice;

import com.jieli.otasdk_java.base.BasePresenter;
import com.jieli.otasdk_java.base.BaseView;
import com.jieli.otasdk_java.tool.ota.OTAManager;

/**
 * @ClassName: IOtaContract
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 8:51
 */
public interface IOtaContract {
    public interface IOtaPresenter extends BasePresenter {
        public BluetoothDevice getConnectedDevice();

        public boolean isDevConnected();

        public OTAManager getOtaManager();

        public boolean isOTA();

        public void startOTA(String filePath);

        public void cancelOTA();

        public void reconnectDev(String devAddr);
    }

    public interface IOtaView extends BaseView<IOtaPresenter> {
        public boolean isViewShow();

        public void onConnection(BluetoothDevice device, int status);

        public void onMandatoryUpgrade();

        public void onOTAStart();

        public void onOTAReconnect(String btAddr);

        public void onOTAProgress(int type, float progress);

        public void onOTAStop();

        public void onOTACancel();

        public void onOTAError(int code, String message);
    }
}
