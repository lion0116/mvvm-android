package com.mylive.live.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Developer Zailong Shi on 2018/10/17.
 */
public class SpecialAdjustResizeLayout extends FrameLayout {

    private boolean autoResize = true;
    private int offsetUp;
    private EditText focusedEditText;
    private Map<EditText, View> adjustableViews;
    private OnChangedListener onChangedListener;

    public SpecialAdjustResizeLayout(@NonNull Context context) {
        this(context, null);
    }

    public SpecialAdjustResizeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpecialAdjustResizeLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                     int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AndroidBug5497Workaround.assistActivity(this);
    }

    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (hasFocusedEditText()) {
            return insets;
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (hasFocusedEditText() && h > 0 && oldh > h) {
            offsetUp = oldh - h;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (hasFocusedEditText()) {
            if (changed && offsetUp > 0) {
                if (autoResize) {
                    layoutChild();
                }
                notifyKeyboardStateChanged(offsetUp);
                offsetUp = 0;
            }
            return;
        }
        if (autoResize || focusedEditText == null) {
            super.onLayout(changed, left, top, right, bottom);
        }
        notifyKeyboardStateChanged(0);
        disposeFocusedEditText(this);
    }

    private boolean isImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int systemUiVisibility = ((Activity) getContext()).getWindow()
                    .getDecorView().getSystemUiVisibility();
            return !getFitsSystemWindows()
                    && (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                    == View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
        return false;
    }

    private void layoutChild() {
        if (focusedEditText == null || adjustableViews == null
                || adjustableViews.size() == 0) {
            return;
        }
        View adjustableView = adjustableViews.get(focusedEditText);
        if (adjustableView != null) {
            adjustableView.layout(adjustableView.getLeft(),
                    adjustableView.getTop() - offsetUp,
                    adjustableView.getRight(),
                    adjustableView.getBottom() - offsetUp);
        }
    }

    private void notifyKeyboardStateChanged(int height) {
        if (onChangedListener != null && focusedEditText != null) {
            View adjustableView = adjustableViews.get(focusedEditText);
            onChangedListener.onChanged(adjustableView, height);
        }
    }

    private OnTouchListener onTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v instanceof EditText) {
                if (focusedEditText == null) {
                    focusedEditText = (EditText) v;
                }
            }
            if ((event.getAction() & MotionEvent.ACTION_MASK)
                    == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        }
    };

    public void addAdjustableViews(View... views) {
        if (views != null && views.length > 0) {
            if (adjustableViews == null) {
                adjustableViews = new HashMap<>();
            }
            for (int i = 0; i < views.length; i++) {
                if (views[i] == null) {
                    throw new IllegalArgumentException("Element index " + i + "in views is null");
                }
                findEditTextView(views[i], views[i]);
            }
        }
    }

    public void addAdjustableViews(@IdRes int... viewIds) {
        if (viewIds != null && viewIds.length > 0) {
            View[] views = new View[viewIds.length];
            for (int i = 0; i < viewIds.length; i++) {
                views[i] = findViewById(viewIds[i]);
                if (views[i] == null) {
                    throw new IllegalArgumentException("Not found the view by id " + viewIds[i]);
                }
            }
            addAdjustableViews(views);
        }
    }

    private void findEditTextView(View rootNode, View currentNode) {
        if (currentNode instanceof EditText) {
            if (!adjustableViews.containsKey(currentNode)) {
                adjustableViews.put((EditText) currentNode, rootNode);
                currentNode.setOnTouchListener(onTouchListener);
            }
        } else if (currentNode instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) currentNode).getChildCount(); i++) {
                View child = ((ViewGroup) currentNode).getChildAt(i);
                findEditTextView(rootNode, child);
            }
        }
    }

    private boolean hasAdjustableViews() {
        return adjustableViews != null
                && adjustableViews.size() > 0;
    }

    private boolean hasFocusedEditText() {
        return focusedEditText != null;
    }

    private void disposeFocusedEditText(Object disposer) {
        if (!isImmersiveMode() && disposer instanceof SpecialAdjustResizeLayout) {
            focusedEditText = null;
        } else if (isImmersiveMode() && disposer instanceof AndroidBug5497Workaround) {
            focusedEditText = null;
        }
    }

    public void setOnChangedListener(OnChangedListener onChangedListener) {
        this.onChangedListener = onChangedListener;
    }

    public void setAutoResize(boolean autoResize) {
        this.autoResize = autoResize;
    }

    public boolean isKeyboardVisible() {
        return getLayoutParams().height != ViewGroup.LayoutParams.MATCH_PARENT;
    }

    public interface OnChangedListener {
        void onChanged(View focusedView, int height);
    }

    private static class AndroidBug5497Workaround {

        // For more information, see https://code.google.com/p/android/issues/detail?id=5497
        // To use this class, simply invoke assistActivity() on an Activity that already has its content view set.

        private static void assistActivity(SpecialAdjustResizeLayout childOfContent) {
            new AndroidBug5497Workaround(childOfContent);
        }

        private SpecialAdjustResizeLayout mChildOfContent;

        private AndroidBug5497Workaround(SpecialAdjustResizeLayout childOfContent) {
            mChildOfContent = childOfContent;
            mChildOfContent.getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            this::possiblyResizeChildOfContent);
        }

        private void possiblyResizeChildOfContent() {
            if (isImmersiveMode() && hasFocusedEditText()) {
                ViewGroup.LayoutParams params = mChildOfContent.getLayoutParams();
                int usableHeightNow = computeUsableHeight();
                int currentHeight = mChildOfContent.getHeight();
                if (currentHeight <= 0
                        || usableHeightNow == currentHeight) {
                    return;
                }
                if (usableHeightNow < currentHeight) {
                    // keyboard probably just became visible
                    params.height = usableHeightNow;
                } else {
                    // keyboard probably just became hidden
                    if (params.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        return;
                    }
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    disposeFocusedEditText(this);
                }
                mChildOfContent.setLayoutParams(params);
            }
        }

        private int computeUsableHeight() {
            Rect r = new Rect();
            mChildOfContent.getWindowVisibleDisplayFrame(r);
            return r.bottom;
        }

        private boolean isImmersiveMode() {
            return mChildOfContent.isImmersiveMode();
        }

        private boolean hasFocusedEditText() {
            return mChildOfContent.hasFocusedEditText();
        }

        private void disposeFocusedEditText(Object disposer) {
            mChildOfContent.disposeFocusedEditText(disposer);
        }
    }
}
