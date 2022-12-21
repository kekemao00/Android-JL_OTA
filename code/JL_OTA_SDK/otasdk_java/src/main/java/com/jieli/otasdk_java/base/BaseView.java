package com.jieli.otasdk_java.base;

/**
 * @ClassName: BaseView
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2021/12/23 9:40
 */
public interface BaseView<T extends BasePresenter> {

    public void setPresenter(T presenter);
}
