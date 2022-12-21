package com.jieli.otasdk;

import android.app.Application;

import com.jieli.component.ActivityManager;
import com.jieli.component.utils.ToastUtil;
import com.jieli.jl_bt_ota.util.CommonUtil;
import com.jieli.jl_bt_ota.util.JL_Log;

public class MainApplication extends Application {
    private static MainApplication instance;
    private final boolean isDebug = BuildConfig.DEBUG;


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        JL_Log.setIsSaveLogFile(null, false);
        JL_Log.setLog(false);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ActivityManager.init(this);
        ToastUtil.init(this);
        CommonUtil.setMainContext(this);
        JL_Log.setLog(isDebug);
        JL_Log.setIsSaveLogFile(this, isDebug);
    }

    public static MainApplication getInstance() {
        return instance;
    }

}

