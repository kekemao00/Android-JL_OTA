package com.jieli.otasdk.util

import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.jieli.jl_bt_ota.util.JL_Log
import com.jieli.otasdk.tool.IDeviceContract
import com.jieli.otasdk.tool.IOtaContract
import com.jieli.otasdk.util.auto_test.DevScanTestTask
import com.jieli.otasdk.util.auto_test.OTATestTask
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 *
 * @ClassName:      AutoTestHandler
 * @Description:     自动化测试任务管理
 * @Author:         ZhangHuanMing
 * @CreateDate:     2022/2/17 19:59
 */
class AutoTestTaskManager : TestTaskFinishListener {
    companion object {
        val AUTO_TEST_OTA_COUNT = 10
        val instance: AutoTestTaskManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            AutoTestTaskManager()
        }
    }

    var otaPresenter: IOtaContract.IOtaPresenter? = null
    var devScanPresenter: IDeviceContract.IDevScanPresenter? = null
    var currentTestTask: TestTask? = null
    private val TAG = this.javaClass.simpleName
    private val mTestTasks: Queue<TestTask> = LinkedBlockingQueue<TestTask>()

    private val uiHandler = Handler(
        Looper.getMainLooper()
    ) { msg: Message ->
        true
    }

    fun isAutoTesting(): Boolean {
        return currentTestTask != null || mTestTasks.size > 0
    }

    //todo 统计升级数量，放在外部去做
    fun startAutoTest(device: BluetoothDevice, filePath: String): Unit {
        for (i in 1..AUTO_TEST_OTA_COUNT) {
            addTask(DevScanTestTask(device, devScanPresenter, this))
            addTask(OTATestTask(filePath, otaPresenter, this))
        }
    }

    fun addTask(testTask: TestTask): Unit {
        mTestTasks.add(testTask)
        if (mTestTasks.size == 1) {
            startTask()
        }
    }

    override fun onFinish() {
        currentTestTask = null
        uiHandler.post {
            val testTask = mTestTasks.poll()
            if (testTask != null) {
                startTask()
            }
        }
    }

    /* ---------------------------------------------------------------- *
    * TODO://私有方法
    * ---------------------------------------------------------------- */
    private fun startTask() {
        val testTask = mTestTasks.peek()
        testTask?.let {
            JL_Log.i(
                TAG,
                "-------------启动SyncTask------------>" + testTask.javaClass.getSimpleName()
            )
            it.start()
        }
        currentTestTask = testTask
    }
}

abstract class TestTask(protected var finishListener: TestTaskFinishListener) {
    abstract fun start()
}

interface TestTaskFinishListener {
    fun onFinish()
}
