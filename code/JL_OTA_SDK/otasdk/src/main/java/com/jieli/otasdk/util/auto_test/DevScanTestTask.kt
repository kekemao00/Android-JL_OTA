package com.jieli.otasdk.util.auto_test

import android.bluetooth.BluetoothDevice
import com.jieli.component.thread.ThreadManager
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.otasdk.tool.IDeviceContract
import com.jieli.otasdk.util.JL_Constant
import com.jieli.otasdk.util.TestTask
import com.jieli.otasdk.util.TestTaskFinishListener

/**
 *
 * @ClassName:      DevScanTask
 * @Description:     java类作用描述
 * @Author:         ZhangHuanMing
 * @CreateDate:     2022/2/18 11:00
 */
class DevScanTestTask(
    private val device: BluetoothDevice,
    private var devScanPresenter: IDeviceContract.IDevScanPresenter?,
    finishListener: TestTaskFinishListener
) : TestTask(finishListener), IDeviceContract.IDevScanView {
    private var mDeviceIsDisconnect = true

    override fun start() {
        if (BluetoothUtil.deviceEquals(device, devScanPresenter?.getConnectedDevice())) {//设备未断开
            taskFinish()
        } else {
            ThreadManager.getInstance().postRunnable {
                devScanPresenter?.startScan()
            }
        }
    }

    private fun taskFinish(): Unit {
        finishListener.onFinish()
        ThreadManager.getInstance().postRunnable {
            devScanPresenter?.stopScan()
        }
        devScanPresenter = null
    }

    override fun onScanStatus(status: Int, device: BluetoothDevice?) { //发现设备
        if (!mDeviceIsDisconnect) return
        if (status == JL_Constant.SCAN_STATUS_FOUND_DEV) {
            if (BluetoothUtil.deviceEquals(device, this.device)) {
                mDeviceIsDisconnect = false
                devScanPresenter?.connectBtDevice(device!!)
            }
        } else if (status == JL_Constant.SCAN_STATUS_IDLE) {//扫描停止了
            devScanPresenter?.startScan()
        }
    }

    override fun onConnectStatus(device: BluetoothDevice?, status: Int) {//连接状态
        if (BluetoothUtil.deviceEquals(device, this.device)) {
            when (status) {
                StateCode.CONNECTION_CONNECTING,
                StateCode.CONNECTION_CONNECTED -> {
                    mDeviceIsDisconnect = false
                }
                StateCode.CONNECTION_OK -> {
                    mDeviceIsDisconnect = false
                    taskFinish()
                }
                StateCode.CONNECTION_FAILED,
                StateCode.CONNECTION_DISCONNECT -> {
                    mDeviceIsDisconnect = true
                }
            }
        }
    }

    override fun onMandatoryUpgrade() {
    }

    override fun onErrorCallback(code: Int, message: String) {
    }

    override fun setPresenter(presenter: IDeviceContract.IDevScanPresenter) {

    }

}