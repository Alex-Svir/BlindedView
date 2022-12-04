package org.shurman.blindedview;

import android.annotation.SuppressLint;
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

public class SingleBlindView extends View {
    private static final float SENSITIVITY = 20f;               //  todo
    private static final float DEFAULT_BLIND_STROKE = 0.4f;
    private static final float DEFAULT_LATCH_RELEASE = 0.3f;


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
    private Drawable mDrawableLeft;
    private Drawable mDrawableRight;
    private float mLeftBlindAxisSentinelRelative;
    private float mRightBlindAxisSentinelRelative;
    private float mLatchReleaseRelative;
    //  measured
    private int mScaledViewWidth;
    private int mScaledViewHeight;
    //  moving blind variables
    private final Blind mBlind;
    private float mBlindAxisPositionRelative;
    private int mBlindsFlags;
    private final PointF mRefPoint;
//--------------------------------------------------------------------------

    public SingleBlindView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SingleBlindView, 0, 0);
        float blindStroke = DEFAULT_BLIND_STROKE;
        float latchRelease = DEFAULT_LATCH_RELEASE;

        try {
            mDrawableLeft = ta.getDrawable(R.styleable.SingleBlindView_drawableLeft2);
            mDrawableRight = ta.getDrawable(R.styleable.SingleBlindView_drawableRight2);
            blindStroke = ta.getFloat(R.styleable.SingleBlindView_blindStroke, DEFAULT_BLIND_STROKE);
            latchRelease = ta.getFloat(R.styleable.SingleBlindView_latchRelease2, DEFAULT_LATCH_RELEASE);
        } catch (RuntimeException e) { l(e.toString()); }

        assert 0f < blindStroke && blindStroke <= 0.5f : "Illegal blindWidth";
        mLeftBlindAxisSentinelRelative = 0.5f - blindStroke;
        mRightBlindAxisSentinelRelative = 0.5f + blindStroke;
        assert 0f <= latchRelease && latchRelease <= 1f;
        mLatchReleaseRelative = blindStroke * latchRelease;

        mBlindAxisPositionRelative = 0.5f;
        mBlindsFlags = 0;
        mRefPoint = new PointF();
        mBlind = new Blind();

        super.setOnClickListener(v -> {
            switch (mBlindsFlags & TARGET_MASK) {
                case TARGET_BUTTON_L:
                    l("Button Left callback");//todo
                    break;
                case TARGET_BUTTON_R:
                    l("Button Right callback");//todo
                    break;
                case TARGET_BLIND:
                    //mBlindAxisPositionRelative = 0.5f;
                    shut(0.5f);
                    //__refreshCorrectly();
                    break;
                default:
                    throw new IllegalStateException("Illegal state at performClick()");
            }});
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
                //  TODO difference?
            case MotionEvent.ACTION_CANCEL:
                switch (mBlindsFlags & STATE_MASK) {
                    case STATE_CLICK:
                        int target = mBlindsFlags & TARGET_MASK;
                        if (target == TARGET_BUTTON_L) {
                            if (withinRect(x, y, mDrawableLeft.getBounds()))
                                performClick();
                            break;
                        } else if (target == TARGET_BUTTON_R) {
                            if (withinRect(x, y, mDrawableRight.getBounds()))
                                performClick();
                            break;
                        } else if (target == TARGET_BLIND) {
                            performClick();
                            break;
                        }
                        throw new IllegalStateException("Illegal BlindView state on Click UP/CANCEL");
                    case STATE_SLIDE:
                        assert (mBlindsFlags & TARGET_MASK) == TARGET_BLIND : "onTouch::UP/CANCEL::SLIDE not the blind";
                        if (leftSideOpen()) {
                            float latchPos = mRightBlindAxisSentinelRelative - mLatchReleaseRelative;
                            //mBlindAxisPositionRelative = mBlindAxisPositionRelative >= latchPos ? latchPos : 0.5f;
                            shut(mBlindAxisPositionRelative >= latchPos ? latchPos : 0.5f);
                        } else if (rightSideOpen()) {
                            float latchPos = mLeftBlindAxisSentinelRelative + mLatchReleaseRelative;
                            //mBlindAxisPositionRelative = mBlindAxisPositionRelative <= latchPos ? latchPos : 0.5f;
                            shut(mBlindAxisPositionRelative <= latchPos ? latchPos : 0.5f);
                        }
                        //__refreshCorrectly();
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
                        int target = mBlindsFlags & TARGET_MASK;
                        if (target == TARGET_BUTTON_L || target == TARGET_BUTTON_R) break;
                        assert target == TARGET_BLIND : "Illegal target at onTouch::MOVE::CLICK";
                        if (underConversionThreshold(x, y)) break;
                        mBlindsFlags = (mBlindsFlags & ~STATE_MASK) | STATE_SLIDE;
                    case STATE_SLIDE:
                        if (slide(x))
                            __refreshCorrectly();
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
                if (withinRect(x, y, mDrawableLeft.getBounds())) {
                    mBlindsFlags = TARGET_BUTTON_L | STATE_CLICK;
                } else {
                    mBlindsFlags = TARGET_NONE | STATE_NOTHING;
                }
            } else {    //  on open blind
                mBlindsFlags = TARGET_BLIND | STATE_CLICK;
            }
        } else if (rightSideOpen()) {
            if (relativeX > mBlindAxisPositionRelative + 0.5f) {    //  to the right of blind
                if (withinRect(x, y, mDrawableRight.getBounds())) {
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

    private void shut(float position) {
        mBlindAxisPositionRelative = position;
        __refreshCorrectly();
    }

    private boolean underConversionThreshold(float x, float y) {
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

    private boolean blindClosed() { return mBlindAxisPositionRelative == 0.5f; }
    private boolean leftSideOpen() { return mBlindAxisPositionRelative > 0.5f; }
    private boolean rightSideOpen() { return mBlindAxisPositionRelative < 0.5f; }
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
            if (blindClosed()) {
                canvas.drawColor(Color.BLUE);

                canvas.drawText(text, 0, text.length(),
                        mTextOffsetFromLeft,
                        mTextBaseline,
                        mText);
            }
            else if (leftSideOpen()) {
                float left = (mBlindAxisPositionRelative - 0.5f) * mScaledViewWidth;
                canvas.drawRect(left, 0, mScaledViewWidth, mScaledViewHeight, mPaint);
                canvas.drawText(text, left + mTextOffsetFromLeft, mTextBaseline, mText);
            }
            else if (rightSideOpen()) {
                float right = (mBlindAxisPositionRelative + 0.5f) * mScaledViewWidth;
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

    private static void l(String text) { Log.d("LOG_TAG::", text); }
}