package com.jieli.otasdk_java.base;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

/**
 * @ClassName: BaseDialogFragment
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:33
 */
public class BaseDialogFragment extends DialogFragment {
    public void show(FragmentManager manager) {
        if (manager != null) {
            show(manager, getClass().getCanonicalName());
        }
    }
}