package com.jieli.otasdk_java.util;

import java.util.UUID;

/**
 * @ClassName: JL_Constant
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:52
 */
public class JL_Constant {
    public static final int SCAN_STATUS_IDLE = 0;
    public static final int SCAN_STATUS_SCANNING = 1;
    public static final int SCAN_STATUS_FOUND_DEV = 2;

    public static UUID UUID_A2DP = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb");
    public static UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    //Ble协议
    public static final int PROTOCOL_BLE = 0;
    //Spp协议
    public static final int PROTOCOL_SPP = 1;

    public static final int CURRENT_PROTOCOL = PROTOCOL_BLE;

    //是否使用设备认证
    public static final boolean IS_NEED_DEVICE_AUTH = true;

    //是否HID设备连接
    public static final boolean HID_DEVICE_WAY = false;

    //是否需要自定义连接方式
    public static final boolean NEED_CUSTOM_RECONNECT_WAY = false;

    //是否使用SPP多通道连接
    public static final boolean USE_SPP_MULTIPLE_CHANNEL = false;

    public static final boolean AUTO_TEST_OTA = false;
    //Key
    public static final String KEY_IS_USE_DEVICE_AUTH = "is_use_device_auth";

    public static final String KEY_IS_HID_DEVICE = "is_hid_device";

    public static final String KEY_USE_CUSTOM_RECONNECT_WAY = "use_custom_reconnect_way";

    public static final String KEY_COMMUNICATION_WAY = "communication_way";

    public static final String KEY_SPP_MULTIPLE_CHANNEL = "spp_multiple_channel";

    public static final String KEY_AUTO_TEST_OTA = "auto_test_ota";
    //Action
    public static final String ACTION_EXIT_APP = "com.jieli.ota.exit_app";

    public static final String DIR_UPGRADE = "upgrade";
    public static final String DIR_LOGCAT = "logcat";

}
