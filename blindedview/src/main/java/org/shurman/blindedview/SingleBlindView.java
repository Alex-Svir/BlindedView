package org.shurman.blindedview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
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
    private final Blind mBlind;
    private float mBlindAxisPositionRelative;
    private int mBlindsFlags;
//--------------------------------------------------------------------------

    public SingleBlindView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mBlindAxisPositionRelative = 0.5f;
        mBlindsFlags = 0;
        mBlind = new Blind();

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
    public void setText(CharSequence text) {
        super.setText(text);
        mBlind.setText();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getBackground() == null)
            canvas.drawColor(Color.YELLOW);

        if (mDrawableLeft != null) mDrawableLeft.draw(canvas);
        if (mDrawableRight != null) mDrawableRight.draw(canvas);

        mBlind.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        measureIcon(mDrawableLeft, true);
        measureIcon(mDrawableRight, false);

        mBlind.measure();
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
                //if (mOnInteractionListener != null) mOnInteractionListener.onStartInteraction();
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

    private void measureIcon(Drawable icon, boolean left) {     //    TODO remeasure with paddings and allowed frame size
        if (icon == null) return;
        int iw = icon.getIntrinsicWidth();
        int ih = icon.getIntrinsicHeight();
        int h = mScaledViewHeight;
        if (iw == ih || iw <= 0 || ih <= 0) {
            iw = h;
        } else {
            iw = iw * h / ih;
        }
        int bias = left ? 0 : mScaledViewWidth - iw;
        icon.setBounds(bias, 0, iw + bias, h);
    }

    private boolean blindClosed() { return mBlindAxisPositionRelative == 0.5f; }
    private boolean leftSideOpen() { return mBlindAxisPositionRelative > 0.5f; }
    private boolean rightSideOpen() { return mBlindAxisPositionRelative < 0.5f; }
//==============================================================================================================
    private class Blind extends Drawable {
        private static final float TEXT_SIZE = 24f;
        private final Paint mPaint;
        private final Paint mPaintText;
        private float mTextOffsetFromLeft;
        private float mTextOffsetFromRight;
        private float mTextBaseline;

        public Blind() {
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(Color.BLUE);

            mPaintText = new Paint();
            mPaintText.setColor(Color.YELLOW);
            mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaintText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE, getResources().getDisplayMetrics()));

            setText();
        }

        public void setText() {
            //this.text = text;
            measureText();
        }

        public void setTextSize(float sp) {
            mPaintText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
            measureText();
        }

        public void measure() {
            measureText();
        }

        private void measureText() {
            String text = mText.toString();
            Rect frame = new Rect();
            mPaintText.getTextBounds(text, 0, text.length(), frame);
            mTextOffsetFromLeft = (mScaledViewWidth - frame.width()) / 2f - frame.left;
            mTextOffsetFromRight = mScaledViewWidth - mTextOffsetFromLeft;
            mTextBaseline = (mScaledViewHeight - frame.height()) / 2f - frame.top;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            String text = mText.toString();
            if (blindClosed()) {
                canvas.drawColor(Color.BLUE);

                canvas.drawText(text, 0, text.length(),
                        mTextOffsetFromLeft,
                        mTextBaseline,
                        mPaintText);
            }
            else if (leftSideOpen()) {
                float left = (mBlindAxisPositionRelative - 0.5f) * mScaledViewWidth;
                canvas.drawRect(left, 0, mScaledViewWidth, mScaledViewHeight, mPaint);
                canvas.drawText(text, left + mTextOffsetFromLeft, mTextBaseline, mPaintText);
            }
            else if (rightSideOpen()) {
                float right = (mBlindAxisPositionRelative + 0.5f) * mScaledViewWidth;
                canvas.drawRect(0, 0, right, mScaledViewHeight, mPaint);
                canvas.drawText(text, right - mTextOffsetFromRight, mTextBaseline, mPaintText);
            }
            else throw new IllegalStateException("Illegal BlindedView state at onDraw");
        }
        @Override
        public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) { mPaint.setColorFilter(colorFilter); }
        @Override
        public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
