package com.jieli.otasdk.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.jieli.component.utils.ToastUtil
import com.jieli.jl_bt_ota.util.PreferencesHelper
import com.jieli.otasdk.MainApplication
import com.jieli.otasdk.R
import com.jieli.otasdk.base.BaseActivity
import com.jieli.otasdk.fragments.OtaFragment
import com.jieli.otasdk.fragments.ScanFragment
import com.jieli.otasdk.tool.ota.ble.BleManager
import com.jieli.otasdk.tool.ota.spp.SppManager
import com.jieli.otasdk.util.AppUtil
import com.jieli.otasdk.util.JL_Constant
import com.jieli.otasdk.util.OtaFileObserverHelper
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity() {

    private var mainBroadcast: MainBroadcast? = null
    private var communicationWay : Int =
        PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fragments = arrayOf<Fragment>(OtaFragment(), ScanFragment())
        viewpage_main.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(i: Int): Fragment {
                return fragments[i]
            }

            override fun getCount(): Int {
                return fragments.size
            }
        }

        bar_main_bottom.setOnNavigationItemSelectedListener { menuItem ->

            when (menuItem.itemId) {
                R.id.action_connect -> switchSubFragment(1, false)
                R.id.action_upgrade -> switchSubFragment(0, false)
            }
            false
        }

        rg_main.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.tab_device -> switchSubFragment(1, false)
                R.id.tab_upgrade -> switchSubFragment(0, false)
            }

            val textView = findViewById<TextView>(checkedId);
            tv_title.text = textView?.text
        }

        iv_main_settings.setOnClickListener { v ->
            toSettingsActivity()
        }

        //rg_main.check(R.id.tab_device)

        registerMainReceiver()

        val isConnected: Boolean
        if (communicationWay == JL_Constant.PROTOCOL_SPP) {
            isConnected = SppManager.getInstance().connectedSppDevice != null
        } else {
            isConnected = BleManager.getInstance().connectedBtDevice != null
        }
        if (isConnected) {
            switchSubFragment(0, false)
        } else {
            switchSubFragment(1, false)
        }
    }

    override fun onBackPressed() {
        if (!AppUtil.isFastDoubleClick()) {
            ToastUtil.showToastShort(R.string.double_tap_to_exit)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterMainReceiver()
        OtaFileObserverHelper.getInstance().destroy()
        BleManager.getInstance().destroy()
        SppManager.getInstance().release()
    }

    fun switchSubFragment(itemIndex: Int, smoothScroll: Boolean) {
        if (!isDestroyed && !isFinishing) {
            viewpage_main?.setCurrentItem(itemIndex, smoothScroll)
            when (itemIndex) {
                0 -> tab_upgrade.isChecked = true
                1 -> tab_device.isChecked = true
            }
        }
    }

    private fun toSettingsActivity() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun registerMainReceiver() {
        if (mainBroadcast == null) {
            mainBroadcast = MainBroadcast()
            val filter = IntentFilter()
            filter.addAction(JL_Constant.ACTION_EXIT_APP)
            registerReceiver(mainBroadcast, filter)
        }
    }

    private fun unregisterMainReceiver() {
        if (mainBroadcast != null) {
            unregisterReceiver(mainBroadcast)
            mainBroadcast = null
        }
    }

    private inner class MainBroadcast : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (JL_Constant.ACTION_EXIT_APP == intent.action) {
                this@MainActivity.finish()
            }
        }
    }
}
