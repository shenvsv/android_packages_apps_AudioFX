package com.cyngn.audiofx.eq;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;

public class EqBarView extends FrameLayout implements MasterConfigControl.EqUpdatedCallback,
        ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {

    private static final String TAG = EqBarView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    MasterConfigControl mConfig;

    private float mNormalWidth;
    private float mParentHeight;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX;
    private float mPosY = -1;
    private boolean mUserInteracting;
    private int mParentTop;
    private Integer mIndex;
    private float mInitialLevel;

    private ValueAnimator mAnimator;
    private boolean mInitialAnimation = true;


    public EqBarView(Context context) {
        super(context);
        init();
    }

    public EqBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mConfig = MasterConfigControl.getInstance(mContext);
        mNormalWidth = getResources().getDimension(R.dimen.eq_bar_width);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mConfig.addEqStateChangeCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mConfig.removeEqStateChangeCallback(this);
    }

    public void setInitialAnimation(boolean forceAnimate) {
        mInitialAnimation = forceAnimate;
    }

    private EqContainerView.EqBandInfo getInfo() {
        return (EqContainerView.EqBandInfo) getTag();
    }

    public void setParentHeight(float h, int top, int textOffset) {

        final float selectedBoxHeight = getResources().getDimension(R.dimen.eq_selected_box_height);
        final float paddingTop = getResources().getDimension(R.dimen.eq_top_padding);

        mParentHeight = h - selectedBoxHeight - textOffset - paddingTop;
        mParentTop = top;
    }

    void updateHeight() {
        if (DEBUG) Log.d(TAG, "updateHeight() mInitialAnimation=" + mInitialAnimation);

        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }

        if (getInfo() != null) {
            float level = mConfig.getLevel(getIndex());
            float yProjection = 1 - mConfig.projectY(level);
            float height = (yProjection * (mParentHeight));
            mPosY = height;

            if (DEBUG) {
                Log.d(TAG, getIndex() + "level: " + level + ", yProjection: "
                        + yProjection + ", mPosY: " + mPosY);
            }

            if (mInitialAnimation) {
                mInitialAnimation = false;
                mAnimator = ValueAnimator.ofFloat(getLayoutParams().height, mPosY);
                mAnimator.addUpdateListener(this);
                mAnimator.addListener(this);
                mAnimator.setDuration(800);
                mAnimator.setInterpolator(new LinearInterpolator());
                mAnimator.start();
            } else {
                updateHeight((int) mPosY);
            }
        } else {
            if (DEBUG) Log.d(TAG, "could not updateHeight()");
        }
    }

    private int getIndex() {
        if (mIndex == null) {
            mIndex = (getInfo()).mIndex;
        }
        return mIndex;
    }

    public boolean isUserInteracting() {
        return mUserInteracting;
    }

    /* package */ void startInteraction(float x, float y) {

        mLastTouchX = x;
        mLastTouchY = y;
        mUserInteracting = true;

        if (DEBUG) Log.d(TAG, "initial level: " + mInitialLevel);
        mInitialLevel = (1 - (mPosY / mParentHeight)) * (mConfig.getMinDB() - mConfig.getMaxDB())
                - mConfig.getMinDB();

        updateWidth((int) (mNormalWidth * 2));
    }

    /* package */ void endInteraction() {
        mUserInteracting = false;

        updateWidth((int)mNormalWidth);
    }

    private void updateHeight(int h) {
        if (isInLayout()) {
            return;
        }

        final ViewGroup.LayoutParams params = getLayoutParams();
        params.height = h;
        setLayoutParams(params);

        if (getParent() instanceof View) {
            ((View) getParent()).postInvalidateOnAnimation();
        }
    }

    private void updateWidth(int w) {
        final ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        setLayoutParams(params);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mConfig.isEqualizerLocked()) {
            return false;
        }

        final float x = event.getRawX();
        final float y = event.getRawY() - mParentTop;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startInteraction(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                mPosX += dx;
                mPosY -= dy;

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;

                int wy = (int) mParentHeight;
                float level = (1 - (mPosY / wy)) * (mConfig.getMinDB() - mConfig.getMaxDB())
                        - mConfig.getMinDB();

                if (DEBUG) Log.d(TAG, "new level: " + level);
                if (level < mConfig.getMinDB()) {
                    level = mConfig.getMinDB();
                } else if (level > mConfig.getMaxDB()) {
                    level = mConfig.getMaxDB();
                }

                if (mInitialLevel != level) {
                    mConfig.setLevel(getInfo().mIndex, level, false);
                } else {
                    updateHeight();
                }

                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                endInteraction();
                break;

        }

        return true;
    }

    public float getPosY() {
        return mPosY;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (getInfo().mIndex != band) {
            return;
        }

        updateHeight();
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {

    }

    @Override
    public void onPresetsChanged() {

    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {

    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        int val = (int) ((Float) animation.getAnimatedValue() + 0.5f);
        updateHeight(val);
    }

    @Override
    public void onAnimationStart(Animator animation) {
//        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public void onAnimationEnd(Animator animation) {
//        setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }
}
