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

public class BlindedView extends AbsBlindedView {
    private static final int BLIND_L = 1;
    private static final int BLIND_R = 2;
    private static final int BLINDS_BRIDGE = 0;
    private static final int BLINDS_MASK = 0x3;

    private static final int BUTTON_L = 4;
    private static final int BUTTON_R = 8;
    private static final int BUTTON_NONE = 0;
    private static final int BUTTONS_MASK = 0xC;

    private static final int STATE_SLIDE = 0x10;
    private static final int STATE_CLICK = 0x20;
    private static final int STATE_NOTHING = 0;
    private static final int STATE_MASK = 0x30;
//---------------------------------------------------------------------------
//  attrs
    private float mLeftBlindBaseRelative;
    private float mRightBlindBaseRelative;
    private float mLeftLatchRelative;
    private float mRightLatchRelative;
//  measured
    private int mScaledLeftBlindBase;
    private int mScaledRightBlindBase;
//  moving blind variables
    private final Blind mBlind;
    private float mMovingBlindPositionRelative;
    private int mBlindsFlags;
//--------------------------------------------------------------------------

    public BlindedView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mMovingBlindPositionRelative = Float.NaN;
        mBlindsFlags = 0;
        mBlind = new Blind();

        super.setOnClickListener(v -> {
            switch (mBlindsFlags & BUTTONS_MASK) {
                case BUTTON_L:
                    if (null != mOnBlindedItemClickListener)
                        mOnBlindedItemClickListener.onBlindedItemClick(true);
                    break;
                case BUTTON_R:
                    if (null != mOnBlindedItemClickListener)
                        mOnBlindedItemClickListener.onBlindedItemClick(false);
                    break;
                case BUTTON_NONE:
                    mMovingBlindPositionRelative = Float.NaN;
                    invalidate();
                    break;
                default:
                    throw new IllegalStateException("Illegal state at performClick()");
            }
        });
    }

    @Override
    public void setBlindWidth(float blindWidth) {
        super.setBlindWidth(blindWidth);
        mLeftBlindBaseRelative = blindWidth;
        mRightBlindBaseRelative = 1 - blindWidth;
        mLeftLatchRelative = mLeftBlindBaseRelative * (1 - getLatchRelease());
        mRightLatchRelative = mRightBlindBaseRelative + blindWidth * getLatchRelease();
    }

    @Override
    public void setLatchRelease(float latchRelease) {
        super.setLatchRelease(latchRelease);
        mLeftLatchRelative = mLeftBlindBaseRelative * (1 - latchRelease);
        mRightLatchRelative = mRightBlindBaseRelative + getBlindWidth() * latchRelease;
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
        mScaledLeftBlindBase = (int) (mScaledViewWidth * mLeftBlindBaseRelative);
        mScaledRightBlindBase = (int) (mScaledViewWidth * mRightBlindBaseRelative);

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
                        int blindsMasked = mBlindsFlags & BLINDS_MASK;
                        assert blindsMasked != BLINDS_MASK : "Illegal BlindView state: both blinds selected";
                        if (underConversionThreshold(x, y)) break;
                        if (blindsMasked == BLINDS_BRIDGE) break;
                        mBlindsFlags = (mBlindsFlags & ~STATE_MASK) | STATE_SLIDE;
                        if (blindsMasked == BLIND_L && rightBlindOpen()) {
                            mMovingBlindPositionRelative = 0f;
                            slideLeftBlind(x);
                            invalidate();
                            break;
                        } else if (blindsMasked == BLIND_R && leftBlindOpen()) {
                            mMovingBlindPositionRelative = 1f;
                            slideRightBlind(x);
                            invalidate();
                            break;
                        }
                    case STATE_SLIDE:
                        float oldPos = mMovingBlindPositionRelative;
                        switch (mBlindsFlags & BLINDS_MASK) {
                            case BLIND_L:
                                slideLeftBlind(x);
                                break;
                            case BLIND_R:
                                slideRightBlind(x);
                                break;
                            default:
                                throw new IllegalStateException("Illegal BlindedView moving state; pos" + mMovingBlindPositionRelative);
                        }
                        if (mMovingBlindPositionRelative != oldPos) {
                            invalidate();
                        }
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
        if (relativeX < mLeftBlindBaseRelative) {       //  on / under LEFT blind
            if (blindsClosed()) {   //  closed both
                mBlindsFlags = BLIND_L | STATE_SLIDE;
                mMovingBlindPositionRelative = 0f;
            } else if (rightBlindOpen() || relativeX >= mMovingBlindPositionRelative) {  //  on left blind
                mBlindsFlags = BLIND_L | STATE_CLICK;
            } else if (withinIcon(x, y, mDrawableLeft)) {   //  click on left icon
                mBlindsFlags = BUTTON_L | STATE_CLICK;
            } else {        //  click outside open blind and icon
                mBlindsFlags = STATE_NOTHING;
            }
        } else if (relativeX > mRightBlindBaseRelative) {      //  on / under RIGHT blind
            if (blindsClosed()) {   //  closed both
                mBlindsFlags = BLIND_R | STATE_SLIDE;
                mMovingBlindPositionRelative = 1f;
            } else if (leftBlindOpen() || relativeX <= mMovingBlindPositionRelative) {   //  on right blind
                mBlindsFlags = BLIND_R | STATE_CLICK;
            } else if (withinIcon(x, y, mDrawableRight)) {  //  on right icon
                mBlindsFlags = BUTTON_R | STATE_CLICK;
            } else {        //  outside open blind and icon
                mBlindsFlags = STATE_NOTHING;
            }
        } else {        //  center area
            if (blindsClosed())
                mBlindsFlags = BLINDS_BRIDGE | STATE_NOTHING;
            else
                mBlindsFlags = BLINDS_BRIDGE | STATE_CLICK;
        }
    }

    private void slideLeftBlind(float x) {
        if (x <= 0f) {      //  outside left (view) boundary
            if (mMovingBlindPositionRelative > 0f) {
                mMovingBlindPositionRelative = 0f;
                mRefPoint.x = 0f;
            }
            return;
        }
        if (x >= mScaledLeftBlindBase) {    //  outside right boundary
            if (mRefPoint.x < mScaledLeftBlindBase) {
                mMovingBlindPositionRelative += (mScaledLeftBlindBase - mRefPoint.x) / mScaledViewWidth;
                if (mMovingBlindPositionRelative > mLeftBlindBaseRelative)
                    mMovingBlindPositionRelative = mLeftBlindBaseRelative;
                mRefPoint.x = mScaledLeftBlindBase;
            }
            return;
        }
        mMovingBlindPositionRelative += (x - mRefPoint.x) / mScaledViewWidth;
        if (mMovingBlindPositionRelative < 0f)
            mMovingBlindPositionRelative = 0f;
        else if (mMovingBlindPositionRelative > mLeftBlindBaseRelative)
            mMovingBlindPositionRelative = mLeftBlindBaseRelative;
        mRefPoint.x = x;
    }

    private void slideRightBlind(float x) {
        if (x > mScaledViewWidth) {     //  outside right (view) boundary
            if (mMovingBlindPositionRelative < 1f) {
                mMovingBlindPositionRelative = 1f;
                mRefPoint.x = mScaledViewWidth;
            }
            return;
        }
        if (x <= mScaledRightBlindBase) {   //  outside inner boundary
            if (mRefPoint.x > mScaledRightBlindBase) {
                mMovingBlindPositionRelative += (mScaledRightBlindBase - mRefPoint.x) / mScaledViewWidth;
                if (mMovingBlindPositionRelative < mRightBlindBaseRelative)
                    mMovingBlindPositionRelative = mRightBlindBaseRelative;
                mRefPoint.x = mScaledRightBlindBase;
            }
            return;
        }
        mMovingBlindPositionRelative += (x - mRefPoint.x) / mScaledViewWidth;
        if (mMovingBlindPositionRelative > 1f)
            mMovingBlindPositionRelative = 1f;
        else if (mMovingBlindPositionRelative < mRightBlindBaseRelative)
            mMovingBlindPositionRelative = mRightBlindBaseRelative;
        mRefPoint.x = x;
    }

    private void finalizeTouchInteraction(float x, float y, boolean correctly) {
        int blindsMasked = mBlindsFlags & BLINDS_MASK;
        assert blindsMasked != BLINDS_MASK : "Illegal BlindedView state at finalizeTouch: both blinds selected";
        switch (mBlindsFlags & STATE_MASK) {
            case STATE_CLICK:
                if (!correctly) break;
                int buttonsMasked = mBlindsFlags & BUTTONS_MASK;
                assert buttonsMasked != BUTTONS_MASK : "Illegal BlindedView state at finalizeTouch: both buttons selected";
                if (buttonsMasked == BUTTON_L) {
                    if (!withinIcon(x, y, mDrawableLeft)) break;
                } else if (buttonsMasked == BUTTON_R) {
                    if(!withinIcon(x, y, mDrawableRight)) break;
                } else if (blindsMasked == BLINDS_BRIDGE
                        && (x < mScaledLeftBlindBase || x > mScaledRightBlindBase || y < 0 || y > mScaledViewHeight)) {
                    break;
                }
                performClick();
                break;
            case STATE_SLIDE:
                switch (blindsMasked) {
                    case BLIND_L:
                        mMovingBlindPositionRelative = mMovingBlindPositionRelative < mLeftLatchRelative
                                ? Float.NaN : mLeftLatchRelative;
                        break;
                    case BLIND_R:
                        mMovingBlindPositionRelative = mMovingBlindPositionRelative > mRightLatchRelative
                                ? Float.NaN : mRightLatchRelative;
                        break;
                    default:
                        throw new IllegalStateException("Illegal blinds configuration at finalizeTouch");
                }
                invalidate();
                break;
            case STATE_NOTHING:
                break;
            default:
                throw new IllegalStateException("Illegal BlindView state on finalizing touch");
        }
        mBlindsFlags = 0;
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

    private boolean blindsClosed() { return Float.isNaN(mMovingBlindPositionRelative); }
    private boolean leftBlindOpen() { return mMovingBlindPositionRelative <= mLeftBlindBaseRelative; }
    private boolean rightBlindOpen() { return mMovingBlindPositionRelative >= mRightBlindBaseRelative; }
//==============================================================================================================
    private class Blind extends Drawable {
        private static final float TEXT_SIZE = 24f;
        private final Paint mPaint;
        private final Paint mText;
        private  String text = "Super-Duper View!!!";
        private float mTextOffsetFromLeft;
        private float mTextOffsetFromRight;
        private float mTextBaseline;

        public Blind() {
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(Color.BLUE);

            mText = new Paint();
            mText.setColor(Color.YELLOW);
            mText.setStyle(Paint.Style.FILL_AND_STROKE);
            mText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE, getResources().getDisplayMetrics()));

            setText(text);
        }

        public void setText(String text) {
            this.text = text;
            measureText();
        }

        public void setTextSize(float sp) {
            mText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
            measureText();
        }

        public void measure() {
            measureText();
        }

        private void measureText() {
            Rect frame = new Rect();
            mText.getTextBounds(text, 0, text.length(), frame);
            mTextOffsetFromLeft = (mScaledViewWidth - frame.width()) / 2f - frame.left;
            mTextOffsetFromRight = mScaledViewWidth - mTextOffsetFromLeft;
            mTextBaseline = (mScaledViewHeight - frame.height()) / 2f - frame.top;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (blindsClosed()) {
                canvas.drawColor(Color.BLUE);

                canvas.drawText(text, 0, text.length(),
                        mTextOffsetFromLeft,
                        mTextBaseline,
                        mText);
            }
            else if (leftBlindOpen()) {
                float left = mMovingBlindPositionRelative * mScaledViewWidth;
                canvas.drawRect(left, 0, mScaledViewWidth, mScaledViewHeight, mPaint);
                canvas.drawText(text, left + mTextOffsetFromLeft, mTextBaseline, mText);
            }
            else if (rightBlindOpen()) {
                float right = mMovingBlindPositionRelative * mScaledViewWidth;
                canvas.drawRect(0, 0, right, mScaledViewHeight, mPaint);
                canvas.drawText(text, right - mTextOffsetFromRight, mTextBaseline, mText);
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
