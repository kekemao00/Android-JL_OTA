package com.jieli.otasdk.tool

import android.bluetooth.BluetoothDevice
import com.jieli.otasdk.base.BasePresenter
import com.jieli.otasdk.base.BaseView
import com.jieli.otasdk.tool.ota.OTAManager

/**
 * OTA接口管理
 *
 * @author zqjasonZhong
 * @date 2019/12/30
 */
interface IOtaContract {

    interface IOtaPresenter : BasePresenter {

        fun getConnectedDevice(): BluetoothDevice?

        fun isDevConnected(): Boolean

        fun getOtaManager(): OTAManager?

        fun isOTA(): Boolean

        fun startOTA(filePath: String)

        fun cancelOTA()

        fun reconnectDev(devAddr: String?)
    }

    interface IOtaView : BaseView<IOtaPresenter> {
        fun isViewShow(): Boolean

        fun onConnection(device: BluetoothDevice?, status: Int)

        fun onMandatoryUpgrade()

        fun onOTAStart()

        fun onOTAReconnect(btAddr: String?)

        fun onOTAProgress(type: Int, progress: Float)

        fun onOTAStop()

        fun onOTACancel()

        fun onOTAError(code: Int, message: String)
    }
}