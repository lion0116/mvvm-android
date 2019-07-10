package com.mylive.live.view.main;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.OnLifecycleEvent;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.mylive.live.R;
import com.mylive.live.arch.annotation.FieldMap;
import com.mylive.live.arch.feature.FeaturesActivity;
import com.mylive.live.arch.feature.FeaturesFragment;
import com.mylive.live.base.BaseFeature;
import com.mylive.live.component.ScrollEvent;
import com.mylive.live.databinding.ActivityMainBinding;
import com.mylive.live.view.home.HomeScrollEvent;

/**
 * Create by zailongshi on 2019/7/8
 */
public class BottomBarFeature extends BaseFeature {

    @FieldMap("binding")
    private ActivityMainBinding binding;
    private ScrollEvent.Observer scrollEventObserver;

    public BottomBarFeature(FeaturesActivity activity) {
        super(activity);
    }

    public BottomBarFeature(FeaturesFragment fragment) {
        super(fragment);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private void onCreate() {
        HomeScrollEvent.getInstance().registerObserver(
                scrollEventObserver = direction -> {
                    int parentHeight = binding.getRoot().getHeight();
                    int bottomBarHeight = binding.btnJump.getHeight();
                    int startY = direction > 0 ?
                            parentHeight - bottomBarHeight
                            : parentHeight;
                    int endY = direction > 0 ? parentHeight
                            : parentHeight - bottomBarHeight;
                    Runnable resetRecyclerViewLayoutParams = () -> {
                        RecyclerView recyclerView
                                = binding.getRoot().findViewById(R.id.recycler_view);
                        ViewGroup.MarginLayoutParams params
                                = (ViewGroup.MarginLayoutParams) recyclerView.getLayoutParams();
                        params.bottomMargin = direction > 0 ? 0 : bottomBarHeight;
                        recyclerView.setLayoutParams(params);
                    };
                    if (direction > 0) {
                        resetRecyclerViewLayoutParams.run();
                    }
                    ValueAnimator valueAnimator = ValueAnimator.ofInt(startY, endY);
                    valueAnimator.addUpdateListener(animation -> {
                        binding.btnJump.setY((int) animation.getAnimatedValue());
                    });
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            HomeScrollEvent.getInstance().onFeedBack(direction);
                            if (direction < 0) {
                                resetRecyclerViewLayoutParams.run();
                            }
                        }
                    });
                    valueAnimator.setInterpolator(new DecelerateInterpolator());
                    valueAnimator.setDuration(350);
                    valueAnimator.start();
                }
        );
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy() {
        HomeScrollEvent.getInstance().unregisterObserver(scrollEventObserver);
    }
}