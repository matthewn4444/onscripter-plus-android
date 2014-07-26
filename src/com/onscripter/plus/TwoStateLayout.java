package com.onscripter.plus;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.Scroller;

public class TwoStateLayout extends ViewGroup {
    private final LinearLayout mLayout;
    private final boolean mIsRight;
    private final int mFlingDistance;
    private final LinearLayout.LayoutParams mSpacedParams;
    private final int mMinimumVelocity;
    private final Scroller mScroller;
    private final int mMarginThreshold;

    private float mLastMotionX;
    private boolean mScrolling;
    private boolean mQuickReturn;
    private boolean mIsUnableToDrag;
    private boolean mIsDragging;
    private int mScrollX;
    private float mInitialMotionX;
    private SIDE mSide;
    private boolean mIsOnRightSide;
    private TwoStateLayout mOtherLayout;
    private OnSideMovedListener mListener;

    protected int mActivePointerId = INVALID_POINTER;
    protected int mMaximumVelocity;
    protected VelocityTracker mVelocityTracker;

    public static enum SIDE {LEFT, RIGHT};

    private static final Interpolator sInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static final int INVALID_POINTER = -1;
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips
    private static final int MARGIN_THRESHOLD = 48; // dips

    public interface OnSideMovedListener {
        public void onLeftSide(TwoStateLayout v);
        public void onRightSide(TwoStateLayout v);
    }

    public TwoStateLayout(Context context) {
        this(context, null);
    }

