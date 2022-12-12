package org.shurman.blindedview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
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

    protected OnInteractionListener mOnInteractionListener;
    //attrs
    protected Drawable mDrawableLeft;
    protected Drawable mDrawableRight;
    private float mBlindWidth;
    private float mLatchRelease;
    protected CharSequence mText;
    //measured
    protected int mScaledViewWidth;
    protected int mScaledViewHeight;
    protected final PointF mRefPoint;

    public AbsBlindedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AbsBlindedView, 0, 0);
        float blindWidth = DEFAULT_BLIND_WIDTH;
        float latchRelease = DEFAULT_LATCH_RELEASE;

        try {
            mDrawableLeft = ta.getDrawable(R.styleable.AbsBlindedView_drawableLeft);
            mDrawableRight = ta.getDrawable(R.styleable.AbsBlindedView_drawableRight);
            blindWidth = ta.getFloat(R.styleable.AbsBlindedView_blindWidth, DEFAULT_BLIND_WIDTH);
            latchRelease = ta.getFloat(R.styleable.AbsBlindedView_latchRelease, DEFAULT_LATCH_RELEASE);
        } catch (RuntimeException e) { l(e.toString()); }

        setBlindWidth(blindWidth);
        setLatchRelease(latchRelease);

        mRefPoint = new PointF();

        mText = "";        //  todo
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
    public void setText(CharSequence text) { mText = text.subSequence(0, text.length()); }  //  todo
    public CharSequence getText() { return mText.subSequence(0, mText.length()); }  //  todo

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mScaledViewWidth = getMeasuredWidth();
        mScaledViewHeight = getMeasuredHeight();
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
