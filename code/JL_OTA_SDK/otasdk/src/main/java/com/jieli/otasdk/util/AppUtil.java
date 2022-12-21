package com.jieli.otasdk.util;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import java.util.Date;

/**
 * @author zqjasonZhong
 * @since 2020/7/16
 */
public class AppUtil {


    /**
     * 是否具有读取位置权限
     *
     * @param context 上下文
     * @return 结果
     */
    public static boolean isHasLocationPermission(Context context) {
        return isHasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /**
     * 是否具有读写存储器权限
     *
     * @param context 上下文
     * @return 结果
     */
    public static boolean isHasStoragePermission(Context context) {
        return isHasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) && isHasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    /**
     * 是否具有指定权限
     *
     * @param context    上下文
     * @param permission 权限
     *                   <p>参考{@link Manifest.permission}</p>
     * @return 结果
     */
    public static boolean isHasPermission(Context context, String permission) {
        return context != null && ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static long lastClickTime = 0;
    private final static long DOUBLE_CLICK_INTERVAL = 2000; //2 s

    public static boolean isFastDoubleClick() {
        return isFastDoubleClick(DOUBLE_CLICK_INTERVAL);
    }

    public static boolean isFastDoubleClick(long interval) {
        boolean isDoubleClick = false;
        long currentTime = new Date().getTime();
        if (currentTime - lastClickTime <= interval) {
            isDoubleClick = true;
        }
        lastClickTime = currentTime;
        return isDoubleClick;
    }

    public static boolean enableBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (null == bluetoothAdapter) return false;
        boolean ret = bluetoothAdapter.isEnabled();
        if (!ret) {
            ret = bluetoothAdapter.enable();
        }
        return ret;
    }
}
