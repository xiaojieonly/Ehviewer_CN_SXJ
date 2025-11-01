package com.hippo.ehviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.hippo.easyrecyclerview.EasyRecyclerView;

public class MyEasyRecyclerView extends EasyRecyclerView {
    public MyEasyRecyclerView(Context context) {
        super(context);
    }

    public MyEasyRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyEasyRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try{
            return super.onTouchEvent(ev);
        }catch (IllegalArgumentException e){
            Log.e("RecyclerView", "IllegalArgumentException",e);
        }
        return  true;
    }

}
