package com.jieli.otasdk_java.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ScreenUtils;
import com.jieli.component.utils.HandlerManager;
import com.jieli.jl_dialog.Jl_Dialog;
import com.jieli.otasdk_java.base.BaseActivity;
import com.jieli.otasdk_java.R;
import com.jieli.otasdk_java.util.OtaFileObserverHelper;

import org.jetbrains.annotations.Nullable;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

/**
 * @ClassName: WelcomeActivity
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/22 15:30
 */
@RuntimePermissions
public class WelcomeActivity extends BaseActivity {
    public static final int GPS_REQUEST_CODE = 1234;
    private Jl_Dialog notifyGpsDialog = null;


    @NeedsPermission({
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    })
    public void toMainActivity() {
        if (checkGpsProviderEnable(getApplicationContext())) {
            goToMainActivity();
        } else {
            showNotifyGPSDialog();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenUtils.setFullScreen(this);
        setContentView(R.layout.activity_welcome);
        if (!isTaskRoot()) {//是不是任务栈的第一个任务
            WelcomeActivityPermissionsDispatcher.toMainActivityWithPermissionCheck(this);
        } else {//显示welcome欢迎界面
            HandlerManager.getInstance().getMainHandler().postDelayed(
                    () -> {
                            WelcomeActivityPermissionsDispatcher.toMainActivityWithPermissionCheck(this);
                    },
                    1000
            );
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissNotifyGPSDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (GPS_REQUEST_CODE == requestCode) {
            goToMainActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        WelcomeActivityPermissionsDispatcher.onRequestPermissionsResult(this,requestCode,grantResults);
    }

    @OnShowRationale({
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE}
    )
    public void showRationaleForPermission(PermissionRequest request) {
        request.proceed();
    }

    @OnNeverAskAgain({
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    })
    public void onPermissionsNeverAskAgain() {
        //  Toast.makeText(this, "权限不再询问", Toast.LENGTH_SHORT).show()
        goToMainActivity();
    }

    @OnPermissionDenied({
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    })
    public void onPermissionsDenied() {
        goToMainActivity();
    }

    /**
     * 检查GPS位置功能是否使能
     *
     * @param context 上下文
     * @return 结果
     */
    private Boolean checkGpsProviderEnable(Context context) {
        if (context == null) return false;
        LocationManager locManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * 显示打开定位服务(gps)提示框
     */
    private void showNotifyGPSDialog() {
        if (isDestroyed() || isFinishing()) return;
        if (notifyGpsDialog == null) {
            notifyGpsDialog = new Jl_Dialog.Builder()
                    .title(getString(R.string.tips))
                    .content(getString(R.string.open_gpg_tip))
                    .cancel(false)
                    .left(getString(R.string.cancel))
                    .leftColor(getResources().getColor(R.color.gray_text_444444))
                    .right(getString(R.string.to_setting))
                    .rightColor(getResources().getColor(R.color.red_FF688C))
                    .rightClickListener((view, dialogFragment) -> {
                        dismissNotifyGPSDialog();
                        startActivityForResult(
                                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                GPS_REQUEST_CODE
                        );
                    }).leftClickListener((view, dialogFragment) -> {
                        dismissNotifyGPSDialog();
                        goToMainActivity();
                    })
                    .build();
        }
        if (this.notifyGpsDialog != null && !this.notifyGpsDialog.isShow()) {
            this.notifyGpsDialog.show(getSupportFragmentManager(), "notify_gps_dialog");
        }
    }

    private void dismissNotifyGPSDialog() {
        if (notifyGpsDialog != null) {
            if (notifyGpsDialog.isShow() && !isDestroyed()) {
                notifyGpsDialog.dismiss();
            }
            notifyGpsDialog = null;
        }
    }

    private void goToMainActivity() {
        OtaFileObserverHelper.getInstance().startObserver();
        if (isTaskRoot()) {//是不是任务栈的第一个任务
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
