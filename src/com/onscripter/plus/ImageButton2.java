package com.onscripter.plus;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class ImageButton2 extends FrameLayout implements OnTouchListener, OnClickListener {

    private final static int DOWN_ALPHA = 150;
    private final static int NORMAL_ALPHA = 255;
    private final static int HOVER_ALPHA = 200;

    private final ImageView mImageView;
    private Drawable mSrcImage;
    private Drawable mSrcImageSelected;
    private OnTouchListener mTouchListener;
    private OnClickListener mClickListener;
    private boolean mSelected = false;

    public ImageButton2(Context context) {
        this(context, null, 0);
    }

    public ImageButton2(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageButton2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mImageView = new ImageView(context);

        TypedArray a = context.obtainStyledAttributes (attrs, R.styleable.ImageButton2, 0, 0);
        mSrcImage = a.getDrawable(R.styleable.ImageButton2_src);
        mSrcImageSelected = a.getDrawable(R.styleable.ImageButton2_src_selected);
        a.recycle();

        super.setOnTouchListener(this);
        mImageView.setImageDrawable(mSrcImage);

        mImageView.setScaleType(ScaleType.FIT_CENTER);

        addView(mImageView);
    }

    public void setNormalDrawable(Drawable drawable) {
        mSrcImage = drawable;
    }

    public void setSelectedDrawable(Drawable drawable) {
        mSrcImageSelected = drawable;
        if (drawable == null) {
            mImageView.setImageDrawable(mSrcImage);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        mSelected = selected;
        if (mSrcImageSelected != null) {
            mImageView.setImageDrawable(selected ? mSrcImageSelected : mSrcImage);
        }
        super.setSelected(selected);
    }

    public boolean getSelected() {
        return mSelected;
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        mTouchListener = l;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    @Override
    public void onClick(View v) {
        mClickListener.onClick(v);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean flag = false;
        if (mTouchListener != null) {
            flag = mTouchListener.onTouch(v, event);
        }
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mImageView.setAlpha(DOWN_ALPHA);
            return true;
        case MotionEvent.ACTION_HOVER_EXIT:
        case MotionEvent.ACTION_UP:
            mImageView.setAlpha(NORMAL_ALPHA);
            setSelected(!mSelected);
            onClick(v);
            return true;
        case MotionEvent.ACTION_HOVER_ENTER:
            mImageView.setAlpha(HOVER_ALPHA);
            return true;
        default:
            break;
        }
        return flag;
    }
}
