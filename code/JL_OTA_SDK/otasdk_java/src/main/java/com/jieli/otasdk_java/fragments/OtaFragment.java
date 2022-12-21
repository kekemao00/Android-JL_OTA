package com.jieli.otasdk_java.fragments;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jieli.component.ui.CommonDecoration;
import com.jieli.component.utils.FileUtil;
import com.jieli.component.utils.ToastUtil;
import com.jieli.jl_bt_ota.constant.ErrorCode;
import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.jl_bt_ota.util.PreferencesHelper;
import com.jieli.otasdk_java.tool.IOtaContract;
import com.jieli.otasdk_java.util.JL_Constant;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.databinding.FragmentOtaBinding;
import com.jieli.otasdk_java.tool.OtaPresenter;
import com.jieli.otasdk_java.util.FileObserverCallback;
import com.jieli.otasdk_java.util.OtaFileObserverHelper;
import com.jieli.otasdk_java.util.auto_test.AutoTestStatisticsUtil;
import com.jieli.otasdk_java.util.auto_test.AutoTestTaskManager;
import com.jieli.otasdk_java.util.auto_test.TestTask;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;

/**
 * @ClassName: OtaFragment
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 16:22
 */
public class OtaFragment extends Fragment implements IOtaContract.IOtaView {
    private String tag = OtaFragment.class.getSimpleName();
    private IOtaContract.IOtaPresenter otaHelper = null;
    private FileAdapter adapter = null;
    private AutoTestStatisticsUtil mAutoTestStatisticsUtil = new AutoTestStatisticsUtil();
    private final int MSG_UPDATE_OTA_FILE_LIST = 0x01;
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_UPDATE_OTA_FILE_LIST) {
                readFileList();
            }
            return false;
        }
    });
    private FragmentOtaBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        otaHelper = new OtaPresenter(this, requireActivity().getApplicationContext());
        AutoTestTaskManager.getInstance().otaPresenter = otaHelper;
    }

    @Override
    public void onStart() {
        super.onStart();
        otaHelper.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_ota, container, false);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        binding.rvFileList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFileList.addItemDecoration(new CommonDecoration(getContext(), RecyclerView.VERTICAL));
        adapter = new FileAdapter(new ArrayList<>());
        View emptyView = LayoutInflater.from(getContext()).inflate(R.layout.view_file_empty, null);
        TextView tvTips = emptyView.findViewById(R.id.tv_file_empty_tips);
        tvTips.setMovementMethod(ScrollingMovementMethod.getInstance());
        adapter.setEmptyView(emptyView);
        binding.rvFileList.setAdapter(adapter);
        adapter.setOnItemClickListener((viewAdapter, view, position) -> {
            adapter.setSelectedIndex(position);
        });
        binding.btnUpgrade.setOnClickListener(v -> {
            String path = adapter.getSelectedItem().getPath();
            JL_Log.w(tag, "ota file path : " + path);
            if (path == null) return;
            if (checkFile(path)) {
                boolean autoTestOTA =
                        PreferencesHelper.getSharedPreferences(getActivity().getApplicationContext())
                                .getBoolean(JL_Constant.KEY_AUTO_TEST_OTA, JL_Constant.AUTO_TEST_OTA);
                if (autoTestOTA) {//自动化测试OTA
                    if (!AutoTestTaskManager.getInstance().isAutoTesting()) {//正在自动化测试
                        BluetoothDevice connectedDevice = otaHelper.getConnectedDevice();
                        if (connectedDevice != null) {
                            AutoTestTaskManager.getInstance().startAutoTest(connectedDevice, path);
                            mAutoTestStatisticsUtil.clear();
                            mAutoTestStatisticsUtil.testSum = AutoTestTaskManager.AUTO_TEST_OTA_COUNT;
                            updateAutoOTAProgressTv(
                                    mAutoTestStatisticsUtil.currentCount,
                                    mAutoTestStatisticsUtil.testSum,
                                    View.VISIBLE
                            );
                        }
                    } else {
                        ToastUtil.showToastShort(getString(R.string.auto_test_ota_tip));
                    }
                } else {//普通的OTA
                    otaHelper.startOTA(path);
                    updateAutoOTAProgressTv(
                            mAutoTestStatisticsUtil.currentCount,
                            mAutoTestStatisticsUtil.testSum,
                            View.GONE
                    );
                }
            } else {
                ToastUtil.showToastShort(getString(R.string.ota_please_chose_file));
            }
        });
        binding.ivRefreshFileList.setOnClickListener(v -> {
            readFileList();
        });
        updateDeviceConnectedStatus(otaHelper.isDevConnected());
        OtaFileObserverHelper.getInstance().registerFileObserverCallback(new FileObserverCallback() {
            @Override
            public void onChange(int event, String path) {
                if (path != null) {
                    mHandler.removeMessages(MSG_UPDATE_OTA_FILE_LIST);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_OTA_FILE_LIST, 300);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        readFileList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        otaHelper.destroy();
    }

    private void updateConnectedDeviceInfo(BluetoothDevice device) {
        if (isAdded() && !isDetached()) {
            if (device == null) {
                binding.tvConnectDevNameVale.setText("");
                binding.tvConnectDevAddressVale.setText("");
                binding.tvConnectDevTypeVale.setText("");
            } else {
                binding.tvConnectDevNameVale.setText(device.getName());
                binding.tvConnectDevAddressVale.setText(device.getAddress());
                binding.tvConnectDevTypeVale.setText(getBtDeviceTypeString(device.getType()));
            }
        }
    }

    private String getBtDeviceTypeString(int type) {
        String typeName = "";
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                typeName =
                        getString(R.string.device_type_classic);
                break;
            case BluetoothDevice.DEVICE_TYPE_LE:
                typeName = getString(R.string.device_type_ble);
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                typeName = getString(R.string.device_type_dual_mode);
                break;
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                typeName =
                        getString(R.string.device_type_unknown);
                break;
        }
        return typeName;
    }

    private void updateUpgradeTips(String content, int visibility) {
        if (isAdded() && !isDetached()) {
            binding.tvUpgradeTips.setVisibility(visibility);
            if (visibility == View.VISIBLE && content != null) {
                binding.tvUpgradeTips.setText(content);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateUpgradeProgressTv(float progress, int visibility) {
        if (isAdded() && !isDetached()) {
            binding.tvUpgradeProgress.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                int cProgress = (int) progress;
                binding.tvUpgradeProgress.setText(cProgress + "%");
            }
        }
    }

    private void updateUpgradeProgressPb(float progress, int visibility) {
        if (isAdded() && !isDetached()) {
            binding.barUpgrade.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                int cProgress = (int) progress;
                binding.barUpgrade.setProgress(cProgress);
            }
        }
    }

    private void updateAutoOTAProgressTv(int progress, int sum, int visibility) {
        if (isAdded() && !isDetached()) {
            binding.tvAutoTestProgress.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                binding.tvAutoTestProgress.setText(getString(R.string.auto_test_ota) + ": " + progress + "/" + sum);
            }
        }
    }

    private boolean checkFile(String path) {
        JL_Log.i("sen", "checkFile-->" + path);

        File file = new File(path);

        if (!file.exists()) {
            return false;
        }

        //todo 升级文件校验


        return true;
    }

    //最多支持5个文件
    private void readFileList() {

        String parentPath = FileUtil.splicingFilePath(
                getActivity(),
                getActivity().getPackageName(),
                JL_Constant.DIR_UPGRADE,
                null,
                null
        );
        File parent = new File(parentPath);
        ArrayList<File> files = new ArrayList<File>();
        if (parent.exists()) {
            for (File file : parent.listFiles()) {
                if (file.getName().endsWith(".ufw") || file.getName().endsWith(".bfu")) {
                    files.add(file);
                }
            }
        } else {
            parent.mkdirs();
        }

        JL_Log.w(tag, "readFileList ---> ${files.size}, adapter = $adapter");

        adapter.setNewInstance(new ArrayList<>());
        for (File file : files) {
            adapter.addData(file);
        }
    }

    private void updateOtaUiStatus(boolean isUpgrade) {
        if (isUpgrade) {
            binding.btnUpgrade.setVisibility(View.INVISIBLE);
            binding.barUpgrade.setVisibility(View.VISIBLE);
        } else {
            binding.btnUpgrade.setVisibility(View.VISIBLE);
            binding.barUpgrade.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
        if (otaHelper.isOTA()) {
            otaHelper.cancelOTA();
        }
    }

    private void updateDeviceConnectedStatus(Boolean connected) {
        int visibility = View.INVISIBLE;
        if (connected) {
            visibility = View.VISIBLE;
        }
        if (connected) {
            binding.tvConnectStatus.setText(getString(R.string.device_status_connected));
            binding.btnUpgrade.setEnabled(true);
            binding.btnUpgrade.setVisibility(visibility);
            binding.btnUpgrade.setBackgroundResource(R.drawable.bg_btn_upgrade);
            updateUpgradeTips(getString(R.string.ota_upgrade_not_started), visibility);
            updateUpgradeProgressTv(0.0f, View.GONE);
            updateUpgradeProgressPb(0.0f, visibility);
            updateConnectedDeviceInfo(otaHelper.getConnectedDevice());
        } else {
            binding.tvConnectStatus.setText(getString(R.string.device_status_disconnected));
            binding.btnUpgrade.setEnabled(false);
            binding.btnUpgrade.setVisibility(View.VISIBLE);
            binding.btnUpgrade.setBackgroundResource(R.drawable.dbg_btn_unenable);

            updateUpgradeTips(null, View.VISIBLE);
            updateUpgradeProgressTv(0.0f, View.GONE);
            binding.barUpgrade.setVisibility(visibility);
            updateConnectedDeviceInfo(null);
        }
    }

    @Override
    public boolean isViewShow() {
        return isVisible();
    }

    @Override
    public void onConnection(@org.jetbrains.annotations.Nullable BluetoothDevice device, int status) {
        if (getAutoTestView() != null) {
            getAutoTestView().onConnection(device, status);
        }
        if (!otaHelper.isOTA()) {
            updateDeviceConnectedStatus(otaHelper.isDevConnected());
        }
    }

    @Override
    public void onMandatoryUpgrade() {
        if (getAutoTestView() != null) {
            getAutoTestView().onMandatoryUpgrade();
        }
        ToastUtil.showToastShort(R.string.device_must_mandatory_upgrade);
    }

    @Override
    public void onOTAStart() {
        mAutoTestStatisticsUtil.currentCount++;
        if (getAutoTestView() != null) {
            getAutoTestView().onOTAStart();
        }
        if (!isAdded()) return;
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateUpgradeProgressPb(0.0f, View.INVISIBLE);
        updateUpgradeProgressTv(0.0f, View.INVISIBLE);
        updateUpgradeTips(getString(R.string.ota_checking_upgrade_file), View.VISIBLE);
        updateOtaUiStatus(true);
        updateAutoOTAProgressTv(
                mAutoTestStatisticsUtil.currentCount,
                mAutoTestStatisticsUtil.testSum,
                View.VISIBLE
        );
    }

    @Override
    public void onOTAReconnect(@org.jetbrains.annotations.Nullable String btAddr) {
        if (getAutoTestView() != null) {
            getAutoTestView().onOTAReconnect(btAddr);
        }
        //TODO:实现自定义回连
        if (getContext() != null && PreferencesHelper.getSharedPreferences(getContext()).getBoolean(
                JL_Constant.KEY_USE_CUSTOM_RECONNECT_WAY,
                JL_Constant.NEED_CUSTOM_RECONNECT_WAY
        )
        ) {
            otaHelper.reconnectDev(btAddr);
        }
    }

    @Override
    public void onOTAProgress(int type, float progress) {
        if (getAutoTestView() != null) {
            getAutoTestView().onOTAProgress(type, progress);
        }
        if (!isAdded()) return;
        if (progress > 0) {
            updateUpgradeProgressTv(progress, View.VISIBLE);
            String message = type == 0 ? getString(R.string.ota_check_file) : getString(R.string.ota_upgrading);
            updateUpgradeTips(message, View.VISIBLE);
        } else {
            updateUpgradeProgressTv(0.0f, View.INVISIBLE);
        }
        updateUpgradeProgressPb(progress, View.VISIBLE);
    }

    @Override
    public void onOTAStop() {
        if (getAutoTestView() != null) {
            getAutoTestView().onOTAStop();
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                updateUpgradeProgressTv(0.0f, View.GONE);
                updateUpgradeTips(getString(R.string.ota_complete), View.VISIBLE);
//                    updateOtaUiStatus(false)
                updateDeviceConnectedStatus(false);
            }
        });
    }

    @Override
    public void onOTACancel() {
        if (getAutoTestView() != null) {
            getAutoTestView().onOTACancel();
        }
        if (!isAdded()) return;
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateUpgradeProgressTv(0.0f, View.GONE);
        updateUpgradeTips(getString(R.string.ota_upgrade_cancel), View.VISIBLE);
        updateOtaUiStatus(false);
    }

    @Override
    public void onOTAError(int code, @NotNull String message) {
        if (getAutoTestView() != null) {
            getAutoTestView().onOTAError(code, message);
        }
        JL_Log.e(tag, "startOta has error : " + message);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    if (code == ErrorCode.SUB_ERR_OTA_IN_HANDLE) {
                        ToastUtil.showToastShort(message);
                        return;
                    } else if (code == ErrorCode.SUB_ERR_FILE_NOT_FOUND) {
                        readFileList();
                    }
                    String text = String.format(getString(R.string.ota_upgrade_failed), message);
                    updateUpgradeProgressTv(0.0f, View.GONE);
                    updateUpgradeTips(text, View.VISIBLE);
                    updateOtaUiStatus(false);
                }
            }
        });
    }

    @Override
    public void setPresenter(@NotNull IOtaContract.IOtaPresenter presenter) {
        otaHelper = presenter;
    }

    private IOtaContract.IOtaView getAutoTestView() {
        TestTask testTask = AutoTestTaskManager.getInstance().currentTestTask;
        if (testTask instanceof IOtaContract.IOtaView) {
            return (IOtaContract.IOtaView) testTask;
        } else {
            return null;
        }
    }
}
