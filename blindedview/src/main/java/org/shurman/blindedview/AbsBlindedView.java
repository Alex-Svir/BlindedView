package org.shurman.blindedview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

public abstract class AbsBlindedView extends View {
    public interface OnInteractionListener {
        void onBlindedItemClick(View view, boolean left);
        void onBlindClick(View view);
        void onBlindSlideCompleted(View view);
    }

    private static final float CONVERSION_THRESHOLD = 20f;               //  todo
    private static final float DEFAULT_BLIND_WIDTH = 0.4f;
    private static final float DEFAULT_LATCH_RELEASE = 0.3f;

    protected static final float TEXT_SIZE = 24f;
    private static final int FONT_STYLE_BOLD = 1;
    private static final int FONT_STYLE_ITALIC = 2;

    protected OnInteractionListener mOnInteractionListener;
    //attrs
    protected Drawable mDrawableLeft;
    protected Drawable mDrawableRight;
    private float mBlindWidth;
    private float mLatchRelease;
    protected CharSequence mText;
    protected Paint mPaintText;
    protected Drawable mBlindBack;
    //measured
    protected int mScaledViewWidth;
    protected int mScaledViewHeight;
    protected final PointF mRefPoint;
    protected float mTextOffsetFromLeft;
    protected float mTextOffsetFromRight;
    protected float mTextBaseline;

    public AbsBlindedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AbsBlindedView, 0, 0);
        float blindWidth = DEFAULT_BLIND_WIDTH;
        float latchRelease = DEFAULT_LATCH_RELEASE;

        try {
            mDrawableLeft = ta.getDrawable(R.styleable.AbsBlindedView_drawableLeft);
            mDrawableRight = ta.getDrawable(R.styleable.AbsBlindedView_drawableRight);
            blindWidth = ta.getFloat(R.styleable.AbsBlindedView_blindWidth, DEFAULT_BLIND_WIDTH);
            latchRelease = ta.getFloat(R.styleable.AbsBlindedView_latchRelease, DEFAULT_LATCH_RELEASE);

            mText = ta.getText(R.styleable.AbsBlindedView_text);
            if (mText == null) mText = "";
            mPaintText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    ta.getDimension(R.styleable.AbsBlindedView_fontSize, TEXT_SIZE),
                    getResources().getDisplayMetrics()));
            mPaintText.setColor(ta.getColor(R.styleable.AbsBlindedView_fontColor, Color.BLACK));
            Typeface tface;
            switch (ta.getInteger(R.styleable.AbsBlindedView_fontTypeface, 0)) {
                case FONT_STYLE_BOLD | FONT_STYLE_ITALIC:
                    tface = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
                    break;
                case FONT_STYLE_BOLD:
                    tface = Typeface.defaultFromStyle(Typeface.BOLD);
                    break;
                case FONT_STYLE_ITALIC:
                    tface = Typeface.defaultFromStyle(Typeface.ITALIC);
                    break;
                default:
                    tface = Typeface.DEFAULT;
            }
            mPaintText.setTypeface(tface);
            setBlindBack(ta.getDrawable(R.styleable.AbsBlindedView_blindBack));
            if (mBlindBack == null) mBlindBack = new ColorDrawable(Color.WHITE);
        } catch (RuntimeException e) { l(e.toString()); }

        setBlindWidth(blindWidth);
        setLatchRelease(latchRelease);

        mRefPoint = new PointF();
    }

    public abstract void shut();

    public void setBlindWidth(float blindWidth) {
        assert 0f < blindWidth && blindWidth <= 0.5f : "Illegal blindWidth";
        mBlindWidth = blindWidth;
    }
    public float getBlindWidth() { return mBlindWidth; }
    public void setLatchRelease(float latchRelease) {
        assert 0f <= latchRelease && latchRelease <= 1f : "Illegal latchRelease";
        mLatchRelease = latchRelease;
    }
    public float getLatchRelease() { return mLatchRelease; }
    public void setDrawableLeft(Drawable d) { mDrawableLeft = d; }
    public Drawable getDrawableLeft() { return mDrawableLeft; }
    public void setDrawableRight(Drawable d) { mDrawableRight = d; }
    public Drawable getDrawableRight() { return mDrawableRight; }
    public void setText(CharSequence text) {
        if (text == null) { mText = ""; }
        else { mText = text.subSequence(0, text.length()); }  //  todo
        measureText();
    }
    public CharSequence getText() { return mText.subSequence(0, mText.length()); }  //  todo
    public void setFontSize(float sp) {
        mPaintText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
        measureText();
    }
    public float getFontSize() { return mPaintText.getTextSize() / getResources().getDisplayMetrics().scaledDensity; }
    public void setFontColor(int color) { mPaintText.setColor(color); }
    public int getFontColor() { return mPaintText.getColor(); }
    public void setFontTypeface(Typeface typeface) {
        mPaintText.setTypeface(typeface);
        measureText();
    }
    public Typeface getFontTypeface() { return mPaintText.getTypeface(); }
    public void setBlindBack(Drawable blindBack) {
        mBlindBack = blindBack;
        if (mBlindBack == null) mBlindBack = new ColorDrawable(Color.WHITE);
    }
    public Drawable getBlindBack() { return mBlindBack; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mScaledViewWidth = getMeasuredWidth();
        mScaledViewHeight = getMeasuredHeight();
        measureIcon(mDrawableLeft, true);
        measureIcon(mDrawableRight, false);

        measureText();
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

    private void measureText() {
        String text = mText.toString();
        Rect frame = new Rect();
        mPaintText.getTextBounds(text, 0, text.length(), frame);
        mTextOffsetFromLeft = (mScaledViewWidth - frame.width()) / 2f - frame.left;
        mTextOffsetFromRight = mScaledViewWidth - mTextOffsetFromLeft;
        mTextBaseline = (mScaledViewHeight - frame.height()) / 2f - frame.top;
    }

    public void setOnInteractionListener(OnInteractionListener l) {
        mOnInteractionListener = l;
    }

    protected boolean underConversionThreshold(float x, float y) {
        float dx = x - mRefPoint.x;
        float dy = y - mRefPoint.y;
        return Math.sqrt(dx * dx + dy * dy) < CONVERSION_THRESHOLD;        //TODO any direction?
    }

    protected boolean outOfViewBounds(float x, float y) {
        //return 0 <= x && x <= mScaledViewWidth && 0 <= y && y <= mScaledViewHeight;
        return x < 0 || x > mScaledViewWidth || y < 0 || y > mScaledViewHeight;
    }

    protected static boolean withinIcon(float x, float y, Drawable icon) {
        if (null == icon) return false;
        Rect area = icon.getBounds();
        return area != null && x >= area.left && x < area.right && y >= area.top && y < area.bottom;
    }

    protected static void l(String text) { Log.d("LOG_TAG::", text); }
}
