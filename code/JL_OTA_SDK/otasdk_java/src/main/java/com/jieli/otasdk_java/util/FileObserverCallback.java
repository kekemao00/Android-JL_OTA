package com.jieli.otasdk_java.util;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc  文件监听器
 * @since 2021/5/31
 */
public interface FileObserverCallback {

    void onChange(int event, String path);
}
