package com.jieli.otasdk_java.tool;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.jieli.component.Logcat;
import com.jieli.component.utils.PreferencesHelper;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.interfaces.BtEventCallback;
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback;
import com.jieli.jl_bt_ota.model.base.BaseError;
import com.jieli.jl_bt_ota.tool.DeviceStatusManager;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_bt_ota.util.CHexConver;
import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.otasdk_java.tool.ota.OTAManager;
import com.jieli.otasdk_java.util.JL_Constant;
import com.jieli.otasdk_java.MainApplication;
import com.jieli.otasdk_java.tool.ota.ble.BleManager;
import com.jieli.otasdk_java.tool.ota.ble.interfaces.BleEventCallback;
import com.jieli.otasdk_java.tool.ota.spp.SppManager;
import com.jieli.otasdk_java.tool.ota.spp.interfaces.SppEventCallback;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @ClassName: OtaPresenter
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 9:17
 */
public class OtaPresenter implements IOtaContract.IOtaPresenter {
    private IOtaContract.IOtaView view;
    private BleManager bleManager = BleManager.getInstance();
    private SppManager sppManager = SppManager.getInstance();
    private OTAManager otaManager = null;
    private int communicationWay = PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
            .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);
    private String tag = "zzc_ota";
    private BtEventCallback mOTABtEventCallback = new BtEventCallback() {
        @Override
        public void onConnection(BluetoothDevice device, int status) {
            view.onConnection(device, status);
            /*//可以在这里调用queryMandatoryUpdate
            if (status == StateCode.CONNECTION_OK) { //准备完成
                //获取设备信息的两种方式
                //第一种方式是直接调用缓存设备信息，判断是否需要强制升级，判断mandatoryUpgradeFlag是否等于1即可
                val info = DeviceStatusManager.getInstance().getDeviceInfo(device)
                JL_Log.i("OtaPresenter", "info : $info")
                //第二种方式是直接请求设备信息
                otaManager?.queryMandatoryUpdate(object : IActionCallback<TargetInfoResponse> {
                    //需要强升
                    override fun onSuccess(p0: TargetInfoResponse?) {
                        view.onMandatoryUpgrade()
                    }

                    override fun onError(p0: com.jieli.jl_bt_ota.model.base.BaseError?) {
                        if (p0?.subCode == ErrorCode.ERR_NONE) { //获取版本信息成功
                            //查询版本号
                            val deviceInfo = DeviceStatusManager.getInstance()
                                .getDeviceInfo(otaManager?.connectedDevice)
                            JL_Log.i(
                                "OtaPresenter", String.format(
                                    Locale.getDefault(),
                                    "device version code : %d, version name : %s",
                                    deviceInfo.versionCode,
                                    deviceInfo.versionName
                                )
                            )
                        }
                    }
                })
            }*/
        }

        @Override
        public void onMandatoryUpgrade(BluetoothDevice device) {
            JL_Log.w(
                    tag, "=======onMandatoryUpgrade==========" + view.isViewShow()
                            + ", " + BluetoothUtil.printBtDeviceInfo(device)
            );
            if (view.isViewShow()) {
                view.onConnection(device, StateCode.CONNECTION_OK);
                String path = otaManager.getBluetoothOption().getFirmwareFilePath();
                if (path != null) {
                    startOTA(path);
                }
            }
        }
    };

    public OtaPresenter(IOtaContract.IOtaView view, Context context) {
        this.view = view;
        otaManager = new OTAManager(context);
        otaManager.registerBluetoothCallback(mOTABtEventCallback);
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
        JL_Log.w(tag, "================destroy=================");
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.unregisterBleEventCallback(mBleEventCallback);
        } else {
            sppManager.unregisterSppEventCallback(mSppEventCallback);
        }
        otaManager.release();
    }

    @Nullable
    @Override
    public BluetoothDevice getConnectedDevice() {
        BluetoothDevice device = null;
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            device = bleManager.getConnectedBtDevice();
        } else {
            device = sppManager.getConnectedSppDevice();
        }
        return device;
    }

    @Override
    public boolean isDevConnected() {
        BluetoothDevice device = getConnectedDevice();
        if (device == null) return false;
        return DeviceStatusManager.getInstance().getDeviceInfo(device) != null;
    }

    @Override
    public OTAManager getOtaManager() {
        return otaManager;
    }

    @Override
    public boolean isOTA() {
        boolean ret = false;
        if (otaManager != null) {
            ret = otaManager.isOTA();
        }
        return ret;
    }

    @Override
    public void startOTA(@NotNull String filePath) {
        JL_Log.i(tag, "startOTA :: $String");
        if (otaManager != null) {
            otaManager.getBluetoothOption().setFirmwareFilePath(filePath);
            otaManager.startOTA(mOTAManagerCallback);
        }
    }

    @Override
    public void cancelOTA() {
        if (otaManager != null) {
            otaManager.cancelOTA();
        }
    }

    @Override
    public void reconnectDev(@Nullable String devAddr) {
        //Step0.转换成目标地址， 比如地址+1
        JL_Log.i("zzc_ota", "change addr before : $devAddr");
        byte[] data = BluetoothUtil.addressCovertToByteArray(devAddr);
        int value = CHexConver.byteToInt(data[data.length - 1]) + 1;
        data[data.length - 1] = CHexConver.intToByte(value);
        String newAddr = BluetoothUtil.hexDataCovetToAddress(data);
        JL_Log.i("zzc_ota", "change addr after: $newAddr");
        //Step1.更新回连的地址
        if (otaManager != null) {
            otaManager.setReconnectAddr(newAddr);
        }
        //Step2.主动实现回连方式
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.setReconnectDevAddr(devAddr);
        } else {
            //TODO:需要增加SPP自定义回连方式
        }
    }

    private IUpgradeCallback mOTAManagerCallback = new IUpgradeCallback() {
        @Override
        public void onStartOTA() {
            view.onOTAStart();
        }

        @Override
        public void onNeedReconnect(String addr, boolean isUseAdv) {
            Logcat.e(tag, "onNeedReconnect : " + addr);
            view.onOTAReconnect(addr);
        }

        @Override
        public void onProgress(int type, float progress) {
            view.onOTAProgress(type, progress);
        }

        @Override
        public void onStopOTA() {
            view.onOTAStop();
        }

        @Override
        public void onCancelOTA() {
            view.onOTACancel();
        }

        @Override
        public void onError(BaseError error) {
            if (error != null) {
                view.onOTAError(error.getSubCode(), error.getMessage());
            }
        }
    };

    private BleEventCallback mBleEventCallback = new BleEventCallback() {
        @Override
        public void onBleConnection(BluetoothDevice device, int status) {
            JL_Log.w(
                    tag,
                    "onBleConnection : " + BluetoothUtil.printBtDeviceInfo(device) + ", status:" + status);
        }
    };
    private SppEventCallback mSppEventCallback = new SppEventCallback() {
        @Override
        public void onSppConnection(BluetoothDevice device, UUID uuid, int status) {
            JL_Log.w(
                    tag,
                    "onSppConnection : " + BluetoothUtil.printBtDeviceInfo(device) + ", status:" + status
            );
            if (uuid == SppManager.UUID_SPP) {
                view.onConnection(device, OTAManager.changeConnectStatus(status));
            }
        }
    };
}
