package com.jieli.otasdk_java.fragments;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ajguan.library.EasyRefreshLayout;
import com.ajguan.library.LoadModel;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.jieli.component.utils.ValueUtil;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.util.BluetoothUtil;
import com.jieli.jl_dialog.Jl_Dialog;
import com.jieli.otasdk_java.tool.DevScanPresenter;
import com.jieli.otasdk_java.tool.IDeviceContract;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.activities.MainActivity;
import com.jieli.otasdk_java.databinding.FragmentScanBinding;
import com.jieli.otasdk_java.tool.IOtaContract;
import com.jieli.otasdk_java.util.JL_Constant;
import com.jieli.otasdk_java.util.auto_test.AutoTestTaskManager;
import com.jieli.otasdk_java.util.auto_test.TestTask;
import com.jieli.otasdk_java.widget.SpecialDecoration;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @ClassName: ScanFragment
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 17:44
 */
public class ScanFragment extends Fragment implements IDeviceContract.IDevScanView {
    IDeviceContract.IDevScanPresenter mPresenter = null;
    private Jl_Dialog mNotifyDialog = null;
    private ScanDeviceAdapter adapter = null;
    private MainActivity activity = null;
    Handler mHandler = new Handler(Looper.getMainLooper());
    FragmentScanBinding binding;
    Boolean isRefreshing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_scan, container, false);
        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (activity == null && getContext() instanceof MainActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (activity == null && getActivity() instanceof MainActivity) {
            activity = (MainActivity) getActivity();
        }
        mPresenter = new DevScanPresenter(this);
        AutoTestTaskManager.getInstance().devScanPresenter = mPresenter;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        init();
    }

    @Override
    public void onStart() {
        super.onStart();
        mPresenter.start();
    }


    private void init() {
        adapter = new ScanDeviceAdapter(new ArrayList<>());
        binding.rcDeviceList.setAdapter(adapter);
        binding.rcDeviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        int color = getResources().getColor(R.color.rc_decoration);
        binding.rcDeviceList.addItemDecoration(new SpecialDecoration(
                activity,
                LinearLayoutManager.VERTICAL,
                color,
                ValueUtil.dp2px(activity, 1)
        ));
        adapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull BaseQuickAdapter<?, ?> adapter, @NonNull View view, int position) {
                BluetoothDevice currentDevice = mPresenter.getConnectedDevice();
                BluetoothDevice device = (BluetoothDevice) adapter.getItem(position);
                if (currentDevice != null && BluetoothUtil.deviceEquals(currentDevice, device)) {
                    mPresenter.disconnectBtDevice(device);
                } else {
                    mPresenter.stopScan();
                    mPresenter.connectBtDevice(device);
                }
            }
        });
        binding.easyRefresh.addEasyEvent(new EasyRefreshLayout.EasyEvent() {
            @Override
            public void onLoadMore() {

            }

            @Override
            public void onRefreshing() {
                if (!isRefreshing) {
                    adapter.getData().clear();
                    mPresenter.startScan();
                    isRefreshing = true;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isRefreshing) {
                                binding.easyRefresh.refreshComplete();
                                isRefreshing = false;
                            }
                        }
                    }, 500);
                }
            }
        });
        binding.easyRefresh.setLoadMoreModel(LoadModel.NONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPresenter.destroy();
    }

    @Override
    public void setPresenter(@NotNull IDeviceContract.IDevScanPresenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void onScanStatus(int status, @org.jetbrains.annotations.Nullable BluetoothDevice device) {
        if (getAutoTestView() != null) {
            getAutoTestView().onScanStatus(status, device);
        }
        if (isDetached() || !isAdded()) return;
        switch (status) {
            case JL_Constant.SCAN_STATUS_FOUND_DEV:
                String filter = binding.edScanBleFilter.getText().toString();
                if (!TextUtils.isEmpty(device.getName()) && (TextUtils.isEmpty(filter)
                        || device.getName().contains(filter))
                ) {
                    adapter.addDevice(device);
                }
                break;
            default:
                boolean bStart = status == JL_Constant.SCAN_STATUS_SCANNING;
                if (bStart) {
                    adapter.getData().clear();
                    if (mPresenter.getConnectedDevice() != null) {
                        adapter.getData().add(mPresenter.getConnectedDevice());
                    }
                    adapter.notifyDataSetChanged();
                }
                activity.findViewById(R.id.bar_scan_status).setVisibility(bStart ? View.VISIBLE : View.INVISIBLE);
                binding.tvScanTip.setText(bStart ? getString(R.string.scaning_tip) : getString(R.string.scan_tip));
                break;
        }
    }

    @Override
    public void onConnectStatus(@org.jetbrains.annotations.Nullable BluetoothDevice device, int status) {
        if (getAutoTestView()!=null){
            getAutoTestView().onConnectStatus(device, status);
        }
        if (status == StateCode.CONNECTION_CONNECTING) {
            showConnectionDialog();
        } else {
            if (status == StateCode.CONNECTION_CONNECTED) {
                adapter.addDevice(device);
            }
            dismissConnectionDialog();
            mPresenter.startScan();
        }
    }

    @Override
    public void onMandatoryUpgrade() {
        if (getAutoTestView()!=null){
            getAutoTestView().onMandatoryUpgrade();
        }
        activity.switchSubFragment(0, false);
    }

    @Override
    public void onErrorCallback(int code, @NotNull String message) {
        if (getAutoTestView()!=null){
            getAutoTestView().onErrorCallback(code, message);
        }
        if (code == 10596) {
            binding.tvScanTip.setText(getString(R.string.bt_bluetooth_close));
        }
    }


    private void showConnectionDialog() {
        if (mNotifyDialog == null) {
            mNotifyDialog = Jl_Dialog.builder()
                    .title(getString(R.string.tips))
                    .content(getString(R.string.bt_connecting))
                    .showProgressBar(true)
                    .width(0.8f)
                    .cancel(false)
                    .build();
        }
        if (mNotifyDialog != null && !mNotifyDialog.isShow()) {
            mNotifyDialog.show(activity.getSupportFragmentManager(), "connect_to_ble");
        }
    }

    private void dismissConnectionDialog() {
        if (mNotifyDialog != null && mNotifyDialog.isShow()) {
            mNotifyDialog.dismiss();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        if (mPresenter != null) {
            if (isVisibleToUser) {
                mPresenter.startScan();
            } else {
                mPresenter.stopScan();
            }
        }
    }

    private IDeviceContract.IDevScanView getAutoTestView() {
        TestTask testTask = AutoTestTaskManager.getInstance().currentTestTask;
        if (testTask instanceof IDeviceContract.IDevScanView) {
            return (IDeviceContract.IDevScanView) testTask;
        } else {
            return null;
        }
    }
}