    public TwoStateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScrollX = 0;
        mIsRight = true;
        mQuickReturn = false;
        mIsDragging = false;
        mScrolling = false;
        mScroller = new Scroller(context, sInterpolator);
        mIsOnRightSide = false;

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mMarginThreshold = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                MARGIN_THRESHOLD, getResources().getDisplayMetrics()) / 2;

        final float density = context.getResources().getDisplayMetrics().density;
        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);

        // Create inner layout and put current children in it
        mLayout = new LinearLayout(context);
        mLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mLayout.setOrientation(LinearLayout.VERTICAL);
        mLayout.setPadding(getPaddingLeft(), getPaddingLeft(), getPaddingRight(), getPaddingBottom());
        setPadding(0, 0, 0, 0);
        super.addView(mLayout);

        // Style
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TwoStateLayout);
        setSideMode(ta.getInt(R.styleable.TwoStateLayout_side, 0) == 0 ? SIDE.LEFT : SIDE.RIGHT);
        int orientation = ta.getInt(R.styleable.TwoStateLayout_orientation, 0) == 0 ?
                LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        mLayout.setOrientation(orientation);
        mLayout.setBackgroundColor(Color.argb(170, 0, 0, 0));

        // This is hardcoded
        if (orientation == LinearLayout.VERTICAL) {
            mSpacedParams = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1);
        } else {
            mSpacedParams = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, 1);
        }

        float weightSum = ta.getFloat(R.styleable.TwoStateLayout_weightSum, 0);
        if (weightSum > 0) {
            mLayout.setWeightSum(weightSum);
        }
        mLayout.setWeightSum(4.0f);
        ta.recycle();
    }

    public void setSideMode(SIDE side) {
        if (mSide != side) {
            mSide = side;
            if (mSide == SIDE.LEFT) {
                moveLeft(false);
            } else {
                moveRight(false);
            }
            invalidate();
        }
    }

    public void setOtherLayout(TwoStateLayout layout) {
        mOtherLayout = layout;
    }

    public void toggle() {
        if (isOnRight()) {
            moveLeft();
        } else {
            moveRight();
        }
    }

    public void moveLeft() {
        moveLeft(true);
    }

    public void moveRight() {
        moveRight(true);
    }

    public void moveLeft(boolean animate) {
        if (animate) {
            smoothScrollTo(getMaxBound());
        } else {
            scrollTo(getMaxBound(), 0);
        }
        mIsOnRightSide = false;
    }

    public void moveRight(boolean animate) {
        if (animate) {
            smoothScrollTo(getMinBound());
        } else {
            scrollTo(getMinBound(), 0);
        }
        mIsOnRightSide = true;
    }

    public boolean isOnRight() {
        return mIsRight;
    }

    public void setOnSideMovedListener(OnSideMovedListener listener) {
        mListener = listener;
    }

    private void endDrag() {
        mActivePointerId = INVALID_POINTER;
        mIsDragging = false;
        mIsUnableToDrag = false;
        mQuickReturn = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void startDrag() {
        mQuickReturn = false;
        mIsDragging = true;
    }

    private int getPointerIndex(MotionEvent ev, int id) {
        int activePointerIndex = MotionEventCompat.findPointerIndex(ev, id);
        if (activePointerIndex == -1) {
            mActivePointerId = INVALID_POINTER;
        }
        return activePointerIndex;
    }

    private void determineDrag(MotionEvent ev) {
        final int activePointerId = mActivePointerId;
        final int pointerIndex = getPointerIndex(ev, activePointerId);
        if (activePointerId == INVALID_POINTER || pointerIndex == INVALID_POINTER) {
            return;
        }
        final float x = MotionEventCompat.getX(ev, pointerIndex);
        startDrag();
        mLastMotionX = x;
    }

    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return FloatMath.sin(f);
    }

    private int getMinBound() {
        return -mLayout.getWidth();
    }

    private int getMaxBound() {
        return 0;
    }

    public void smoothScrollTo(int x) {
        smoothScrollTo(x, 0);
    }

    public void smoothScrollTo(int x, int velocity) {
        if (getChildCount() == 0) {
            return;
        }
        int sx = getScrollX();
        int sy = getScrollY();
        int dx = x - sx;
        int dy = 0 - sy;
        if (dx == 0 && dy == 0) {
            completeScroll();
            return;
        }
        mScrolling = true;

        final int width = mLayout.getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
        final float distance = halfWidth + halfWidth *
                distanceInfluenceForSnapDuration(distanceRatio);

        int duration = 0;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float pageDelta = (float) Math.abs(dx) / width;
            duration = (int) ((pageDelta + 1) * 100);
            duration = MAX_SETTLE_DURATION;
        }
        duration = Math.min(duration, MAX_SETTLE_DURATION);

        mScroller.startScroll(sx, sy, dx, dy, duration);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished()) {
            if (mScroller.computeScrollOffset()) {
                int oldX = getScrollX();
                int oldY = getScrollY();
                int x = mScroller.getCurrX();
                int y = mScroller.getCurrY();

                if (oldX != x || oldY != y) {
                    scrollTo(x, y);
                }

                // Keep on drawing until the animation has finished.
                invalidate();
                return;
            }
        }

        // Done with scroll, clean up state.
        completeScroll();
    }

    private void completeScroll() {
        boolean needPopulate = mScrolling;
        if (needPopulate) {
            // Done with scroll, no longer want to cache view drawing.
            mScroller.abortAnimation();
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (oldX != x || oldY != y) {
                scrollTo(x, y);
            }
        }
        mScrolling = false;
    }

    @Override
    public void scrollTo(int x, int y) {
        if (mScrollX != x) {
            mScrollX = x;
            if (mOtherLayout != null && mLayout.getWidth() > 0) {
                mOtherLayout.scrollTo(- Math.abs(-x - mLayout.getWidth()), y);
            }
            super.scrollTo(x, y);
            if (mListener != null) {
                if (x == getMinBound()) {
                    mListener.onRightSide(this);
                } else if (x == getMaxBound()) {
                    mListener.onLeftSide(this);
                }
            }
        }
    }

    private boolean thisTouchAllowed(MotionEvent ev) {
        int x = (int) (ev.getX() + mScrollX);
        int sideX = mSide == SIDE.RIGHT ? mLayout.getLeft() : mLayout.getRight();       // This x value takes the opposite side
        return x >= sideX - mMarginThreshold && x <= mMarginThreshold + sideX;
    }

    private boolean touchInQuickReturn(MotionEvent ev) {
        return ev.getX() + mScrollX >= mLayout.getLeft();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isEnabled()) {
            return false;
        }

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
                || (action != MotionEvent.ACTION_DOWN && mIsUnableToDrag)) {
            endDrag();
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_MOVE:
            determineDrag(ev);
            break;
        case MotionEvent.ACTION_DOWN:
            int index = MotionEventCompat.getActionIndex(ev);
            mActivePointerId = MotionEventCompat.getPointerId(ev, index);
            if (mActivePointerId == INVALID_POINTER) {
                break;
            }
            mLastMotionX = mInitialMotionX = MotionEventCompat.getX(ev, index);
            if (thisTouchAllowed(ev)) {
                mIsUnableToDrag = false;
                mIsDragging = false;
                if (isOnRight() && touchInQuickReturn(ev)) {
                    mQuickReturn = true;
                }
            } else {
                mIsUnableToDrag = true;
            }
            break;
            default:
                break;
        }
        if (!mIsDragging) {
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityTracker.obtain();
            }
            mVelocityTracker.addMovement(ev);
        }
        return mIsDragging || mQuickReturn;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isEnabled()) {
            return false;
        }

        final int action = ev.getAction();

        if (!mIsDragging && !thisTouchAllowed(ev)) {
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action & MotionEventCompat.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
            completeScroll();

            // Remember where the motion event started
            int index = MotionEventCompat.getActionIndex(ev);
            mActivePointerId = MotionEventCompat.getPointerId(ev, index);
            mLastMotionX = mInitialMotionX = ev.getX();
            break;
        case MotionEvent.ACTION_MOVE:
            if (!mIsDragging) {
                determineDrag(ev);
                if (mIsUnableToDrag) {
                    return false;
                }
            }
            if (mIsDragging) {
                // Scroll to follow the motion event
                final int activePointerIndex = getPointerIndex(ev, mActivePointerId);
                if (mActivePointerId == INVALID_POINTER) {
                    break;
                }
                final float x = MotionEventCompat.getX(ev, activePointerIndex);
                final float deltaX = mLastMotionX - x;
                mLastMotionX = x;
                float oldScrollX = getScrollX();
                float scrollX = oldScrollX + deltaX;
                final float minBound = getMinBound();
                final float maxBound = getMaxBound();
                if (scrollX < minBound) {
                    scrollX = minBound;
                } else if (scrollX > maxBound) {
                    scrollX = maxBound;
                }
                // Don't lose the rounded component
                mLastMotionX += scrollX - (int) scrollX;
                scrollTo((int) scrollX, getScrollY());

                mIsOnRightSide = x != minBound;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mIsDragging) {
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(
                        velocityTracker, mActivePointerId);
                final int totalDelta = (int) (ev.getX() - mInitialMotionX);
                final int xThreshold = (int) (mLayout.getWidth() * (mSide == SIDE.LEFT ? 1.5 : 0.5) + mLayout.getLeft());
                if (Math.abs(totalDelta) > mFlingDistance && Math.abs(initialVelocity) > mMinimumVelocity) {
                    if (initialVelocity > 0 && totalDelta > 0) {
                        moveRight();
                    } else {
                        moveLeft();
                    }
                } else if (ev.getX() < xThreshold) {
                    moveLeft();
                } else {
                    moveRight();
                }
                mActivePointerId = INVALID_POINTER;
                endDrag();
            } else if (mQuickReturn && touchInQuickReturn(ev)) {
                smoothScrollTo(0, 0);
                endDrag();
            }
            break;
        case MotionEvent.ACTION_CANCEL:
            if (mIsDragging) {
                mActivePointerId = INVALID_POINTER;
                endDrag();
            }
            break;
        case MotionEventCompat.ACTION_POINTER_DOWN:
            final int indexx = MotionEventCompat.getActionIndex(ev);
            mLastMotionX = MotionEventCompat.getX(ev, indexx);
            mActivePointerId = MotionEventCompat.getPointerId(ev, indexx);
            break;
        }
        return true;
    }

    @Override
    public void addView(View child) {
        mLayout.addView(child);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() > 1) {
            // Move all children into the layout
            while(getChildCount() > 1) {
                View v = getChildAt(1);
                v.setLayoutParams(mSpacedParams);       // TODO hardcoded layout, make it smarter
                removeView(v);
                mLayout.addView(v);
            }
        }

        int width = getDefaultSize(0, widthMeasureSpec);
        int height = getDefaultSize(0, heightMeasureSpec);
        setMeasuredDimension(width * 2, height);

        // First time measuring needs to make the layout to the right
        if (mLayout.getWidth() == 0 && mIsOnRightSide) {
            int toX = - width;
            scrollTo(toX, 0);
        }

        final int contentWidth = getChildMeasureSpec(widthMeasureSpec, 0, width);
        final int contentHeight = getChildMeasureSpec(heightMeasureSpec, 0, height);
        mLayout.measure(contentWidth, contentHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = (r - l);
        final int height = b - t;
        mLayout.layout(0, 0, width / 2, height);
    }

    protected static void log(Object... txt) {
        String returnStr = "";
        int i = 1;
        int size = txt.length;
        if (size != 0) {
            returnStr = txt[0] == null ? "null" : txt[0].toString();
            for (; i < size; i++) {
                returnStr += ", "
                        + (txt[i] == null ? "null" : txt[i].toString());
            }
        }
        Log.i("lunch", returnStr);
    }
}
