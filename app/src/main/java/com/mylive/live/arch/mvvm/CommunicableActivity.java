package com.mylive.live.arch.mvvm;

import android.annotation.SuppressLint;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;

import com.mylive.live.arch.exception.ProhibitedException;
import com.mylive.live.arch.subscriber.PublisherAndSchedulerProxy;
import com.mylive.live.arch.subscriber.Scheduler;
import com.mylive.live.arch.subscriber.SubscribesScheduler;

/**
 * Created by Developer Zailong Shi on 2019-06-19.
 */
@SuppressLint("Registered")
public class CommunicableActivity extends FragmentActivity implements LifecycleObserver {

    private static class SchedulerHolder {
        private static final Scheduler SCHEDULER = new SubscribesScheduler();
    }

    private Scheduler schedulerAndPublisherProxy = new PublisherAndSchedulerProxy(
            CommunicableActivity.SchedulerHolder.SCHEDULER
    );

    {
        getLifecycle().addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private void onSubscribe() {
        onSubscribe(schedulerAndPublisherProxy);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onUnsubscribe() {
        schedulerAndPublisherProxy.unsubscribeAll();
    }

    protected void onSubscribe(Scheduler scheduler) {

    }

    protected <T> void publish(T event) {
        schedulerAndPublisherProxy.publish(event);
    }

    @Deprecated
    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }

    @Deprecated
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }
}
