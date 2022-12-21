package com.jieli.otasdk.base

import android.os.Bundle
import com.blankj.utilcode.util.BarUtils
import com.jieli.component.base.Jl_BaseActivity

/**
 *  create Data:2019-07-24
 *  create by:chensenhua
 *
 **/
open class BaseActivity : Jl_BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BarUtils.setStatusBarLightMode(this,true)

    }


}