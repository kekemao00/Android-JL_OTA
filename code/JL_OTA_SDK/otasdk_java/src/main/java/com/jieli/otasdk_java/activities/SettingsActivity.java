package com.jieli.otasdk_java.activities;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.RadioGroup;

import androidx.databinding.DataBindingUtil;

import com.jieli.component.utils.SystemUtil;
import com.jieli.component.utils.ToastUtil;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_bt_ota.util.CHexConver;
import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.jl_bt_ota.util.PreferencesHelper;
import com.jieli.otasdk_java.BuildConfig;
import com.jieli.otasdk_java.base.BaseActivity;
import com.jieli.otasdk_java.util.JL_Constant;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.databinding.ActivitySettingsBinding;
import com.jieli.otasdk_java.tool.ota.spp.SppManager;
import com.jieli.otasdk_java.tool.ota.spp.interfaces.OnWriteSppDataCallback;
import com.jieli.otasdk_java.tool.ota.spp.interfaces.SppEventCallback;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @ClassName: SettingsActivity
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 14:29
 */
public class SettingsActivity extends BaseActivity {

    public static UUID CUSTOM_UUID = SppManager.UUID_CUSTOM_SPP;

    public static SettingsActivity newInstance() {
        return new SettingsActivity();
    }

    private boolean isChangeConfiguration = false;
    private boolean isUseDeviceAuth = false;
    private boolean isHidDevice = false;
    private boolean useCustomReconnectWay = false;
    private int communicationWay = JL_Constant.CURRENT_PROTOCOL;
    private boolean useSppPrivateChannel = false;
    private boolean autoTestOTA = false;
    private ActivitySettingsBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_settings);
        isUseDeviceAuth = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH);
        isHidDevice = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY);
        useCustomReconnectWay = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(
                        JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                        JL_Constant.NEED_CUSTOM_RECONNECT_WAY
                );
        communicationWay = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);
        useSppPrivateChannel = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL);
        autoTestOTA = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA);
        initView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isChangeConfiguration = false;
        SppManager.getInstance().unregisterSppEventCallback(sppEventCallback);
    }

    private void initView() {
        binding.tvSettingsVersionValue.setText(String.format(" : %s", SystemUtil.getVersioName(getApplicationContext())));
        if (!BuildConfig.DEBUG) {
            binding.tvSettingsTopRight.setVisibility(View.GONE);
            binding.clSettingsContainer.setVisibility(View.GONE);
            return;
        }
        binding.tvSettingsTopRight.setVisibility(View.VISIBLE);
        binding.clSettingsContainer.setVisibility(View.VISIBLE);
        binding.cbSettingsDeviceAuth.setChecked(isUseDeviceAuth);
        binding.cbSettingsHidDevice.setChecked(isHidDevice);
        binding.cbSettingsCustomReconnectWay.setChecked(useCustomReconnectWay);
        binding.tvSettingsTopLeft.setOnClickListener(v -> {
            finish();
        });
        binding.tvSettingsTopRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PreferencesHelper.putBooleanValue(
                        getApplicationContext(),
                        JL_Constant.KEY_IS_USE_DEVICE_AUTH,
                        binding.cbSettingsDeviceAuth.isChecked()
                );
                PreferencesHelper.putBooleanValue(
                        getApplicationContext(),
                        JL_Constant.KEY_IS_HID_DEVICE,
                        binding.cbSettingsHidDevice.isChecked()
                );
                PreferencesHelper.putBooleanValue(
                        getApplicationContext(),
                        JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                        binding.cbSettingsCustomReconnectWay.isChecked()
                );
                int way;
                if (binding.rbSettingsWayBle.isChecked()) {
                    way = JL_Constant.PROTOCOL_BLE;
                } else if (binding.rbSettingsWaySpp.isChecked()) {
                    way = JL_Constant.PROTOCOL_SPP;
                } else {
                    way = communicationWay;
                }
                PreferencesHelper.putIntValue(
                        getApplicationContext(),
                        JL_Constant.KEY_COMMUNICATION_WAY,
                        way
                );
                PreferencesHelper.putBooleanValue(
                        getApplicationContext(),
                        JL_Constant.KEY_SPP_MULTIPLE_CHANNEL,
                        binding.cbSettingsUseSppPrivateChannel.isChecked()
                );
                PreferencesHelper.putBooleanValue(
                        getApplicationContext(),
                        JL_Constant.KEY_AUTO_TEST_OTA,
                        binding.cbSettingsCustomAutoTestOta.isChecked()
                );
                checkIsChangeConfiguration();
                if (isChangeConfiguration) {
                    isChangeConfiguration = false;
                    ToastUtil.showToastShort(R.string.settings_success_and_restart);
//                SystemUtil.restartApp(applicationContext)
                    new Handler().postDelayed(() -> {
                        finish();
                        sendBroadcast(new Intent(JL_Constant.ACTION_EXIT_APP));
                    }, 1000L);
                }
            }
        });

        binding.btnSendFileSnapdrop.setOnClickListener(v -> {
            Intent intent = new Intent(this, SnapDropActivity.class);
            startActivity(intent);
        });
        binding.btnSendSppData.setOnClickListener(v -> {
            String text = binding.etSettingsInputData.getText().toString().trim();
            if (text != null) {
                if (SppManager.getInstance().getConnectedSppDevice() != null) {
                    SppManager.getInstance().writeDataToSppAsync(
                            SppManager.getInstance().getConnectedSppDevice(),
                            CUSTOM_UUID,
                            text.getBytes(), new OnWriteSppDataCallback() {
                                @Override
                                public void onSppResult(BluetoothDevice device, UUID sppUUID, boolean result, byte[] data) {
                                    String msg =
                                            "-发送SPP数据- device = " + BluetoothUtil.printBtDeviceInfo(device) + ", sppUUID =" + sppUUID + " , result = " + result + ", data = " + CHexConver.byte2HexStr(data);
                                    ToastUtil.showToastShort(msg);
                                    JL_Log.d("zzc", msg);
                                }
                            }
                    );
                } else {
                    ToastUtil.showToastShort("请先连接设备");
                }
            }
        });
        binding.rgSettingsCommunicationWay.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                binding.cbSettingsUseSppPrivateChannel.setEnabled(checkedId == R.id.rb_settings_way_spp);
            }
        });
        if (communicationWay == JL_Constant.PROTOCOL_SPP) {
            binding.rbSettingsWaySpp.setChecked(true);
        } else {
            binding.rbSettingsWayBle.setChecked(true);
        }
        binding.cbSettingsUseSppPrivateChannel.setChecked(useSppPrivateChannel);
        binding.cbSettingsUseSppPrivateChannel.setEnabled(communicationWay == JL_Constant.PROTOCOL_SPP);
        binding.cbSettingsCustomAutoTestOta.setChecked(autoTestOTA);
        boolean isShowSpp = communicationWay == JL_Constant.PROTOCOL_SPP && useSppPrivateChannel;
        binding.clSettingsSendSppPrivateData.setVisibility(isShowSpp ? View.VISIBLE : View.GONE);
        SppManager.getInstance().registerSppEventCallback(sppEventCallback);
    }

    private void checkIsChangeConfiguration() {
        boolean isUseDeviceAuthNow = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_USE_DEVICE_AUTH, JL_Constant.IS_NEED_DEVICE_AUTH);
        boolean isHidDeviceNow = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_IS_HID_DEVICE, JL_Constant.HID_DEVICE_WAY);
        boolean useCustomReconnectWayNow = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(
                        JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                        JL_Constant.NEED_CUSTOM_RECONNECT_WAY
                );
        int way = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);
        boolean usePrivateChannel = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_SPP_MULTIPLE_CHANNEL, JL_Constant.USE_SPP_MULTIPLE_CHANNEL);
        boolean autoTestOta = PreferencesHelper.getSharedPreferences(getApplicationContext())
                .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA);
        isChangeConfiguration =
                (isUseDeviceAuthNow != isUseDeviceAuth || isHidDeviceNow != isHidDevice || useCustomReconnectWayNow != useCustomReconnectWay
                        || way != communicationWay || usePrivateChannel != useSppPrivateChannel || autoTestOta != autoTestOTA);
    }

    private SppEventCallback sppEventCallback = new SppEventCallback() {
        @Override
        public void onReceiveSppData(BluetoothDevice device, UUID uuid, byte[] data) {
            super.onReceiveSppData(device, uuid, data);
            if (CUSTOM_UUID == uuid) {
                ToastUtil.showToastShort("接收到的SPP数据==> data = ${CHexConver.byte2HexStr(data)}");
                JL_Log.i(
                        "zzc",
                        "-onReceiveSppData- device = ${BluetoothUtil.printBtDeviceInfo(device)}," +
                                " data = ${CHexConver.byte2HexStr(data)}"
                );
            }
        }
    };
}
