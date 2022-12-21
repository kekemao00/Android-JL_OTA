package com.jieli.otasdk.util;

import android.os.Handler;
import android.os.Looper;

import com.jieli.jl_bt_ota.util.FileUtil;
import com.jieli.otasdk.MainApplication;

import java.util.ArrayList;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc  文件监听辅助类
 * @since 2021/5/31
 */
public class OtaFileObserverHelper {

    private volatile static OtaFileObserverHelper instance;
    private final OtaFileObserver mOtaFileObserver;
    private boolean isWatching;
    private final String watchPath;

    private final ArrayList<FileObserverCallback> mFileObserverCallbacks = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());


    private OtaFileObserverHelper() {
        watchPath = FileUtil.splicingFilePath(MainApplication.getInstance(), MainApplication.getInstance().getPackageName(), JL_Constant.DIR_UPGRADE, null, null);
        mOtaFileObserver = new OtaFileObserver(watchPath);
        mOtaFileObserver.setFileObserverCallback((event, path) -> mHandler.post(() -> {
            if (!mFileObserverCallbacks.isEmpty()) {
                for (FileObserverCallback callback : new ArrayList<>(mFileObserverCallbacks)) {
                    callback.onChange(event, path);
                }
            }
        }));
    }

    public static OtaFileObserverHelper getInstance() {
        if (null == instance) {
            synchronized (OtaFileObserverHelper.class) {
                if (null == instance) {
                    instance = new OtaFileObserverHelper();
                }
            }
        }
        return instance;
    }

    public void registerFileObserverCallback(FileObserverCallback callback) {
        if (callback != null && !mFileObserverCallbacks.contains(callback)) {
            mFileObserverCallbacks.add(callback);
        }
    }

    public void unregisterFileObserverCallback(FileObserverCallback callback) {
        if (callback != null && !mFileObserverCallbacks.isEmpty()) {
            mFileObserverCallbacks.remove(callback);
        }
    }

    public boolean isWatching() {
        return isWatching;
    }

    public String getWatchPath() {
        return watchPath;
    }

    public void startObserver() {
        mOtaFileObserver.startWatching();
        isWatching = true;
    }

    public void stopObserver() {
        mOtaFileObserver.stopWatching();
        isWatching = false;
    }

    public void destroy() {
        stopObserver();
        mOtaFileObserver.setFileObserverCallback(null);
        mFileObserverCallbacks.clear();
        instance = null;
    }


}
