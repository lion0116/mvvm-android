package com.mylive.live.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.SparseArray;

import com.mylive.live.arch.annotation.Service;
import com.mylive.live.base.BaseViewModel;
import com.mylive.live.model.LiveList;
import com.mylive.live.service.LiveListService;

/**
 * Create by zailongshi on 2019/6/22
 */
public class LiveListViewModel extends BaseViewModel {

    @Service
    private LiveListService liveListService;

    public LiveData<LiveList> getLiveList(boolean more, int size) {
        MutableLiveData<LiveList> finalLiveList = new MutableLiveData<>();
        liveListService.getLiveList(size)
                .dispose(this)
                .observe(finalLiveList::postValue);
        return finalLiveList;
    }
}
