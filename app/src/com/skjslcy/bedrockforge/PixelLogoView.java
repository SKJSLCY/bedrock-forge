package com.skjslcy.bedrockforge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/** 应用标题：4 个汉字，关闭抗锯齿 + 硬阴影，模拟像素字。 */
final class PixelLogoView extends View {

    private final Paint paint = new Paint();
    private String text = "基岩工坊";
    private float spacing;
    private float accentHeight;
    private float accentGap;

    PixelLogoView(Context c) {
        super(c);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26,
                getResources().getDisplayMetrics()));
        paint.setAntiAlias(false);
        paint.setSubpixelText(false);
        paint.setDither(false);
        spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                getResources().getDisplayMetrics());
        accentHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f,
                getResources().getDisplayMetrics());
        accentGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26,
                getResources().getDisplayMetrics()));
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += paint.measureText(text.substring(i, i + 1));
            if (i < text.length() - 1) w += spacing;
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        int h = (int) Math.ceil(fm.descent - fm.ascent) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension((int) Math.ceil(w) + getPaddingLeft() + getPaddingRight(), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = getPaddingTop() - fm.ascent;
        float x = getPaddingLeft();
        float shadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f,
                getResources().getDisplayMetrics());
        // 硬阴影
        paint.setColor(0xFF1A1A1A);
        float sx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            canvas.drawText(ch, sx + shadow, baseline + shadow, paint);
            sx += paint.measureText(ch) + spacing;
        }
        // 正文：前两字白色，后两字绿色
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            paint.setColor(i < 2 ? 0xFFFFFFFF : 0xFF5BC236);
            canvas.drawText(ch, x, baseline, paint);
            x += paint.measureText(ch) + spacing;
        }
    }
}
