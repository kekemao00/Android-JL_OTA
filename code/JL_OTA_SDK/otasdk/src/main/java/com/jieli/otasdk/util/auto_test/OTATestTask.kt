package com.jieli.otasdk.util.auto_test

import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.jieli.component.thread.ThreadManager
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.interfaces.BtEventCallback
import com.jieli.otasdk.base.BasePresenter
import com.jieli.otasdk.tool.IOtaContract
import com.jieli.otasdk.util.TestTask
import com.jieli.otasdk.util.TestTaskFinishListener

/**
 *
 * @ClassName:      OTATestTask
 * @Description:     java类作用描述
 * @Author:         ZhangHuanMing
 * @CreateDate:     2022/2/18 10:55
 */
class OTATestTask(
    private val filePath: String,
    private var otaPresenter: IOtaContract.IOtaPresenter?,
    finishListener: TestTaskFinishListener
) : TestTask(finishListener), IOtaContract.IOtaView {
    override fun start() {
        otaPresenter?.getOtaManager()?.let {
            if (it.deviceInfo != null) {
                otaPresenter?.startOTA(filePath)
            } else {
                it.registerBluetoothCallback(object : BtEventCallback() {
                    override fun onConnection(device: BluetoothDevice?, status: Int) {
                        if (status == StateCode.CONNECTION_OK) {
                            it.unregisterBluetoothCallback(this)
                            otaPresenter?.startOTA(filePath)
                        }
                    }
                })
            }
        }
    }

    private fun taskFinish(): Unit {
        finishListener.onFinish()
        otaPresenter = null
    }

    override fun isViewShow(): Boolean {
        return true
    }

    override fun onConnection(device: BluetoothDevice?, status: Int) {
    }

    override fun onMandatoryUpgrade() {
    }

    override fun onOTAStart() {
    }

    override fun onOTAReconnect(btAddr: String?) {
    }

    override fun onOTAProgress(type: Int, progress: Float) {
    }

    override fun onOTAStop() {//升级成功
        taskFinish()
    }

    override fun onOTACancel() {
        //todo  停止任务
    }

    override fun onOTAError(code: Int, message: String) {
        //todo  停止任务
    }

    override fun setPresenter(presenter: IOtaContract.IOtaPresenter) {
    }

}