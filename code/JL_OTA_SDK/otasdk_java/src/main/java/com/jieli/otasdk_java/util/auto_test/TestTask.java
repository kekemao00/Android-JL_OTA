package com.jieli.otasdk_java.util.auto_test;

public abstract class TestTask {
    public TestTask(TestTaskFinishListener testTaskFinishListener) {
        finishListener = testTaskFinishListener;
    }

    protected TestTaskFinishListener finishListener;

    public abstract void start();
}
