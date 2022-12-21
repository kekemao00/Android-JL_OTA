package com.jieli.otasdk.activities

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import com.jieli.component.utils.SystemUtil
import com.jieli.component.utils.ToastUtil
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_bt_ota.util.CHexConver
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.BuildConfig
import com.jieli.otasdk.R
import com.jieli.otasdk.base.BaseActivity
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.tool.ota.spp.interfaces.SppEventCallback
import com.jieli.otasdk.util.JL_Constant
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : BaseActivity() {

    companion object {

        val CUSTOM_UUID = SppManager.UUID_CUSTOM_SPP

        fun newInstance(): SettingsActivity {
            return SettingsActivity()
        }
    }

    private var isChangeConfiguration = false
    private var isUseDeviceAuth = false
    private var isHidDevice = false
    private var useCustomReconnectWay = false
    private var communicationWay = JL_Constant.CURRENT_PROTOCOL
    private var useSppPrivateChannel = false
    private var autoTestOTA = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        isUseDeviceAuth = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH)
        isHidDevice = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)
        useCustomReconnectWay = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(
                JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                JL_Constant.NEED_CUSTOM_RECONNECT_WAY
            )
        communicationWay = PreferencesHelper.getSharedPreferences(applicationContext)
            .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)
        useSppPrivateChannel = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL)
        autoTestOTA = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA)
        initView()

    }

    override fun onDestroy() {
        super.onDestroy()
        isChangeConfiguration = false
        SppManager.getInstance().unregisterSppEventCallback(sppEventCallback)
    }

    private fun initView() {

        tv_settings_version_value.text =
            String.format(" : %s", SystemUtil.getVersioName(applicationContext))
        if (!BuildConfig.DEBUG) {
            tv_settings_top_right.visibility = View.GONE
            cl_settings_container.visibility = View.GONE
            return
        }
        tv_settings_top_right.visibility = View.VISIBLE
        cl_settings_container.visibility = View.VISIBLE
        cb_settings_device_auth.isChecked = isUseDeviceAuth
        cb_settings_hid_device.isChecked = isHidDevice
        cb_settings_custom_reconnect_way.isChecked = useCustomReconnectWay
        tv_settings_top_left.setOnClickListener { view ->
            finish()
        }
        tv_settings_top_right.setOnClickListener {
            PreferencesHelper.putBooleanValue(
                applicationContext,
                JL_Constant.KEY_IS_USE_DEVICE_AUTH,
                cb_settings_device_auth.isChecked
            )
            PreferencesHelper.putBooleanValue(
                applicationContext,
                JL_Constant.KEY_IS_HID_DEVICE,
                cb_settings_hid_device.isChecked
            )
            PreferencesHelper.putBooleanValue(
                applicationContext,
                JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                cb_settings_custom_reconnect_way.isChecked
            )
            val way = when {
                rb_settings_way_ble.isChecked -> {
                    JL_Constant.PROTOCOL_BLE
                }
                rb_settings_way_spp.isChecked -> {
                    JL_Constant.PROTOCOL_SPP
                }
                else -> {
                    communicationWay
                }
            }
            PreferencesHelper.putIntValue(
                applicationContext,
                JL_Constant.KEY_COMMUNICATION_WAY,
                way
            )
            PreferencesHelper.putBooleanValue(
                applicationContext,
                JL_Constant.KEY_SPP_MULTIPLE_CHANNEL,
                cb_settings_use_spp_private_channel.isChecked
            )
            PreferencesHelper.putBooleanValue(
                applicationContext,
                JL_Constant.KEY_AUTO_TEST_OTA,
                cb_settings_custom_auto_test_ota.isChecked
            )
            checkIsChangeConfiguration()
            if (isChangeConfiguration) {
                isChangeConfiguration = false
                ToastUtil.showToastShort(R.string.settings_success_and_restart)
//                SystemUtil.restartApp(applicationContext)
                Handler().postDelayed({
                    finish()
                    sendBroadcast(Intent(JL_Constant.ACTION_EXIT_APP))
                }, 1000L)
            }
        }
        btn_send_file_snapdrop.setOnClickListener {
            val intent = Intent(this, SnapDropActivity::class.java);
            startActivity(intent)
        }
        btn_send_spp_data.setOnClickListener {
            val text: String? = et_settings_input_data.text?.trim()?.toString()
            if (text != null) {
                if (SppManager.getInstance().connectedSppDevice != null) {
                    SppManager.getInstance().writeDataToSppAsync(
                        SppManager.getInstance().connectedSppDevice,
                        CUSTOM_UUID,
                        text.toByteArray()
                    ) { device, sppUUID, result, data ->
                        val msg =
                            "-发送SPP数据- device = ${BluetoothUtil.printBtDeviceInfo(device)}, sppUUID = $sppUUID, " +
                                    "result = $result, data = ${CHexConver.byte2HexStr(data)}"
                        ToastUtil.showToastShort(msg)
                        JL_Log.d("zzc", msg)
                    }
                } else {
                    ToastUtil.showToastShort("请先连接设备")
                }
            }
        }
        rg_settings_communication_way.setOnCheckedChangeListener { group, checkedId ->
            cb_settings_use_spp_private_channel.isEnabled = checkedId == R.id.rb_settings_way_spp
        }
        if (communicationWay == JL_Constant.PROTOCOL_SPP) {
            rb_settings_way_spp.isChecked = true
        } else {
            rb_settings_way_ble.isChecked = true
        }
        cb_settings_use_spp_private_channel.isChecked = useSppPrivateChannel
        cb_settings_use_spp_private_channel.isEnabled = communicationWay == JL_Constant.PROTOCOL_SPP
        cb_settings_custom_auto_test_ota.isChecked = autoTestOTA
        val isShowSpp = communicationWay == JL_Constant.PROTOCOL_SPP && useSppPrivateChannel
        if (isShowSpp) {
            cl_settings_send_spp_private_data.visibility = View.VISIBLE
        } else {
            cl_settings_send_spp_private_data.visibility = View.GONE
        }

        SppManager.getInstance().registerSppEventCallback(sppEventCallback)
    }

    private fun checkIsChangeConfiguration() {
        val isUseDeviceAuthNow = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH)
        val isHidDeviceNow = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY)
        val useCustomReconnectWayNow = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(
                JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                JL_Constant.NEED_CUSTOM_RECONNECT_WAY
            )
        val way = PreferencesHelper.getSharedPreferences(applicationContext)
            .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)
        val usePrivateChannel = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL)
        val autoTestOta = PreferencesHelper.getSharedPreferences(applicationContext)
            .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA)
        isChangeConfiguration =
            (isUseDeviceAuthNow != isUseDeviceAuth || isHidDeviceNow != isHidDevice || useCustomReconnectWayNow != useCustomReconnectWay
                    || way != communicationWay || usePrivateChannel != useSppPrivateChannel|| autoTestOta != autoTestOTA)
    }

    private val sppEventCallback = object : SppEventCallback() {
        override fun onReceiveSppData(device: BluetoothDevice?, uuid: UUID?, data: ByteArray?) {
            if (CUSTOM_UUID == uuid) {
                ToastUtil.showToastShort("接收到的SPP数据==> data = ${CHexConver.byte2HexStr(data)}")
                JL_Log.i(
                    "zzc",
                    "-onReceiveSppData- device = ${BluetoothUtil.printBtDeviceInfo(device)}," +
                            " data = ${CHexConver.byte2HexStr(data)}"
                )
            }
        }
    }
}
