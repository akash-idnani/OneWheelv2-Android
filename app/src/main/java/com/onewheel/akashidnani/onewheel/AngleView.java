package com.onewheel.akashidnani.onewheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class AngleView extends View {

    private Paint axesPaint;
    private Paint markerPaint;
    private Paint textPaint;

    private float width = 0;
    private float height = 0;

    private float xAngle = 0;
    private float yAngle = 0;

    private static final float MARGIN_X_RATIO = 10;
    private static final float MARGIN_Y_RATIO = 10;

    public AngleView(Context context) {
        super(context);

        init();
    }

    public AngleView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public AngleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        axesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axesPaint.setColor(getResources().getColor(android.R.color.black));
        axesPaint.setStyle(Paint.Style.STROKE);
        axesPaint.setStrokeWidth(8);

        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(getResources().getColor(android.R.color.holo_blue_dark));
        markerPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(android.R.color.holo_blue_dark));
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(84);
    }

    public void setAngles(float x, float y) {
        xAngle = x;
        yAngle = y;

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = w;
        height = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawLine(width / MARGIN_X_RATIO, height / 2,
                width - width / MARGIN_X_RATIO, height / 2,
                axesPaint);

        canvas.drawLine(width / 2, height / MARGIN_Y_RATIO,
                width / 2, height - height / MARGIN_Y_RATIO,
                axesPaint);

        float centerX = map(xAngle, -50, 50, 0, width);
        float centerY = map(yAngle, -50, 50, 0, height);

        canvas.drawCircle(centerX, centerY, 35, markerPaint);

        canvas.drawText(String.format(Locale.US, "(%.1f, %.1f)", xAngle, yAngle),
                15, height - 15, textPaint);
    }

    private static float map(float in, float in_min, float in_max, float out_min, float out_max) {
        float ret = (in - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
        if (ret < out_min) return out_min;
        if (ret > out_max) return out_max;

        return ret;
    }
}
