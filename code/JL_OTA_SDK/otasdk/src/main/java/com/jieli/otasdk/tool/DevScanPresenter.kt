package com.jieli.otasdk.tool

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.ParcelUuid
import android.text.TextUtils
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_bt_ota.util.CommonUtil
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.tool.ota.OTAManager
import com.jieli.otasdk.tool.ota.ble.BleManager
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback
import com.jieli.otasdk.tool.ota.ble.model.BleScanInfo
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.tool.ota.spp.interfaces.SppEventCallback
import com.jieli.otasdk.util.AppUtil
import com.jieli.otasdk.util.JL_Constant
import java.util.*

/**
 * 扫描连接设备的逻辑实现
 *
 * @author zqjasonZhong
 * @date 2019/12/30
 */
class DevScanPresenter constructor(private var view: IDeviceContract.IDevScanView) :
    IDeviceContract.IDevScanPresenter {

    private val bleManager: BleManager = BleManager.getInstance()
    private val sppManager: SppManager = SppManager.getInstance()
    private var interval = 0

    private var communicationWay: Int =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
            .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)
    private val useSppPrivateChannel =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance())
            .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL)

    companion object {
        const val MAX_INTERVAL = 12
    }

    override fun start() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.registerBleEventCallback(mBleEventCallback)
        } else {
            sppManager.registerSppEventCallback(mSppEventCallback)
        }
    }

    override fun destroy() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.unregisterBleEventCallback(mBleEventCallback)
        } else {
            sppManager.unregisterSppEventCallback(mSppEventCallback)
        }
    }

    override fun isScanning(): Boolean {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.isBleScanning
        } else {
            return sppManager.isScanning
        }
    }

    override fun startScan() {
        if (BluetoothUtil.isBluetoothEnable()) {
            if (communicationWay == JL_Constant.PROTOCOL_BLE) {
                bleManager.startLeScan(12000)
            } else {
                sppManager.startDeviceScan(12000)
            }
        } else {
            AppUtil.enableBluetooth()
        }
    }

    override fun stopScan() {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.stopLeScan()
        } else {
            sppManager.stopDeviceScan()
        }
    }

    override fun getConnectedDevice(): BluetoothDevice? {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.connectedBtDevice
        } else {
            return sppManager.connectedSppDevice
        }
    }

    override fun connectBtDevice(device: BluetoothDevice) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            if (bleManager.connectedBtDevice != null) {
                bleManager.disconnectBleDevice(bleManager.connectedBtDevice)
            } else {
                bleManager.connectBleDevice(device)
            }
        } else {
            sppManager.connectSpp(device)
        }
    }

    override fun disconnectBtDevice(device: BluetoothDevice?) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.disconnectBleDevice(device)
        } else {
            sppManager.disconnectSpp(device, null)
        }
    }

    private fun stopEdrScan() {
        if (interval > 0) {
            interval = 0
            view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null)
            CommonUtil.getMainHandler()?.removeCallbacks(mScanSppDevice)
        }
    }

    fun deviceHasProfile(device: BluetoothDevice?, uuid: UUID?): Boolean {
        if (!BluetoothUtil.isBluetoothEnable()) {
            return false
        } else if (device == null) {
            return false
        } else if (TextUtils.isEmpty(uuid.toString())) {
            return false
        } else {
            var ret = false
            val uuids: Array<out ParcelUuid> = device.uuids ?: return false
            for (uid in uuids) {
                if (uuid.toString().toLowerCase(Locale.getDefault()) == uid.toString()) {
                    ret = true
                    break
                }
            }

            return ret
        }
    }

    private val mScanSppDevice = object : Runnable {
        override fun run() {
            if (interval == 0) {
                view.onScanStatus(JL_Constant.SCAN_STATUS_SCANNING, null)
            }
            val connectedDeviceList: List<BluetoothDevice>? =
                BluetoothUtil.getSystemConnectedBtDeviceList()
            if (connectedDeviceList != null && connectedDeviceList.isNotEmpty()) {
                val edrList = mutableListOf<BluetoothDevice>()
                for (device in connectedDeviceList) {
                    val devType = device.type
                    val isHasA2dp = deviceHasProfile(device, JL_Constant.UUID_A2DP)
                    val isHasSpp = deviceHasProfile(device, JL_Constant.UUID_SPP)
                    if (devType != BluetoothDevice.DEVICE_TYPE_LE
                        && isHasA2dp && isHasSpp
                    ) {
                        val isContains = edrList.contains(device)
                        if (!isContains) {
                            edrList.add(device)
                            view.onScanStatus(JL_Constant.SCAN_STATUS_FOUND_DEV, device)
                        }
                    }
                }
            }
            if (interval >= MAX_INTERVAL) {
                interval = 0
                view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null)
            } else {
                interval++
                CommonUtil.getMainHandler()?.postDelayed(this, 1000)
            }
        }
    }

    private fun handleAdapterStatus(bEnabled: Boolean) {
        if (bEnabled) {
            startScan()
        } else {
            stopEdrScan()
            view.onScanStatus(JL_Constant.SCAN_STATUS_IDLE, null)
            view.onConnectStatus(
                getConnectedDevice(),
                StateCode.CONNECTION_DISCONNECT
            )
        }
    }

    private fun handleDiscoveryStatus(bStart: Boolean) {
        view.onScanStatus(
            if (bStart) JL_Constant.SCAN_STATUS_SCANNING else JL_Constant.SCAN_STATUS_IDLE,
            null
        )
        if (bStart && getConnectedDevice() != null) {
            view.onScanStatus(
                JL_Constant.SCAN_STATUS_FOUND_DEV,
                getConnectedDevice()
            )
        }
    }

    private fun handleDiscoveryDevice(device: BluetoothDevice?) {
        if (device != null && BluetoothUtil.isBluetoothEnable()) {
            view.onScanStatus(JL_Constant.SCAN_STATUS_FOUND_DEV, device)
        }
    }

    private fun handleConnection(device: BluetoothDevice?, status: Int) {
        view.onConnectStatus(device, status)
    }

    private val mBleEventCallback = object : BleEventCallback() {
        override fun onAdapterChange(bEnabled: Boolean) {
            handleAdapterStatus(bEnabled)
        }

        override fun onDiscoveryBleChange(bStart: Boolean) {
            handleDiscoveryStatus(bStart)
        }

        override fun onDiscoveryBle(device: BluetoothDevice?, bleScanMessage: BleScanInfo?) {
            handleDiscoveryDevice(device)
        }

        override fun onBleConnection(device: BluetoothDevice?, status: Int) {
            handleConnection(device, OTAManager.changeConnectStatus(status))
        }
    }

    private val mSppEventCallback = object : SppEventCallback() {
        override fun onDiscoveryDeviceChange(bStart: Boolean) {
            handleDiscoveryStatus(bStart)
        }

        override fun onDiscoveryDevice(device: BluetoothDevice?, rssi: Int) {
            handleDiscoveryDevice(device)
        }

        override fun onSppConnection(device: BluetoothDevice?, uuid: UUID?, status: Int) {
            if (useSppPrivateChannel && status == BluetoothProfile.STATE_CONNECTED && SppManager.UUID_CUSTOM_SPP != uuid) {
//                sppManager.connectSpp(device, SppManager.UUID_RCSP_SPP)
                return
            }
            handleConnection(device, OTAManager.changeConnectStatus(status))
        }

    }
}