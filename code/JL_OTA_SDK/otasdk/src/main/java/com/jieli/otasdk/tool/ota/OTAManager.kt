package com.jieli.otasdk.tool.ota

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.jieli.jl_bt_ota.constant.BluetoothConstant
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure
import com.jieli.jl_bt_ota.model.base.BaseError
import com.jieli.jl_bt_ota.tool.DeviceReConnectManager
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_bt_ota.util.CHexConver
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.tool.ota.ble.BleManager
import com.jieli.otasdk.tool.ota.ble.interfaces.BleEventCallback
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.tool.ota.spp.interfaces.SppEventCallback
import com.jieli.otasdk.util.JL_Constant
import java.util.*

/**
 * 用于RCSP的第三方SDK接入OTA流程
 *
 * create Data:2019-08-21
 * create by:chensenhua
 */
class OTAManager(context: Context) : BluetoothOTAManager(context) {

    private val bleManager: BleManager = BleManager.getInstance()
    private val sppManager: SppManager = SppManager.getInstance()

    private var communicationWay : Int =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)

    companion object {

        fun changeConnectStatus(status: Int): Int {
            var changeStatus = StateCode.CONNECTION_DISCONNECT
            when (status) {
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING -> changeStatus =
                    StateCode.CONNECTION_DISCONNECT
                BluetoothProfile.STATE_CONNECTED -> changeStatus = StateCode.CONNECTION_OK
                BluetoothProfile.STATE_CONNECTING -> changeStatus = StateCode.CONNECTION_CONNECTING
            }
            return changeStatus
        }
    }

    private val bleEventCallback = object : BleEventCallback() {
        override fun onBleConnection(device: BluetoothDevice?, status: Int) {
            super.onBleConnection(device, status)
            JL_Log.i(
                TAG,
                "onBleConnection >>> device : ${BluetoothUtil.printBtDeviceInfo(device)}, status ：$status, change status : ${changeConnectStatus(
                    status
                )}"
            )
            onBtDeviceConnection(device,
                changeConnectStatus(
                    status
                )
            )
        }

        override fun onBleDataNotification(
            device: BluetoothDevice?,
            serviceUuid: UUID?,
            characteristicsUuid: UUID?,
            data: ByteArray?
        ) {
            super.onBleDataNotification(device, serviceUuid, characteristicsUuid, data)
            JL_Log.i(
                TAG,
                "onBleDataNotification >>> ${BluetoothUtil.printBtDeviceInfo(device)}, data ：${CHexConver.byte2HexStr(
                    data
                )} "
            )
            onReceiveDeviceData(device, data)
        }

        override fun onBleDataBlockChanged(device: BluetoothDevice?, block: Int, status: Int) {
            super.onBleDataBlockChanged(device, block, status)
            onMtuChanged(bleManager.connectedBtGatt, block, status)
        }
    }

    private val sppEventCallback = object : SppEventCallback() {
        override fun onSppConnection(device: BluetoothDevice?, uuid: UUID?, status: Int) {
            if (SppManager.UUID_SPP == uuid) {
                val newStatus =
                    changeConnectStatus(
                        status
                    )
                JL_Log.i(
                    TAG,
                    "-onSppConnection- device = ${BluetoothUtil.printBtDeviceInfo(device)}, uuid = $uuid, status = $newStatus"
                )
                onBtDeviceConnection(device, newStatus)
            }
        }

        override fun onReceiveSppData(device: BluetoothDevice?, uuid: UUID?, data: ByteArray?) {
            if (SppManager.UUID_SPP == uuid) {
                onReceiveDeviceData(device, data)
            }
        }
    }

    init {
        val bluetoothOption = BluetoothOTAConfigure()
        //选择通讯方式
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bluetoothOption.priority = BluetoothOTAConfigure.PREFER_BLE
        } else {
            bluetoothOption.priority = BluetoothOTAConfigure.PREFER_SPP
            //是否需要自定义回连方式(默认不需要，如需要自定义回连方式，需要客户自行实现)
            bluetoothOption.isUseReconnect = (PreferencesHelper.getSharedPreferences(context)
                .getBoolean(
                    JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                    JL_Constant.NEED_CUSTOM_RECONNECT_WAY
                ) && PreferencesHelper.getSharedPreferences(context)
                .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY))
        }
        //是否启用设备认证流程(与固件工程师确认)
        bluetoothOption.isUseAuthDevice = PreferencesHelper.getSharedPreferences(context)
            .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH)
        //设置BLE的MTU
        bluetoothOption.mtu = BluetoothConstant.BLE_MTU_MIN
        //是否需要改变BLE的MTU
        bluetoothOption.isNeedChangeMtu = false
        //是否启用杰理服务器(暂时不支持)
        bluetoothOption.isUseJLServer = false
        //配置OTA参数
        configure(bluetoothOption)
        bleManager.registerBleEventCallback(bleEventCallback)
        sppManager.registerSppEventCallback(sppEventCallback)
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            if (bleManager.connectedBtDevice != null) {
                onBtDeviceConnection(bleManager.connectedBtDevice, StateCode.CONNECTION_OK)
                onMtuChanged(
                    bleManager.connectedBtGatt, bleManager.bleMtu + 3,
                    BluetoothGatt.GATT_SUCCESS
                )
            }
        } else {
            if (sppManager.connectedSppDevice != null) {
                onBtDeviceConnection(sppManager.connectedSppDevice, StateCode.CONNECTION_OK)
            }
        }
    }


    override fun getConnectedDevice(): BluetoothDevice? {
        if (bleManager.connectedBtDevice!=null){
            return bleManager.connectedBtDevice
        }else{
            return sppManager.connectedSppDevice
        }
//        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
//            return bleManager.connectedBtDevice
//        } else {
//            return sppManager.connectedSppDevice
//        }
    }

    override fun getConnectedBluetoothGatt(): BluetoothGatt? {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return bleManager.connectedBtGatt
        } else {
            return null
        }
    }

    override fun connectBluetoothDevice(bluetoothDevice: BluetoothDevice?) {
//        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.connectBleDevice(bluetoothDevice)
//        } else {
//            sppManager.connectSpp(bluetoothDevice, SppManager.UUID_SPP)
//        }
    }

    override fun disconnectBluetoothDevice(bluetoothDevice: BluetoothDevice?) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            bleManager.disconnectBleDevice(bluetoothDevice)
        } else {
            sppManager.disconnectSpp(bluetoothDevice, null)
        }
    }

    override fun sendDataToDevice(bluetoothDevice: BluetoothDevice?, bytes: ByteArray?): Boolean {
//        val ret = bluetoothClient.jlBtManager.sendDataToDevice(bluetoothDevice, bytes)
//        JL_Log.d("zzc_bluetooth", "sendDataToDevice : ret = $ret")
        if (bluetoothDevice == null || bytes == null) return false
        if (bleManager.connectedBtDevice!=null/*communicationWay == JL_Constant.PROTOCOL_BLE*/) {
            bleManager.writeDataByBleAsync(
                bluetoothDevice,
                BleManager.BLE_UUID_SERVICE,
                BleManager.BLE_UUID_WRITE,
                bytes
            ) { device, serviceUUID, characteristicUUID, result, data ->
                JL_Log.i(
                    TAG,
                    "-writeDataByBleAsync- result = $result, device:${BluetoothUtil.printBtDeviceInfo(
                        device
                    )}, data:[${CHexConver.byte2HexStr(
                        data
                    )}]"
                )
            }
        } else {
            sppManager.writeDataToSppAsync(
                bluetoothDevice,
                SppManager.UUID_SPP,
                bytes
            ) { device, sppUUID, result, data ->
                JL_Log.i(
                    TAG,
                    "-writeDataToSppAsync- device = ${BluetoothUtil.printBtDeviceInfo(device)}, uuid = $sppUUID, result = $result, data = ${CHexConver.byte2HexStr(
                        data
                    )}"
                )
            }
        }
        return true
    }

    override fun errorEventCallback(baseError: BaseError) {

    }

    override fun release() {
        super.release()
        bleManager.unregisterBleEventCallback(bleEventCallback)
        sppManager.unregisterSppEventCallback(sppEventCallback)
//        bleManager.destroy()
    }

    fun setReconnectAddr(addr: String?) {
        if (BluetoothAdapter.checkBluetoothAddress(addr)) {
            DeviceReConnectManager.getInstance(this).reconnectAddress = addr
        }
    }

}
