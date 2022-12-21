package com.jieli.otasdk.tool

import android.bluetooth.BluetoothDevice
import com.jieli.otasdk.base.BasePresenter
import com.jieli.otasdk.base.BaseView

/**
 * 设备操作接口
 *
 * @author zqjasonZhong
 * @date 2019/12/30
 */
interface IDeviceContract {

    interface IDevScanPresenter : BasePresenter {

        fun isScanning(): Boolean

        fun startScan()

        fun stopScan()

        fun getConnectedDevice(): BluetoothDevice?

        fun connectBtDevice(device: BluetoothDevice)

        fun disconnectBtDevice(device: BluetoothDevice?)
    }

    interface IDevScanView : BaseView<IDevScanPresenter> {
        fun onScanStatus(status: Int, device: BluetoothDevice?)

        fun onConnectStatus(device: BluetoothDevice?, status: Int)

        fun onMandatoryUpgrade()

        fun onErrorCallback(code: Int, message: String)

    }
}