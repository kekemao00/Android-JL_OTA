package com.jieli.otasdk_java.base;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.BarUtils;
import com.jieli.component.base.Jl_BaseActivity;

/**
 * @ClassName: BaseActivity
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:32
 */
public class BaseActivity extends Jl_BaseActivity {
    @Override
    public void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        BarUtils.setStatusBarLightMode(this, true);
    }
}
