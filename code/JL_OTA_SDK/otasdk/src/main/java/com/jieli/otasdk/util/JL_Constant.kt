package com.jieli.otasdk.util

import java.util.*

/**
 * 常量声明
 *
 * @author zqjasonZhong
 * @date 2019/12/30
 */
class JL_Constant {

    companion object {
        const val SCAN_STATUS_IDLE = 0
        const val SCAN_STATUS_SCANNING = 1
        const val SCAN_STATUS_FOUND_DEV = 2

        val UUID_A2DP: UUID = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        //Ble协议
        const val PROTOCOL_BLE = 0
        //Spp协议
        const val PROTOCOL_SPP = 1

        const val CURRENT_PROTOCOL = PROTOCOL_BLE

        //是否使用设备认证
        const val IS_NEED_DEVICE_AUTH = true

        //是否HID设备连接
        const val HID_DEVICE_WAY = false

        //是否需要自定义连接方式
        const val NEED_CUSTOM_RECONNECT_WAY = false

        //是否使用SPP多通道连接
        const val USE_SPP_MULTIPLE_CHANNEL = false
        //是否使用自动化测试
        const val AUTO_TEST_OTA = false
        //Key
        const val KEY_IS_USE_DEVICE_AUTH = "is_use_device_auth"

        const val KEY_IS_HID_DEVICE = "is_hid_device"

        const val KEY_USE_CUSTOM_RECONNECT_WAY = "use_custom_reconnect_way"

        const val KEY_COMMUNICATION_WAY = "communication_way"

        const val KEY_SPP_MULTIPLE_CHANNEL = "spp_multiple_channel"

        const val KEY_AUTO_TEST_OTA = "auto_test_ota"

        //Action
        const val ACTION_EXIT_APP = "com.jieli.ota.exit_app"

        const val DIR_UPGRADE = "upgrade"
        const val DIR_LOGCAT = "logcat"
    }
}