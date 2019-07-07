package com.mylive.live.arch.feature;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

import com.mylive.live.arch.starter.ActivityStartProxy;
import com.mylive.live.arch.starter.Finisher;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Created by Developer Zailong Shi on 2018/12/21.
 */
public class ActivityStarter<T extends FragmentActivity>
        extends com.mylive.live.arch.starter.ActivityStarter<T>
        implements Starter {

    private Class<T> targetActivity;
    @SuppressWarnings("WeakerAccess")
    protected Intent intent;

    protected ActivityStarter() {
        intent = new Intent();
    }

    @Override
    public Finisher start(Feature feature) {
        Objects.requireNonNull(feature);
        if (feature.getContext() != null
                && !ActivityStartProxy.ifPrevent(feature.getLifecycleOwner())) {
            intent.setClass(feature.getContext(), getTargetActivity());
            if (feature.getLifecycleOwner() instanceof FragmentActivity) {
                ActivityStartProxy.startActivity(
                        (FragmentActivity) feature.getLifecycleOwner(),
                        intent);
            } else if (feature.getLifecycleOwner() instanceof Fragment) {
                ActivityStartProxy.startActivity(
                        (Fragment) feature.getLifecycleOwner(),
                        intent);
            }
        }
        return () -> {
            if (feature.getActivity() != null) {
                feature.getActivity().finish();
            }
        };
    }

    @Override
    public void startForResult(Feature feature, int requestCode) {
        Objects.requireNonNull(feature);
        if (feature.getContext() == null
                || ActivityStartProxy.ifPrevent(feature.getLifecycleOwner())) {
            return;
        }
        intent.setClass(feature.getContext(), getTargetActivity());
        if (feature.getLifecycleOwner() instanceof FragmentActivity) {
            ActivityStartProxy.startActivityForResult(
                    (FragmentActivity) feature.getLifecycleOwner(),
                    intent, requestCode);
        } else if (feature.getLifecycleOwner() instanceof Fragment) {
            ActivityStartProxy.startActivityForResult(
                    (Fragment) feature.getLifecycleOwner(),
                    intent, requestCode);
        }
    }

    private Class<T> getTargetActivity() {
        if (targetActivity == null) {
            Type type = getClass().getGenericSuperclass();
            if (type instanceof ParameterizedType) {
                //noinspection unchecked
                targetActivity = (Class<T>) ((ParameterizedType) type)
                        .getActualTypeArguments()[0];
            }
        }
        return targetActivity;
    }
}
