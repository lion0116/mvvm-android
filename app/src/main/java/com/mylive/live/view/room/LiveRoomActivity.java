package com.mylive.live.view.room;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.mylive.live.R;
import com.mylive.live.core.base.BaseActivity;
import com.mylive.live.databinding.ActivityLiveRoomBinding;
import com.mylive.live.event.TestEvent;

/**
 * Created by Developer Zailong Shi on 2019-06-21.
 */
public class LiveRoomActivity extends BaseActivity {

    private ActivityLiveRoomBinding binding;
    private int msg = 100;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_live_room);
        binding.btnSend.setOnClickListener(v -> {
            publish(msg++ % 2 == 0 ? "hello" : msg);
            if (msg % 3 == 0) {
                publish(new TestEvent("test"));
            }
        });
    }
}
