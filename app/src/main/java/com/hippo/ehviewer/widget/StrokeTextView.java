package com.hippo.ehviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;

@SuppressLint("AppCompatCustomView")
public class StrokeTextView extends TextView {

    private TextView outlineTextView = null;

    public StrokeTextView(Context context) {
        this(context, null);
    }

    public StrokeTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StrokeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        outlineTextView = new TextView(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        //1.获取参数
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.StrokeTextView);
        int stroke_color = ta.getColor(R.styleable.StrokeTextView_stroke_color, Color.WHITE);
        float stroke_width = ta.getDimension(R.styleable.StrokeTextView_stroke_width, 2);

        //2.初始化TextPaint
        TextPaint paint = outlineTextView.getPaint();
        paint.setStrokeWidth(stroke_width);
        paint.setStyle(Paint.Style.STROKE);
        outlineTextView.setTextColor(stroke_color);
        outlineTextView.setGravity(getGravity());
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        outlineTextView.setLayoutParams(params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //设置轮廓文字
        CharSequence outlineText = outlineTextView.getText();

        if (outlineText == null || !outlineText.equals(getText())) {
            outlineTextView.setText(getText());
            postInvalidate();
        }
        outlineTextView.measure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        outlineTextView.layout(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        outlineTextView.draw(canvas);
        super.onDraw(canvas);
    }
}
