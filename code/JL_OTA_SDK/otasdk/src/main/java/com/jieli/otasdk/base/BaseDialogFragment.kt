package com.jieli.otasdk.base

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

/**
 ************************************
 *@Author revolve
 *创建时间：2019/7/31  15:44
 *用途
 ************************************
 */
open class BaseDialogFragment : DialogFragment() {


    fun show(manager: FragmentManager?) {
        manager?.let { super.show(it, javaClass.canonicalName) }
    }

}