package com.jieli.otasdk_java.util.auto_test;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;

import com.jieli.jl_bt_ota.util.JL_Log;
import com.jieli.otasdk_java.tool.IDeviceContract;
import com.jieli.otasdk_java.tool.IOtaContract;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @ClassName: AutoTestTaskManager
 * @Description: java类作用描述
 * @Author: ZhangHuanMing
 * @CreateDate: 2022/2/21 14:14
 */
public class AutoTestTaskManager implements TestTaskFinishListener {
    public static int AUTO_TEST_OTA_COUNT = 10;
    public IOtaContract.IOtaPresenter otaPresenter = null;
    public IDeviceContract.IDevScanPresenter devScanPresenter = null;
    public TestTask currentTestTask = null;
    private static volatile AutoTestTaskManager instance;
    private String TAG = getClass().getSimpleName();
    private Queue<TestTask> mTestTasks = new LinkedBlockingQueue<TestTask>();
    private Handler uiHandler = new Handler(
            Looper.getMainLooper(), msg -> false
    );

    public static AutoTestTaskManager getInstance() {
        if (instance == null) {
            synchronized (AutoTestTaskManager.class) {
                if (instance == null) {
                    instance = new AutoTestTaskManager();
                }
            }
        }
        return instance;
    }

    public boolean isAutoTesting() {
        return currentTestTask != null || mTestTasks.size() > 0;
    }

    //todo 统计升级数量，放在外部去做
    public void startAutoTest(BluetoothDevice device, String filePath) {
        for (int i = 0; i < 10; i++) {
            addTask(new DevScanTestTask(device, devScanPresenter, this));
            addTask(new OTATestTask(filePath, otaPresenter, this));
        }
    }


    @Override
    public void onFinish() {
        currentTestTask = null;
        uiHandler.post(() -> {
            TestTask testTask = mTestTasks.poll();
            if (testTask != null) {
                startTask();
            }
        });
    }

    /* ---------------------------------------------------------------- *
     * TODO://私有方法
     * ---------------------------------------------------------------- */
    private void addTask(TestTask testTask) {
        mTestTasks.add(testTask);
        if (mTestTasks.size() == 1) {
            startTask();
        }
    }

    private void startTask() {
        TestTask testTask = mTestTasks.peek();
        if (testTask != null) {
            JL_Log.i(
                    TAG,
                    "-------------启动SyncTask------------>" + testTask.getClass().getSimpleName()
            );
            testTask.start();
        }
        currentTestTask = testTask;
    }
}
