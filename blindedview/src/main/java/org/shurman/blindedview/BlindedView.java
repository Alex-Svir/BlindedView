package org.shurman.blindedview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BlindedView extends View {
    private static final float DEFAULT_BLIND_WIDTH = 0.4f;
    private static final float DEFAULT_LATCH_RELEASE = 0f;
    private static final float SENSITIVITY = 20f;               //  todo

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
    private Drawable mDrawableLeft;
    private Drawable mDrawableRight;
    private float mLeftBlindBaseRelative;
    private float mRightBlindBaseRelative;
    private float mLeftLatchRelative;
    private float mRightLatchRelative;
//  measured
    private int mScaledLeftBlindBase;
    private int mScaledRightBlindBase;
    private int mScaledViewWidth;
    private int mScaledViewHeight;
//  moving blind variables
    private final Blind mBlind;
    private float mMovingBlindPositionRelative;
    private int mBlindsFlags;
    private final PointF mRefPoint;
//--------------------------------------------------------------------------

    public BlindedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BlindedView, 0, 0);
        float blindWidth = DEFAULT_BLIND_WIDTH;
        float latchRelease = DEFAULT_LATCH_RELEASE;

        try {
            mDrawableLeft = ta.getDrawable(R.styleable.BlindedView_drawableLeft);
            mDrawableRight = ta.getDrawable(R.styleable.BlindedView_drawableRight);
            blindWidth = ta.getFloat(R.styleable.BlindedView_blindWidth, DEFAULT_BLIND_WIDTH);
            latchRelease = ta.getFloat(R.styleable.BlindedView_latchRelease, DEFAULT_LATCH_RELEASE);
        } catch (RuntimeException e) { l(e.toString()); }

        assertion(0f < blindWidth && blindWidth <= 0.5f, "Illegal blindWidth");
        mLeftBlindBaseRelative = blindWidth;
        mRightBlindBaseRelative = 1 - blindWidth;
        assertion(0f <= latchRelease && latchRelease <= 1f);
        mLeftLatchRelative = mLeftBlindBaseRelative * (1 - latchRelease);
        mRightLatchRelative = mRightBlindBaseRelative + blindWidth * latchRelease;

        mMovingBlindPositionRelative = Float.NaN;
        mBlindsFlags = 0;
        mRefPoint = new PointF();
        mBlind = new Blind();

        super.setOnClickListener(v -> {});//todo
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getBackground() == null)
            canvas.drawColor(Color.YELLOW);

        if (mDrawableLeft != null) mDrawableLeft.draw(canvas);
        else l("Left null");
        if (mDrawableRight != null) mDrawableRight.draw(canvas);
        else l("Right null");

        mBlind.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //l("Measures provided: " + MeasureSpec.toString(widthMeasureSpec) + "; " + MeasureSpec.toString(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //l("\tMeasured: " + getMeasuredWidth() + "x" + getMeasuredHeight());
        mScaledViewWidth = getMeasuredWidth();
        mScaledViewHeight = getMeasuredHeight();
        mScaledLeftBlindBase = (int) (mScaledViewWidth * mLeftBlindBaseRelative);
        mScaledRightBlindBase = (int) (mScaledViewWidth * mRightBlindBaseRelative);

        measureIcon(mDrawableLeft, true);
        measureIcon(mDrawableRight, false);

        mBlind.measure();
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {}

    @Override
    public boolean performClick() {
        switch (mBlindsFlags & BUTTONS_MASK) {
            case BUTTON_L:
                l("Button Left callback");//todo
                break;
            case BUTTON_R:
                l("Button Right callback");//todo
                break;
            case BUTTON_NONE:
                mMovingBlindPositionRelative = Float.NaN;
                __refreshCorrectly();
                break;
            default:
                throw new IllegalStateException("Illegal state at performClick()");
        }
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                rememberBlindsDownParams(x, y);
                break;
            case MotionEvent.ACTION_UP:
                //  TODO difference?
            case MotionEvent.ACTION_CANCEL:
                switch (mBlindsFlags & STATE_MASK) {
                    case STATE_CLICK:
                        int buttonsMasked = mBlindsFlags & BUTTONS_MASK;
                        assertion(buttonsMasked != BUTTONS_MASK, "Illegal BlindView state on Click UP/CANCEL");
                        if (buttonsMasked == BUTTON_L && !withinRect(x, y, mDrawableLeft.getBounds())) {
                            break;
                        }
                        if (buttonsMasked == BUTTON_R && !withinRect(x, y, mDrawableRight.getBounds())) {
                            break;
                        }
                        if (buttonsMasked == BUTTON_NONE && (mBlindsFlags & BLINDS_MASK) == BLINDS_BRIDGE
                                && (x < mScaledLeftBlindBase || x > mScaledRightBlindBase || y < 0 || y > mScaledViewHeight)) {
                            break;
                        }
                        performClick();
                        break;
                    case STATE_SLIDE:
                        switch (mBlindsFlags & BLINDS_MASK) {
                            case BLIND_L:
                                mMovingBlindPositionRelative = mMovingBlindPositionRelative < mLeftLatchRelative
                                        ? Float.NaN : mLeftLatchRelative;
                                break;
                            case BLIND_R:
                                mMovingBlindPositionRelative = mMovingBlindPositionRelative > mRightLatchRelative
                                        ? Float.NaN : mRightLatchRelative;
                                break;
                            default:
                                throw new IllegalStateException("Illegal blinds configuration on UP/CANCEL sliding");
                        }
                        __refreshCorrectly();
                        break;
                    case STATE_NOTHING:
                        break;
                    default:
                        throw new IllegalStateException("Illegal BlindView state on UP/CANCEL event");
                }
                mBlindsFlags = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                //  TODO    check staying within view rectangle (before or after conversion???)
                switch (mBlindsFlags & STATE_MASK) {
                    case STATE_CLICK:
                        int masked = mBlindsFlags & BLINDS_MASK;
                        if (masked == BLINDS_MASK) throw new IllegalStateException("Illegal BlindView state: both blinds selected");
                        if (underThresholdSlide(x, y)) break;
                        if (masked == 0) break;
                        mBlindsFlags = (mBlindsFlags & ~STATE_MASK) | STATE_SLIDE;
                        if (masked == BLIND_L && rightBlindOpen()) {
                            mMovingBlindPositionRelative = 0f;
                            slideLeftBlind(x);
                            __refreshCorrectly();
                            break;
                        } else if (masked == BLIND_R && leftBlindOpen()) {
                            mMovingBlindPositionRelative = 1f;
                            slideRightBlind(x);
                            __refreshCorrectly();
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
                            __refreshCorrectly();
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
            } else if (withinRect(x, y, mDrawableLeft.getBounds())) {   //  click on left icon
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
            } else if (withinRect(x, y, mDrawableRight.getBounds())) {  //  on right icon
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

    private boolean underThresholdSlide(float x, float y) {
        float dx = x - mRefPoint.x;
        float dy = y - mRefPoint.y;
        return Math.sqrt(dx * dx + dy * dy) < SENSITIVITY;        //TODO any direction?
    }

    private void __refreshCorrectly() {
        invalidate();
        //requestLayout();
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

    private static boolean withinRect(float x, float y, Rect area) {
        return area != null && x >= area.left && x < area.right && y >= area.top && y < area.bottom;
    }

    private boolean blindsClosed() { return Float.isNaN(mMovingBlindPositionRelative); }
    private boolean leftBlindOpen() { return mMovingBlindPositionRelative <= mLeftBlindBaseRelative; }
    //private boolean leftBlindClosed() { return Float.isNaN(mMovingBlindPositionRelative) || mMovingBlindPositionRelative >= mRightBlindBaseRelative; }
    private boolean rightBlindOpen() { return mMovingBlindPositionRelative >= mRightBlindBaseRelative; }
    //private boolean rightBlindClosed() { return Float.isNaN(mMovingBlindPositionRelative) || mMovingBlindPositionRelative <= mLeftBlindBaseRelative; }
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

    private void assertion(boolean condition, String msg) { if (!condition) throw new IllegalStateException(msg); }
    private void assertion(boolean condition) { assertion(condition, "Illegal BlindedView state"); }

    private static void l(String text) { Log.d("LOG_TAG::", text); }
}
