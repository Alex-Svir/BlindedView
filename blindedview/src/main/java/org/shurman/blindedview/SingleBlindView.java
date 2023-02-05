package org.shurman.blindedview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

public class SingleBlindView extends AbsBlindedView {
    private static final int TARGET_BUTTON_L = 1;
    private static final int TARGET_BLIND = 2;
    private static final int TARGET_BUTTON_R = 4;
    private static final int TARGET_NONE = 0;
    private static final int TARGET_MASK = 0x7;

    private static final int STATE_SLIDE = 0x10;
    private static final int STATE_CLICK = 0x20;
    private static final int STATE_NOTHING = 0;
    private static final int STATE_MASK = 0x30;
    //---------------------------------------------------------------------------
//  attrs
    private float mLeftBlindAxisSentinelRelative;
    private float mRightBlindAxisSentinelRelative;
    private float mLatchReleaseRelative;
    //  moving blind variables
    private float mBlindAxisPositionRelative;
    private int mBlindsFlags;
//--------------------------------------------------------------------------

    public SingleBlindView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mBlindAxisPositionRelative = 0.5f;
        mBlindsFlags = 0;

        super.setOnClickListener(v -> {
            switch (mBlindsFlags & TARGET_MASK) {
                case TARGET_BUTTON_L:
                    if (null != mOnInteractionListener)
                        mOnInteractionListener.onBlindedItemClick(this, true);
                    break;
                case TARGET_BUTTON_R:
                    if (null != mOnInteractionListener)
                        mOnInteractionListener.onBlindedItemClick(this, false);
                    break;
                case TARGET_BLIND:
                    shut(0.5f);
                    if (null != mOnInteractionListener)
                        mOnInteractionListener.onBlindClick(this);
                    break;
                default:
                    throw new IllegalStateException("Illegal state at performClick()");
            }});
    }

    @Override
    public void setBlindWidth(float blindWidth) {
        super.setBlindWidth(blindWidth);
        mLeftBlindAxisSentinelRelative = 0.5f - blindWidth;
        mRightBlindAxisSentinelRelative = 0.5f + blindWidth;
        mLatchReleaseRelative = blindWidth * getLatchRelease();
    }

    @Override
    public void setLatchRelease(float latchRelease) {
        super.setLatchRelease(latchRelease);
        mLatchReleaseRelative = getBlindWidth() * latchRelease;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDrawableLeft != null) mDrawableLeft.draw(canvas);
        if (mDrawableRight != null) mDrawableRight.draw(canvas);

        String text = mText.toString();

        float startText;
        float blindLeft, blindRight;
        if (blindClosed()) {
            startText = mTextOffsetFromLeft;
            blindLeft = 0;
            blindRight = mScaledViewWidth;
        } else if (leftSideOpen()) {
            blindLeft = (mBlindAxisPositionRelative - 0.5f) * mScaledViewWidth;
            startText = blindLeft + mTextOffsetFromLeft;
            blindRight = blindLeft + mScaledViewWidth;
        } else if (rightSideOpen()) {
            blindRight = (mBlindAxisPositionRelative + 0.5f) * mScaledViewWidth;
            startText = blindRight - mTextOffsetFromRight;
            blindLeft = blindRight - mScaledViewWidth;
        }
        else throw new IllegalStateException("Illegal BlindedView state at onDraw");
        mBlindBack.setBounds((int)blindLeft, 0, (int)blindRight, mScaledViewHeight);
        mBlindBack.draw(canvas);
        canvas.drawText(text, startText, mTextBaseline, mTextPaint);
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {}

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                rememberBlindsDownParams(x, y);
                break;
            case MotionEvent.ACTION_UP:
                finalizeTouchInteraction(x, y, true);
                break;
            case MotionEvent.ACTION_CANCEL:
                finalizeTouchInteraction(x, y, false);
                break;
            case MotionEvent.ACTION_MOVE:
                if (outOfViewBounds(x, y)) {
                    finalizeTouchInteraction(x, y, false);
                    return false;
                }
                switch (mBlindsFlags & STATE_MASK) {
                    case STATE_CLICK:
                        int target = mBlindsFlags & TARGET_MASK;
                        if (target == TARGET_BUTTON_L || target == TARGET_BUTTON_R) break;
                        assert target == TARGET_BLIND : "Illegal target at onTouch::MOVE::CLICK";
                        if (underConversionThreshold(x, y)) break;
                        mBlindsFlags = (mBlindsFlags & ~STATE_MASK) | STATE_SLIDE;
                    case STATE_SLIDE:
                        if (slide(x))
                            invalidate();
                        break;
                    case STATE_NOTHING:
                    default:
                }
                break;
            default:
        }
        return true;
    }

    private void rememberBlindsDownParams(float x, float y) {
        mRefPoint.set(x, y);
        float relativeX = x / mScaledViewWidth;
        if (leftSideOpen()) {
            if (relativeX < mBlindAxisPositionRelative - 0.5f) {    //  to the left of blind
                if (withinIcon(x, y, mDrawableLeft)) {
                    mBlindsFlags = TARGET_BUTTON_L | STATE_CLICK;
                } else {
                    mBlindsFlags = TARGET_NONE | STATE_NOTHING;
                }
            } else {    //  on open blind
                mBlindsFlags = TARGET_BLIND | STATE_CLICK;
            }
        } else if (rightSideOpen()) {
            if (relativeX > mBlindAxisPositionRelative + 0.5f) {    //  to the right of blind
                if (withinIcon(x, y, mDrawableRight)) {
                    mBlindsFlags = TARGET_BUTTON_R | STATE_CLICK;
                } else {
                    mBlindsFlags = TARGET_NONE | STATE_NOTHING;
                }
            } else {    //  on open blind
                mBlindsFlags = TARGET_BLIND | STATE_CLICK;
            }
        } else {    //  blind closed
            mBlindsFlags = TARGET_BLIND | STATE_SLIDE;
        }
    }

    private boolean slide(float x) {
        assert 0 <= mRefPoint.x && mRefPoint.x <= mScaledViewWidth : "slide() assertion failed";
        if (x < 0) x = 0;
        else if (x > mScaledViewWidth) x = mScaledViewWidth;
        if (x == mRefPoint.x) return false;

        float oldPosition = mBlindAxisPositionRelative;
        mBlindAxisPositionRelative += (x - mRefPoint.x) / mScaledViewWidth;
        mRefPoint.x = x;
        if (mBlindAxisPositionRelative < mLeftBlindAxisSentinelRelative)
            mBlindAxisPositionRelative = mLeftBlindAxisSentinelRelative;
        else if (mBlindAxisPositionRelative > mRightBlindAxisSentinelRelative)
            mBlindAxisPositionRelative = mRightBlindAxisSentinelRelative;
        return oldPosition != mBlindAxisPositionRelative;
    }

    private void finalizeTouchInteraction(float x, float y, boolean correctly) {
        int target = mBlindsFlags & TARGET_MASK;
        switch (mBlindsFlags & STATE_MASK) {
            case STATE_CLICK:
                if (!correctly)
                    break;
                if (target == TARGET_BUTTON_L) {
                    if (withinIcon(x, y, mDrawableLeft))
                        performClick();
                    break;
                }
                if (target == TARGET_BUTTON_R) {
                    if (withinIcon(x, y, mDrawableRight))
                        performClick();
                    break;
                }
                if (target == TARGET_BLIND) {
                    performClick();
                    break;
                }
                throw new IllegalStateException("Illegal BlindView state on finalizeTouch::Click");
            case STATE_SLIDE:
                assert target == TARGET_BLIND : "finalizeTouch::SLIDE not the blind";
                if (leftSideOpen()) {
                    float latchPos = mRightBlindAxisSentinelRelative - mLatchReleaseRelative;
                    shut(mBlindAxisPositionRelative >= latchPos ? latchPos : 0.5f);
                } else if (rightSideOpen()) {
                    float latchPos = mLeftBlindAxisSentinelRelative + mLatchReleaseRelative;
                    shut(mBlindAxisPositionRelative <= latchPos ? latchPos : 0.5f);
                }
                if (null != mOnInteractionListener)
                    mOnInteractionListener.onBlindSlideCompleted(this);
                break;
            case STATE_NOTHING:
                break;
            default:
                throw new IllegalStateException("Illegal BlindView state on UP/CANCEL event");
        }
        mBlindsFlags = 0;
    }

    @Override
    public void shut() { shut(0.5f); }

    private void shut(float position) {
        mBlindAxisPositionRelative = position;
        invalidate();
    }

    private boolean blindClosed() { return mBlindAxisPositionRelative == 0.5f; }
    private boolean leftSideOpen() { return mBlindAxisPositionRelative > 0.5f; }
    private boolean rightSideOpen() { return mBlindAxisPositionRelative < 0.5f; }
}
