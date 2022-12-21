package com.jieli.otasdk.fragments

import android.bluetooth.BluetoothDevice
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

import com.jieli.component.utils.HandlerManager
import com.jieli.jl_bt_ota.util.BluetoothUtil
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.R
import com.jieli.otasdk.tool.ota.ble.BleManager
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.util.JL_Constant

class ScanDeviceAdapter(data: MutableList<BluetoothDevice>?) :
    BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(R.layout.item_device_list, data) {

    private var communicationWay : Int =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)

    override fun convert(holder: BaseViewHolder, item: BluetoothDevice) {
        item.run {
            holder.setText(R.id.tv_device_name, name)
            var view: View? = holder.getView(R.id.iv_device_status);
            holder.setImageResource(
                R.id.iv_device_status,
                if (isConnectedDevice(item)) R.drawable.ic_device_sel else R.drawable.ic_device_normal
            )


        }
    }

    fun addDevice(device: BluetoothDevice?) {

        data.let {
            if (!it.contains(device)) {
                device?.let { it1 -> it.add(it1) }
            }
            HandlerManager.getInstance().mainHandler.removeCallbacks(refreshTask);
            HandlerManager.getInstance().mainHandler.postDelayed(refreshTask, 300);
        }

    }

    private var refreshTask: Runnable = Runnable { notifyDataSetChanged() }

    private fun isConnectedDevice(device: BluetoothDevice?): Boolean {
        if (communicationWay == JL_Constant.PROTOCOL_BLE) {
            return BluetoothUtil.deviceEquals(
                BleManager.getInstance().connectedBtDevice,
                device
            )
        } else {
            return BluetoothUtil.deviceEquals(
                SppManager.getInstance().connectedSppDevice,
                device
            )
        }
        return false
    }

}