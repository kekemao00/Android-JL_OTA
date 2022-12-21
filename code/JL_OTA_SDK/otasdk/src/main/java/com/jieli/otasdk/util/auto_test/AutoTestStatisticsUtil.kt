package com.jieli.otasdk.util.auto_test

/**
 *
 * @ClassName:      AutoTestStatisticsUtil
 * @Description:     java类作用描述
 * @Author:         ZhangHuanMing
 * @CreateDate:     2022/2/18 11:24
 */
class AutoTestStatisticsUtil {
    var testSum = 0;
    var currentCount = 0
    fun clear(): Unit {
        testSum = 0
        currentCount = 0
    }
}