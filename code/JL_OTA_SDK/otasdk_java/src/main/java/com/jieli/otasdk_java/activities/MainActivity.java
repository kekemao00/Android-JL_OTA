package com.jieli.otasdk_java.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jieli.component.ui.widget.NoScrollViewPager;
import com.jieli.component.utils.ToastUtil;
import com.jieli.jl_bt_ota.util.PreferencesHelper;
import com.jieli.otasdk_java.base.BaseActivity;
import com.jieli.otasdk_java.fragments.OtaFragment;
import com.jieli.otasdk_java.fragments.ScanFragment;
import com.jieli.otasdk_java.util.JL_Constant;
import com.jieli.otasdk_java.MainApplication;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.tool.ota.ble.BleManager;
import com.jieli.otasdk_java.tool.ota.spp.SppManager;
import com.jieli.otasdk_java.util.AppUtil;

import org.jetbrains.annotations.Nullable;

/**
 * @ClassName: MainActivity
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 11:54
 */
public class MainActivity extends BaseActivity {
    private MainBroadcast mainBroadcast = null;
    private int communicationWay =
            PreferencesHelper.getSharedPreferences(MainApplication.getInstance()).
                    getInt(JL_Constant.KEY_COMMUNICATION_WAY, JL_Constant.CURRENT_PROTOCOL);
    private NoScrollViewPager viewpage_main;
    private BottomNavigationView bar_main_bottom;
    private RadioGroup rg_main;
    private TextView tv_title;
    private ImageView iv_main_settings;
    private RadioButton tab_upgrade;
    private RadioButton tab_device;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Fragment[] fragments = new Fragment[2];
        fragments[0] = new OtaFragment();
        fragments[1] = new ScanFragment();
        viewpage_main = findViewById(R.id.viewpage_main);
        bar_main_bottom = findViewById(R.id.bar_main_bottom);
        rg_main = findViewById(R.id.rg_main);
        tv_title = findViewById(R.id.tv_title);
        iv_main_settings = findViewById(R.id.iv_main_settings);
        tab_device = findViewById(R.id.tab_device);
        tab_upgrade = findViewById(R.id.tab_upgrade);

        viewpage_main.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @NonNull
            @Override
            public Fragment getItem(int position) {
                return fragments[position];
            }

            @Override
            public int getCount() {
                return fragments.length;
            }
        });
        bar_main_bottom.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_connect:
                        switchSubFragment(1, false);
                        break;
                    case R.id.action_upgrade:
                        switchSubFragment(0, false);
                        break;
                }
                return false;
            }
        });
        rg_main.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.tab_device:
                        switchSubFragment(1, false);
                        break;
                    case R.id.tab_upgrade:
                        switchSubFragment(0, false);
                        break;
                }
                TextView textView = MainActivity.this.findViewById(checkedId);
                tv_title.setText(textView.getText());
            }
        });

        iv_main_settings.setOnClickListener(v -> toSettingsActivity());

        //rg_main.check(R.id.tab_device)

        registerMainReceiver();

        Boolean isConnected;
        if (communicationWay == JL_Constant.PROTOCOL_SPP) {
            isConnected = SppManager.getInstance().getConnectedSppDevice() != null;
        } else {
            isConnected = BleManager.getInstance().getConnectedBtDevice() != null;
        }
        if (isConnected) {
            switchSubFragment(0, false);
        } else {
            switchSubFragment(1, false);
        }
    }

    @Override
    public void onBackPressed() {
        if (!AppUtil.isFastDoubleClick()) {
            ToastUtil.showToastShort(R.string.double_tap_to_exit);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterMainReceiver();
        BleManager.getInstance().destroy();
        SppManager.getInstance().release();
    }

    public void switchSubFragment(int itemIndex, Boolean smoothScroll) {
        Log.d(TAG, "switchSubFragment: "+itemIndex);
        if (!isDestroyed() && !isFinishing()) {
            viewpage_main.setCurrentItem(itemIndex, smoothScroll);
            switch (itemIndex) {
                case 0:
                    tab_upgrade.setChecked(true);
                    break;
                case 1:
                    tab_device.setChecked(true);
                    break;
            }
        }
    }

    private void toSettingsActivity() {
        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
    }

    private void registerMainReceiver() {
        if (mainBroadcast == null) {
            mainBroadcast = new MainBroadcast();
            IntentFilter filter = new IntentFilter();
            filter.addAction(JL_Constant.ACTION_EXIT_APP);
            registerReceiver(mainBroadcast, filter);
        }
    }

    private void unregisterMainReceiver() {
        if (mainBroadcast != null) {
            unregisterReceiver(mainBroadcast);
            mainBroadcast = null;
        }
    }

    private class MainBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (JL_Constant.ACTION_EXIT_APP == intent.getAction()) {
                MainActivity.this.finish();
            }
        }
    }
}
