package com.skjslcy.bedrockforge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;

import java.io.InputStream;

/** 液态玻璃背景：保存用户壁纸、解码、模糊与慢速缩放渲染。 */
final class LiquidWallpaper {

    private static final String PREF = "liquid_wallpaper_uri";

    private LiquidWallpaper() {
    }

    static String uri(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString(PREF, "");
    }

    static boolean has(Context c) {
        String u = uri(c);
        return u != null && !u.isEmpty();
    }

    static void set(Context c, String uri) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putString(PREF, uri).apply();
    }

    static Bitmap loadBlurred(Context c) {
        String u = uri(c);
        if (u == null || u.isEmpty()) return null;
        Bitmap src = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            InputStream in = c.getContentResolver().openInputStream(Uri.parse(u));
            src = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
        } catch (Throwable ignored) {
        }
        if (src == null) return null;
        try {
            int w = Math.max(1, src.getWidth() / 6);
            int h = Math.max(1, src.getHeight() / 6);
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setMaskFilter(new BlurMaskFilter(28, BlurMaskFilter.Blur.NORMAL));
            cv.drawBitmap(small, null, new Rect(0, 0, out.getWidth(), out.getHeight()), p);
            if (small != src) small.recycle();
            src.recycle();
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** 全屏壁纸：模糊 + 慢速缩放 + 暗色压底。 */
    static final class View extends android.view.View {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint dim = new Paint();
        private final Matrix matrix = new Matrix();
        private float zoom = 1f;
        private android.animation.ValueAnimator anim;
        private final Paint fallback = new Paint();

        View(Context c) {
            super(c);
            dim.setColor(0x77060A10);
            fallback.setShader(new LinearGradient(0, 0, 0, 2400,
                    new int[]{0xFF0B1A22, 0xFF152C2A, 0xFF2A2330},
                    null, Shader.TileMode.CLAMP));
        }

        void setBitmap(Bitmap b) {
            bitmap = b;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = android.animation.ValueAnimator.ofFloat(1f, 1.06f);
            anim.setDuration(26000);
            anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            anim.addUpdateListener(a -> {
                zoom = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (bitmap == null || bitmap.isRecycled()) {
                canvas.drawRect(0, 0, w, h, fallback);
                canvas.drawRect(0, 0, w, h, dim);
                return;
            }
            float scale = Math.max(w / (float) bitmap.getWidth(),
                    h / (float) bitmap.getHeight()) * zoom;
            float dw = bitmap.getWidth() * scale;
            float dh = bitmap.getHeight() * scale;
            float dx = (w - dw) / 2f;
            float dy = (h - dh) / 2f;
            RectF dst = new RectF(dx, dy, dx + dw, dy + dh);
            canvas.drawBitmap(bitmap, null, dst, paint);
            canvas.drawRect(0, 0, w, h, dim);
        }
    }
}
