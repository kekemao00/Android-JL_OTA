package com.jieli.otasdk_java.fragments;

import android.bluetooth.BluetoothDevice;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.jieli.component.utils.HandlerManager;
import com.jieli.component.utils.PreferencesHelper;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.otasdk_java.MainApplication;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.tool.ota.ble.BleManager;
import com.jieli.otasdk_java.tool.ota.spp.SppManager;
import com.jieli.otasdk_java.util.JL_Constant;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @ClassName: ScanDeviceAdapter
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:44
 */
public class ScanDeviceAdapter extends BaseQuickAdapter<BluetoothDevice, BaseViewHolder> {
    private int communicationWay =
            PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);

    public ScanDeviceAdapter(@Nullable List<BluetoothDevice> data) {
        super(R.layout.item_device_list, data);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, BluetoothDevice device) {
        if (device==null)return;
        baseViewHolder.setText(R.id.tv_device_name, device.getName());
        baseViewHolder.setImageResource(
                R.id.iv_device_status, isConnectedDevice(device) ? R.drawable.ic_device_sel : R.drawable.ic_device_normal
        );
    }

    public void addDevice(BluetoothDevice device) {
        List<BluetoothDevice> dataList = getData();
        if (!dataList.contains(device)) {
            if (device != null) {
                dataList.add(device);
            }
        }
        HandlerManager.getInstance().getMainHandler().removeCallbacks(refreshTask);
        HandlerManager.getInstance().getMainHandler().postDelayed(refreshTask, 300);
    }

    private Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
        }
    };

    private boolean isConnectedDevice(BluetoothDevice device) {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return BluetoothUtil.deviceEquals(
                    BleManager.getInstance().getConnectedBtDevice(),
                    device
            );
        } else {
            return BluetoothUtil.deviceEquals(
                    SppManager.getInstance().getConnectedSppDevice(),
                    device
            );
        }
    }

}
