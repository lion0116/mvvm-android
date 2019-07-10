package com.mylive.live.base;

import android.content.Intent;
import android.os.Bundle;

import com.mylive.live.exception.ProhibitedException;
import com.mylive.live.arch.mvvm.CommunicableFragment;

/**
 * Created by Developer Zailong Shi on 2019-06-28.
 */
public class BaseFragment extends CommunicableFragment {

    @Deprecated
    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }

    @Deprecated
    @Override
    public void startActivity(Intent intent, Bundle options) {
        super.startActivity(intent, options);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }

    @Deprecated
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }

    @Deprecated
    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        super.startActivityForResult(intent, requestCode, options);
        throw new ProhibitedException("Please start activity by extends ActivityStarter class.");
    }
}