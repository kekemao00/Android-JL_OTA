package com.jieli.otasdk.tool

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.jieli.component.Logcat
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.interfaces.BtEventCallback
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback
import com.jieli.jl_bt_ota.tool.DeviceStatusManager
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_bt_ota.util.CHexConver
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.tool.ota.OTAManager
import com.jieli.otasdk.tool.ota.ble.BleManager
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.tool.ota.spp.interfaces.SppEventCallback
import com.jieli.otasdk.util.JL_Constant
import java.util.*

/**
 * OTA逻辑实现
 *
 * @author zqjasonZhong
 * @date 2019/12/30
 */

class OtaPresenter constructor(
    private var view: IOtaContract.IOtaView, context:
    Context
) : IOtaContract.IOtaPresenter {

    private val bleManager: BleManager = BleManager.getInstance()
    private val sppManager: SppManager = SppManager.getInstance()
    private var otaManager: OTAManager? = null
    private var communicationWay: Int =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
            .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)
    private val tag = "zzc_ota"
    private var isSkipTips = false

    private val mOTABtEventCallback = object : BtEventCallback() {

        override fun onConnection(device: BluetoothDevice?, status: Int) {
            view.onConnection(device, status)
            //可以在这里调用queryMandatoryUpdate
            if (status == StateCode.CONNECTION_OK) { //准备完成
                //获取设备信息的两种方式
                //第一种方式是直接调用缓存设备信息，判断是否需要强制升级，判断mandatoryUpgradeFlag是否等于1即可
                val info = DeviceStatusManager.getInstance().getDeviceInfo(device)
                JL_Log.i("OtaPresenter", "info : $info")
                if(!isSkipTips && info.mandatoryUpgradeFlag == com.jieli.jl_bt_ota.constant.JL_Constant.FLAG_MANDATORY_UPGRADE){
                    JL_Log.e("OtaPresenter", "设备处于强制升级状态，请先升级固件")
                    view.onMandatoryUpgrade()
                }
                /*//第二种方式是直接请求设备信息
                otaManager?.queryMandatoryUpdate(object : IActionCallback<TargetInfoResponse> {
                    //需要强升
                    override fun onSuccess(p0: TargetInfoResponse?) {
                        view.onMandatoryUpgrade()
                    }

                    override fun onError(p0: com.jieli.jl_bt_ota.model.base.BaseError?) {
                        if (p0?.code == ErrorCode.ERR_NONE && p0.subCode == ErrorCode.ERR_NONE) { //获取版本信息成功
                            //查询版本号
                            val deviceInfo = otaManager?.deviceInfo;
                            JL_Log.i("OtaPresenter", String.format(Locale.getDefault(), "device version code : %d, version name : %s",
                                deviceInfo?.versionCode, deviceInfo?.versionName))
                            if(!isSkipTips && info.mandatoryUpgradeFlag == com.jieli.jl_bt_ota.constant.JL_Constant.FLAG_MANDATORY_UPGRADE){
                                view.onMandatoryUpgrade()
                            }
                        }
                    }
                })*/
            }
        }

        override fun onMandatoryUpgrade(device: BluetoothDevice?) {
            JL_Log.w(
                tag, "=======onMandatoryUpgrade==========" + view.isViewShow()
                        + ", " + BluetoothUtil.printBtDeviceInfo(device)
            )
            if (view.isViewShow()) {
                view.onConnection(device, StateCode.CONNECTION_OK)
                otaManager?.bluetoothOption?.firmwareFilePath?.let { startOTA(it) }
            }
        }
    }

    init {
        otaManager = OTAManager(context)
        otaManager?.registerBluetoothCallback(mOTABtEventCallback)
    }

    override fun start() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.registerBleEventCallback(mBleEventCallback)
        } else {
            sppManager.registerSppEventCallback(mSppEventCallback)
        }
    }

    override fun destroy() {
        JL_Log.w(tag, "================destroy=================")
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.unregisterBleEventCallback(mBleEventCallback)
        } else {
            sppManager.unregisterSppEventCallback(mSppEventCallback)
        }
        otaManager?.release()
    }

    override fun getConnectedDevice(): BluetoothDevice? {
        return if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.connectedBtDevice
        } else {
            sppManager.connectedSppDevice
        }
    }

    override fun isDevConnected(): Boolean {
        val device = getConnectedDevice() ?: return false
        return DeviceStatusManager.getInstance().getDeviceInfo(device) != null
    }

    override fun getOtaManager(): OTAManager? {
        return otaManager
    }

    override fun isOTA(): Boolean {
        var ret = false
        if (otaManager != null) {
            ret = otaManager!!.isOTA
        }
        return ret
    }

    override fun startOTA(filePath: String) {
        JL_Log.i(tag, "startOTA :: $String")
        otaManager?.let {
            //            it.upgradeFilePath = filePath
            it.bluetoothOption.firmwareFilePath = filePath
            it.startOTA(mOTAManagerCallback)
        }
    }

    override fun cancelOTA() {
        otaManager?.cancelOTA()
    }

    override fun reconnectDev(devAddr: String?) {
        //Step0.转换成目标地址， 比如地址+1
        JL_Log.i("zzc_ota", "change addr before : $devAddr")
        val data = BluetoothUtil.addressCovertToByteArray(devAddr)
        val value = CHexConver.byteToInt(data[data.size - 1]) + 1;
        data[data.size - 1] = CHexConver.intToByte(value)
        val newAddr = BluetoothUtil.hexDataCovetToAddress(data)
        JL_Log.i("zzc_ota", "change addr after: $newAddr")
        //Step1.更新回连的地址
        otaManager?.setReconnectAddr(newAddr)
        //Step2.主动实现回连方式
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.setReconnectDevAddr(devAddr)
        } else {
            //TODO:需要增加SPP自定义回连方式
        }
    }

    private val mOTAManagerCallback = object : IUpgradeCallback {
        override fun onError(p0: com.jieli.jl_bt_ota.model.base.BaseError?) {
            isSkipTips = false
            if (p0 != null) {
                view.onOTAError(p0.subCode, p0.message)
            }
        }

        override fun onNeedReconnect(p0: String?, p1: Boolean) {
            Logcat.e(tag, "onNeedReconnect : $p0")
            isSkipTips = true
            view.onOTAReconnect(p0)
        }

        override fun onStopOTA() {
            isSkipTips = false
            view.onOTAStop()
        }

        override fun onProgress(type: Int, progress: Float) {
            view.onOTAProgress(type, progress)
        }

        override fun onStartOTA() {
            isSkipTips = false
            view.onOTAStart()
        }

        override fun onCancelOTA() {
            isSkipTips = false
            view.onOTACancel()
        }
    }

    private val mBleEventCallback = object : BleEventCallback() {
        override fun onBleConnection(device: BluetoothDevice?, status: Int) {
            JL_Log.w(
                tag,
                "onBleConnection : ${BluetoothUtil.printBtDeviceInfo(device)}, status:$status"
            )
//            view.onConnection(device, OTAManager.changeConnectStatus(status))
        }
    }

    private val mSppEventCallback = object : SppEventCallback() {
        override fun onSppConnection(device: BluetoothDevice?, uuid: UUID?, status: Int) {
            JL_Log.w(
                tag,
                "onSppConnection : ${BluetoothUtil.printBtDeviceInfo(device)}, status:$status"
            )
            if (uuid == SppManager.UUID_SPP) {
                view.onConnection(device, OTAManager.changeConnectStatus(status))
            }
        }
    }
}