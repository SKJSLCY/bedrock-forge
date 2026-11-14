package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Minecraft-GUI 风格的绘制与控件工具。 */
public final class MinecraftUi {

    public static int PANEL = 0xFFC6C6C6;
    public static int PANEL_TEXT = 0xFF1B1B1B;
    public static int PANEL_MUTED = 0xFF4A4A4A;
    public static int SLOT = 0xFFA8A8A8;
    public static int SLOT_LIGHT = 0xFFE0E0E0;
    public static int SLOT_DARK = 0xFF777777;
    public static int GREEN = 0xFF2F7D1F;
    public static int GREEN_BRIGHT = 0xFF5BC236;
    public static int RED = 0xFFB3261E;
    public static int AMBER = 0xFFB26A00;

    public static boolean MINIMAL = false;
    public static boolean ORE = false;
    public static boolean LIQUID = false;
    public static int BG = 0xFF2A1D13;
    public static int BORDER = 0xFF151515;
    public static int TEXT = 0xFF1B1B1B;
    public static int MUTED = 0xFF4A4A4A;
    public static int ACCENT = 0xFF5BC236;
    public static int ON_ACCENT = 0xFF102014;

    static void applyTheme(boolean minimal, boolean ore, boolean liquid, boolean dark) {
        MINIMAL = minimal;
        ORE = ore;
        LIQUID = liquid;
        if (liquid) {
            BG = 0x00000000;
            PANEL = 0x26FFFFFF;
            PANEL_TEXT = 0xFFFFFFFF;
            PANEL_MUTED = 0xCCD9E4E0;
            SLOT = 0x14FFFFFF;
            SLOT_LIGHT = 0x33FFFFFF;
            SLOT_DARK = 0x0AFFFFFF;
            BORDER = 0x66FFFFFF;
            TEXT = 0xFFFFFFFF;
            MUTED = 0xCCD9E4E0;
            ACCENT = 0xCC6EE7B7;
            ON_ACCENT = 0xFF062018;
            GREEN = 0xFF7BE0A8;
            GREEN_BRIGHT = 0xCC6EE7B7;
            RED = 0xFFFF8B7B;
            AMBER = 0xFFFFD166;
            return;
        }
        if (ore) {
            // 取自基岩版 Ore UI 实机截图的取样值
            BG = ORE_BG;
            PANEL = ORE_PANEL;
            SLOT = ORE_SLOT;
            SLOT_LIGHT = 0xFF3B3C3E;
            SLOT_DARK = 0xFF26272A;
            BORDER = ORE_BORDER;
            PANEL_TEXT = ORE_TEXT;
            PANEL_MUTED = ORE_MUTED;
            TEXT = ORE_TEXT;
            MUTED = ORE_MUTED;
            ACCENT = ORE_GREEN;
            ON_ACCENT = 0xFFFFFFFF;
            GREEN = ORE_GREEN_HI;
            GREEN_BRIGHT = ORE_GREEN_HI;
            RED = ORE_RED;
            AMBER = 0xFFC9A227;
            return;
        }
        if (!minimal) {
            PANEL = 0xFFC6C6C6;
            PANEL_TEXT = 0xFF1B1B1B;
            PANEL_MUTED = 0xFF4A4A4A;
            SLOT = 0xFFA8A8A8;
            SLOT_LIGHT = 0xFFE0E0E0;
            SLOT_DARK = 0xFF777777;
            GREEN = 0xFF2F7D1F;
            GREEN_BRIGHT = 0xFF5BC236;
            RED = 0xFFB3261E;
            AMBER = 0xFFB26A00;
            BG = 0xFF2A1D13;
            BORDER = 0xFF151515;
            TEXT = 0xFF1B1B1B;
            MUTED = 0xFF4A4A4A;
            ACCENT = 0xFF5BC236;
            ON_ACCENT = 0xFF102014;
            return;
        }
        if (dark) {
            BG = 0xFF000000;
            PANEL = 0xFF000000;
            SLOT = 0xFF0A0A0A;
            SLOT_LIGHT = 0xFF161616;
            SLOT_DARK = 0xFF222222;
            BORDER = 0xFF262626;
            PANEL_TEXT = 0xFFF7F7F4;
            PANEL_MUTED = 0xFFA3A3A0;
            TEXT = 0xFFF7F7F4;
            MUTED = 0xFFA3A3A0;
            ACCENT = 0xFFA5B4FC;
            ON_ACCENT = 0xFF11132A;
            GREEN = 0xFF6EE7A8;
            GREEN_BRIGHT = 0xFFA5B4FC;
            RED = 0xFFFF7062;
            AMBER = 0xFFF0A35E;
        } else {
            BG = 0xFFEEECE6;
            PANEL = 0xFFFFFFFF;
            SLOT = 0xFFF5F3EE;
            SLOT_LIGHT = 0xFFFFFFFF;
            SLOT_DARK = 0xFFE3E0D8;
            BORDER = 0xFFE3E0D8;
            PANEL_TEXT = 0xFF171614;
            PANEL_MUTED = 0xFF77736B;
            TEXT = 0xFF171614;
            MUTED = 0xFF77736B;
            ACCENT = 0xFF4338CA;
            ON_ACCENT = 0xFFFFFFFF;
            GREEN = 0xFF15803D;
            GREEN_BRIGHT = 0xFF4338CA;
            RED = 0xFFB42318;
            AMBER = 0xFFB45309;
        }
    }

    // ------------------------------------------------------------ chat bubbles

    /** 对话气泡背景：跟随当前主题（经典 / 简约 / 苔石 / 液态玻璃）。 */
    public static Drawable chatBubbleDrawable(Context c, boolean mine) {
        if (LIQUID) {
            // 液态玻璃：自己发的用薄绿玻璃，AI 侧用深色玻璃，保证任何壁纸下白字都读得清。
            return rounded(c, mine ? 0x5534D399 : 0x59000000,
                    mine ? 0xAAFFFFFF : 0x4DFFFFFF, 1, 20);
        }
        if (ORE) {
            return rounded(c, mine ? 0xFF2F5A22 : SLOT,
                    mine ? 0xFF1B1B1B : BORDER, 2, 0);
        }
        if (MINIMAL) {
            return rounded(c, mine ? ACCENT : SLOT, mine ? ACCENT : 0, 1, 16);
        }
        float u = dp(c, 2f);
        return new BevelDrawable(mine ? 0xFF8FBF6A : 0xFFC6C6C6, 0xFF151515,
                mine ? 0xFFCBE8B0 : 0xFFFFFFFF, mine ? 0xFF5A7F3A : 0xFF5A5A5A, u, u);
    }

    /** 气泡内正文颜色；返回值直接 setTextColor，不再经过主题映射。 */
    public static int chatBubbleText(boolean mine) {
        if (LIQUID) return 0xFFFFFFFF;
        if (ORE) return mine ? 0xFFEAFFDF : TEXT;
        if (MINIMAL) return mine ? ON_ACCENT : PANEL_TEXT;
        return 0xFF1B1B1B;
    }

    /** 气泡上方“你 / AI 助手”的小标题颜色。 */
    public static int chatNameColor(boolean mine) {
        if (LIQUID) return mine ? 0xFFD9FFF0 : 0xFFFFFFFF;
        if (ORE) return mine ? GREEN : PANEL_MUTED;
        if (MINIMAL) return mine ? GREEN : PANEL_MUTED;
        return mine ? 0xFFD8C7AE : 0xFFB8FFB0;
    }

    /** AI 页状态行的次要文字色。 */
    public static int chatMetaColor() {
        return (MINIMAL || ORE || LIQUID) ? PANEL_MUTED : 0xFFE8E8E8;
    }

    /** AI 页输入框文字色。 */
    public static int chatInputText() {
        return (MINIMAL || ORE || LIQUID) ? PANEL_TEXT : 0xFFB8FFB0;
    }

    /** AI 页输入框提示色。 */
    public static int chatInputHint() {
        return (MINIMAL || ORE || LIQUID) ? PANEL_MUTED : 0xFF6A8A6A;
    }

    // ---------------------------------------------------------------- Ore UI (苔石)
    static final int ORE_BG       = 0xFF48494B;
    static final int ORE_PANEL    = 0xFF454648;
    static final int ORE_TITLE    = 0xFF4A4B4D;
    static final int ORE_SLOT     = 0xFF313234;
    static final int ORE_BORDER   = 0xFF1D1D1E;
    static final int ORE_TEXT     = 0xFFFFFFFF;
    static final int ORE_MUTED    = 0xFFB4B5B7;
    static final int ORE_INK      = 0xFF1E1E1F;
    static final int ORE_LIGHT    = 0xFFD0D1D5;
    static final int ORE_LIGHT_HI = 0xFFEDEDEF;
    static final int ORE_LIGHT_LO = 0xFF575758;
    static final int ORE_GREEN    = 0xFF3B8526;
    static final int ORE_GREEN_HI = 0xFF5F9F4E;
    static final int ORE_GREEN_LO = 0xFF1C4C12;
    static final int ORE_RED      = 0xFFCA3636;
    static final int ORE_RED_HI   = 0xFFE05F5F;
    static final int ORE_RED_LO   = 0xFF741818;
    static final int ORE_PURPLE   = 0xFF7345E5;
    static final int ORE_DIS      = 0xFF5A5B5D;

    static int mixc(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int lighten(int c, float t) {
        return mixc(c, 0xFFFFFFFF, t);
    }

    static int darken(int c, float t) {
        return mixc(c, 0xFF000000, t);
    }

    /**
     * 苔石（Ore UI）立体块：平面填充 + 上/左高光 + 下/右暗边 + 外框；
     * 三态：未选中 / 选中（绿色 + 白色下划线）/ 按下（亮暗互换，凹陷感）。
     */
    public static class OreBlock extends Drawable {
        private final Paint p = new Paint();
        private final float den;
        private final int idleFace, selFace;
        private final boolean marker;
        private boolean pressed, selected, enabled = true;

        public OreBlock(Context c, int idleFace, int selFace, boolean marker) {
            this.den = c.getResources().getDisplayMetrics().density;
            this.idleFace = idleFace;
            this.selFace = selFace;
            this.marker = marker;
        }

        private int px(float v) {
            return Math.max(1, Math.round(v * den));
        }

        @Override public boolean isStateful() {
            return true;
        }

        @Override protected boolean onStateChange(int[] state) {
            boolean pr = false, se = false, en = true;
            for (int st : state) {
                if (st == android.R.attr.state_pressed) pr = true;
                else if (st == android.R.attr.state_selected || st == android.R.attr.state_checked) se = true;
                else if (st == -android.R.attr.state_enabled) en = false;
            }
            if (pr != pressed || se != selected || en != enabled) {
                pressed = pr;
                selected = se;
                enabled = en;
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override public void draw(Canvas canvas) {
            Rect r = getBounds();
            if (r.isEmpty()) return;
            int face = enabled ? (selected ? selFace : idleFace) : ORE_DIS;
            int hi = lighten(face, selected ? 0.42f : 0.36f);
            int lo = darken(face, selected ? 0.52f : 0.46f);
            if (pressed) {
                // 按下：面色压暗 + 亮暗互换，形成“凹进去”的观感
                face = darken(face, 0.16f);
                hi = darken(face, 0.45f);
                lo = lighten(face, 0.22f);
            }
            int bw = px(1.4f), hb = px(1.7f), lb = px(3.4f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORE_BORDER);
            canvas.drawRect(r, p);
            Rect in = new Rect(r.left + bw, r.top + bw, r.right - bw, r.bottom - bw);
            p.setColor(face);
            canvas.drawRect(in, p);
            p.setColor(hi);
            canvas.drawRect(in.left, in.top, in.right, in.top + hb, p);
            canvas.drawRect(in.left, in.top, in.left + hb, in.bottom, p);
            p.setColor(lo);
            canvas.drawRect(in.left, in.bottom - lb, in.right, in.bottom, p);
            canvas.drawRect(in.right - px(1.0f), in.top + hb, in.right, in.bottom - lb, p);
            if (selected && marker) {
                p.setColor(0xFFFFFFFF);
                int mh = px(2.6f);
                int inset = px(10);
                canvas.drawRect(in.left + inset, in.bottom - lb - px(3),
                        in.right - inset, in.bottom - lb - px(3) + mh, p);
            }
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** 平面 + 外框（面板/槽位/终端用，无立体感）。 */
    public static class OreFrame extends Drawable {
        private final Paint p = new Paint();
        private final float den;
        private final int fill, titleLine;

        public OreFrame(Context c, int fill, int titleLine) {
            this.den = c.getResources().getDisplayMetrics().density;
            this.fill = fill;
            this.titleLine = titleLine;
        }

        @Override public void draw(Canvas canvas) {
            Rect r = getBounds();
            if (r.isEmpty()) return;
            int bw = Math.max(2, Math.round(1.3f * den));
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORE_BORDER);
            canvas.drawRect(r, p);
            p.setColor(fill);
            canvas.drawRect(r.left + bw, r.top + bw, r.right - bw, r.bottom - bw, p);
            if (titleLine != 0) {
                p.setColor(titleLine);
                canvas.drawRect(r.left + bw, r.top + bw, r.right - bw,
                        r.top + bw + Math.max(1, Math.round(den)), p);
            }
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static Drawable oreBtn(Context c, int idleFace, int selFace, boolean marker) {
        return new OreBlock(c, idleFace, selFace, marker);
    }

    static Drawable orePlain(Context c, int fill) {
        return new OreFrame(c, fill, 0);
    }

    static Drawable oreTitled(Context c, int fill) {
        return new OreFrame(c, fill, lighten(fill, 0.10f));
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static Typeface mono() {
        return Typeface.create("monospace", Typeface.BOLD);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(I18n.t(s));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(ThemeManager.mapTextColor(color));
        if (MINIMAL || ORE || LIQUID) {
            t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                    bold ? Typeface.BOLD : Typeface.NORMAL));
        } else {
            t.setTypeface(bold ? mono() : Typeface.create("sans-serif", Typeface.NORMAL));
        }
        t.setIncludeFontPadding(false);
        return t;
    }

    // ---------------------------------------------------------------- drawables

    public static class BevelDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fill, border, light, dark;
        private final float bw, lw;

        public BevelDrawable(int fill, int border, int light, int dark, float bw, float lw) {
            this.fill = fill;
            this.border = border;
            this.light = light;
            this.dark = dark;
            this.bw = Math.max(1f, bw);
            this.lw = Math.max(1f, lw);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(border);
            canvas.drawRect(b, paint);
            float l = b.left + bw, t = b.top + bw, r = b.right - bw, bo = b.bottom - bw;
            paint.setColor(fill);
            canvas.drawRect(l, t, r, bo, paint);
            paint.setColor(light);
            canvas.drawRect(l, t, r, t + lw, paint);
            canvas.drawRect(l, t, l + lw, bo, paint);
            paint.setColor(dark);
            canvas.drawRect(l, bo - lw, r, bo, paint);
            canvas.drawRect(r - lw, t, r, bo, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static Drawable rounded(Context c, int fill, int stroke, int strokeWidth, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeWidth > 0) g.setStroke(dp(c, strokeWidth), stroke);
        return g;
    }

    public static Drawable panelDrawable(Context c) {
        if (LIQUID) {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0x33FFFFFF, 0x12FFFFFF, 0x08FFFFFF});
            g.setCornerRadius(dp(c, 28));
            g.setStroke(dp(c, 1), 0x66FFFFFF);
            return g;
        }
        if (ORE) return oreTitled(c, ORE_PANEL);
        if (MINIMAL) return rounded(c, PANEL, BORDER, 1, 20);
        float u = dp(c, 2.5f);
        return new BevelDrawable(PANEL, 0xFF151515, 0xFFFFFFFF, 0xFF5A5A5A, u, u);
    }

    public static Drawable slotDrawable(Context c) {
        if (LIQUID) return rounded(c, 0x14FFFFFF, 0x33FFFFFF, 1, 18);
        if (ORE) return orePlain(c, ORE_SLOT);
        if (MINIMAL) return rounded(c, SLOT, 0, 0, 14);
        float u = dp(c, 1.6f);
        return new BevelDrawable(SLOT, 0xFF8A8A8A, SLOT_LIGHT, SLOT_DARK, u, u);
    }

    public static Drawable terminalDrawable(Context c) {
        if (LIQUID) return rounded(c, 0x33000000, 0x44FFFFFF, 1, 18);
        if (ORE) return orePlain(c, 0xFF2B2C2E);
        if (MINIMAL) return rounded(c, PANEL, BORDER, 1, 16);
        float u = dp(c, 2.5f);
        return new BevelDrawable(0xFF151515, 0xFF000000, 0xFF3A3A3A, 0xFF000000, u, u);
    }

    public static Drawable hotbarDrawable(Context c) {
        if (LIQUID) return rounded(c, 0x1AFFFFFF, 0x55FFFFFF, 1, 28);
        if (ORE) return orePlain(c, ORE_PANEL);
        if (MINIMAL) return rounded(c, PANEL, BORDER, 1, 24);
        float u = dp(c, 2.5f);
        return new BevelDrawable(0xFF2A1D13, 0xFF120D08, 0xFF4C3827, 0xFF0B0805, u, u);
    }

    public static Drawable oreRaisedDrawable(Context c, final int lip, final int top,
                                             final int bottom, final int stroke,
                                             final boolean pressed) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final int b = dp(c, 2);
            private final int bev = pressed ? dp(c, 1) : dp(c, 3);

            @Override public void draw(Canvas canvas) {
                Rect r = getBounds();
                if (r.isEmpty()) return;
                p.setStyle(Paint.Style.FILL);
                p.setColor(lip);
                canvas.drawRect(r, p);
                int faceBottom = r.bottom - bev;
                p.setShader(new android.graphics.LinearGradient(
                        r.left, r.top, r.left, faceBottom, top, bottom,
                        android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRect(r.left + b, r.top + b, r.right - b, faceBottom, p);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x44FFFFFF);
                canvas.drawRect(r.left + b, r.top + b, r.right - b,
                        r.top + b + Math.max(1, dp(c, 2)), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1, dp(c, 1)));
                p.setColor(stroke);
                canvas.drawRect(r.left + b, r.top + b, r.right - b, faceBottom, p);
            }

            @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
        };
    }

    public static Drawable oreGreenDrawable(Context c, boolean pressed) {
        return oreRaisedDrawable(c, 0xFF234A16,
                pressed ? 0xFF377E2A : 0xFF3D8B2F,
                pressed ? 0xFF2F6B24 : 0xFF367C29,
                0xFF2A5C1A, pressed);
    }

    public static Drawable selectedDrawable(Context c) {
        if (LIQUID) return rounded(c, 0x5534D399, 0xAAFFFFFF, 1, 22);
        if (ORE) return oreBtn(c, ORE_GREEN, ORE_GREEN, false);
        if (MINIMAL) return rounded(c, ACCENT, ACCENT, 1, 14);
        float u = dp(c, 2f);
        return new BevelDrawable(0xFFC6C6C6, GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A, u, u);
    }

    public static int selectedTextColor() {
        return (MINIMAL || ORE) ? ON_ACCENT : 0xFF1B1B1B;
    }

    public static Drawable hotbarSlotDrawable(Context c, boolean active) {
        if (LIQUID) {
            return rounded(c, active ? 0x33FFFFFF : 0x00000000,
                    active ? 0x55FFFFFF : 0, active ? 1 : 0, 20);
        }
        if (ORE) return active ? oreBtn(c, ORE_GREEN, ORE_GREEN, false)
                : new android.graphics.drawable.ColorDrawable(0x00000000);
        if (MINIMAL) {
            return rounded(c, active ? ACCENT : 0x00000000, 0, 0, 18);
        }
        float u = dp(c, 2f);
        if (active) {
            return new BevelDrawable(0xFF3A2A1C, GREEN_BRIGHT, 0xFF7EE356, 0xFF21160D, u, u);
        }
        return new BevelDrawable(0xFF3A2A1C, 0xFF1A120B, 0xFF523B26, 0xFF21160D, u, u);
    }

    /** 泥土背景（平铺）。 */
    public static class DirtDrawable extends Drawable {
        private final Paint paint = new Paint();
        private final Paint overlay = new Paint();
        private final BitmapShader shader;

        public DirtDrawable(Context c) {
            Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            Paint p = new Paint();
            int[] palette = {0xFF4A3523, 0xFF3F2D1E, 0xFF553D27, 0xFF332318, 0xFF5C4128};
            Random r = new Random(7);
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    p.setColor(palette[r.nextInt(palette.length)]);
                    cv.drawRect(x * 8, y * 8, x * 8 + 8, y * 8 + 8, p);
                }
            }
            for (int i = 0; i < 60; i++) {
                p.setColor(0x22000000);
                int x = r.nextInt(96), y = r.nextInt(96);
                cv.drawRect(x, y, x + 4, y + 4, p);
            }
            shader = new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            paint.setShader(shader);
            overlay.setColor(0x66000000);
        }

        @Override
        public void draw(Canvas canvas) {
            if (LIQUID) {
                return;
            }
            if (MINIMAL || ORE) {
                canvas.drawColor(BG);
                return;
            }
            canvas.drawRect(getBounds(), paint);
            canvas.drawRect(getBounds(), overlay);
        }

        @Override
        public void setAlpha(int alpha) {
            overlay.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    // ---------------------------------------------------------------- widgets

    public static class McButton extends Button {
        public McButton(Context c, String label) {
            super(c);
            setText(I18n.t(label));
            setAllCaps(false);
            setGravity(Gravity.CENTER);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            setTypeface(mono());
            setIncludeFontPadding(false);
            setPadding(dp(c, 8), 0, dp(c, 8), 0);
            setMinWidth(0);
            setMinHeight(0);
            setStateListAnimator(null);

            if (LIQUID) {
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                setShadowLayer(0, 0, 0, 0);
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_pressed},
                        liquidFace(c, 0x7734D399, 0xCCFFFFFF));
                bg.addState(new int[]{android.R.attr.state_selected},
                        liquidFace(c, 0x5534D399, 0xAAFFFFFF));
                bg.addState(new int[]{android.R.attr.state_pressed},
                        liquidFace(c, 0x40FFFFFF, 0x66FFFFFF));
                bg.addState(new int[]{-android.R.attr.state_enabled},
                        liquidFace(c, 0x14FFFFFF, 0x33FFFFFF));
                bg.addState(new int[]{}, liquidFace(c, 0x24FFFFFF, 0x3CFFFFFF));
                setBackground(bg);
                setTextColor(0xFFFFFFFF);
                return;
            }

            if (ORE) {
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                setShadowLayer(0, 0, 0, 0);
                oreStyle(0);
                return;
            }

            if (MINIMAL) {
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                setShadowLayer(0, 0, 0, 0);
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_pressed},
                        rounded(c, SLOT_DARK, ACCENT, 1, 14));
                bg.addState(new int[]{android.R.attr.state_selected},
                        rounded(c, ACCENT, ACCENT, 1, 14));
                bg.addState(new int[]{android.R.attr.state_pressed},
                        rounded(c, SLOT_DARK, BORDER, 1, 14));
                bg.addState(new int[]{-android.R.attr.state_enabled},
                        rounded(c, SLOT, BORDER, 1, 14));
                bg.addState(new int[]{}, rounded(c, PANEL, BORDER, 1, 14));
                setBackground(bg);
                int[][] st = new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                };
                setTextColor(new ColorStateList(st, new int[]{ON_ACCENT, MUTED, TEXT}));
                return;
            }

            setShadowLayer(dp(c, 1.5f), dp(c, 1.5f), 0, 0xFF3F3F3F);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_pressed},
                    new BevelDrawable(0xFF6E6E6E, GREEN_BRIGHT, 0xFF9A9A9A, 0xFF2F5A22, dp(c, 2.5f), dp(c, 2.5f)));
            bg.addState(new int[]{android.R.attr.state_selected},
                    new BevelDrawable(0xFFC6C6C6, GREEN_BRIGHT, 0xFFEAFFDF, 0xFF5A7F3A, dp(c, 2.5f), dp(c, 2.5f)));
            bg.addState(new int[]{android.R.attr.state_pressed},
                    new BevelDrawable(0xFF6E6E6E, 0xFF151515, 0xFF9A9A9A, 0xFF3F3F3F, dp(c, 2.5f), dp(c, 2.5f)));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    new BevelDrawable(0xFF9A9A9A, 0xFF151515, 0xFFBDBDBD, 0xFF777777, dp(c, 2.5f), dp(c, 2.5f)));
            bg.addState(new int[]{},
                    new BevelDrawable(0xFF8B8B8B, 0xFF151515, 0xFFC6C6C6, 0xFF555555, dp(c, 2.5f), dp(c, 2.5f)));
            setBackground(bg);

            int[][] states = new int[][]{
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{}
            };
            int[] colors = new int[]{0xFF8A8A8A, 0xFFFFFFFF};
            setTextColor(new ColorStateList(states, colors));
        }

        private static Drawable ore3d(Context c, final int top, final int bottom,
                                      final int bevel, final boolean pressed) {
            return new Drawable() {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final int b = dp(c, 2);
                private final int bev = pressed ? dp(c, 2) : dp(c, 7);
                private final int w = Math.max(1, dp(c, 2));

                @Override public void draw(Canvas canvas) {
                    Rect r = getBounds();
                    if (r.isEmpty()) return;
                    // 凸起部分直接用底色，不加深色外描边
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(bevel);
                    canvas.drawRect(r, p);

                    int faceBottom = r.bottom - bev;
                    p.setShader(new android.graphics.LinearGradient(
                            r.left, r.top, r.left, faceBottom, top, bottom,
                            android.graphics.Shader.TileMode.CLAMP));
                    canvas.drawRect(r.left + b, r.top + b, r.right - b, faceBottom, p);
                    p.setShader(null);

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0x55FFFFFF);
                    canvas.drawRect(r.left + b, r.top + b,
                            r.right - b, r.top + b + Math.max(1, dp(c, 2)), p);

                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1, dp(c, 1)));
                    p.setColor(0xFF4A4A4A);
                    canvas.drawRect(r.left + b, r.top + b,
                            r.right - b, faceBottom, p);
                }

                @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
                @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
                @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
            };
        }

        private static StateListDrawable oreBg(Context c, int fill, int pressed, int disabled) {
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    ore3d(c, 0xFFC8C8C8, 0xFFBEBEBE, 0xFF8A8A8A, true));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    ore3d(c, 0xFFE8E8E8, 0xFFDDDDDD, 0xFFC4C4C4, false));
            bg.addState(new int[]{},
                    ore3d(c, 0xFFEDEDED, 0xFFD4D4D4, 0xFFADADAD, false));
            return bg;
        }

        private static StateListDrawable liquidBg(Context c, int fill, int pressed, int stroke) {
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    liquidFace(c, 0x40FFFFFF, 0x66FFFFFF));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    liquidFace(c, 0x14FFFFFF, 0x33FFFFFF));
            bg.addState(new int[]{}, liquidFace(c, fill, stroke));
            return bg;
        }

        private static Drawable liquidFace(Context c, int fill, int stroke) {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0x55FFFFFF, fill, 0x0DFFFFFF});
            g.setCornerRadius(dp(c, 18));
            g.setStroke(dp(c, 1), stroke);
            return g;
        }

        private static StateListDrawable minimalBg(Context c, int fill, int border,
                                                   int pressed, int text) {
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    rounded(c, pressed, border, 1, 14));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    rounded(c, SLOT, border, 1, 14));
            bg.addState(new int[]{}, rounded(c, fill, border, 1, 14));
            return bg;
        }

        private static ColorStateList textStates(int normal, int disabled) {
            int[][] states = new int[][]{
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{}
            };
            int[] colors = new int[]{disabled, normal};
            return new ColorStateList(states, colors);
        }

        /**
         * 苔石主题按钮样式：0=浅灰(可选中) 1=绿 2=红 3=紫。
         * 三态由 OreBlock 的状态驱动：默认 / selected / pressed。
         */
        /** 苔石下按下时整体下沉 2px，配合斜面翻转形成“按进去”的手感。 */
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (ORE) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    setTranslationY(dp(getContext(), 0.7f));
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    setTranslationY(0);
                }
            }
            return super.onTouchEvent(event);
        }

        /** 苔石下按下时整体下沉 2px，配合斜面翻转形成“按进去”的手感。 */

        private void oreStyle(int kind) {
            Context c = getContext();
            int idle, sel, txtIdle;
            if (kind == 1) {
                idle = ORE_GREEN;
                sel = ORE_GREEN;
                txtIdle = 0xFFFFFFFF;
            } else if (kind == 2) {
                idle = ORE_RED;
                sel = ORE_RED;
                txtIdle = 0xFFFFFFFF;
            } else if (kind == 3) {
                idle = ORE_PURPLE;
                sel = ORE_PURPLE;
                txtIdle = 0xFFFFFFFF;
            } else {
                idle = ORE_LIGHT;
                sel = ORE_GREEN;
                txtIdle = ORE_INK;
            }
            setBackground(oreBtn(c, idle, sel, kind == 0));
            int[][] st = new int[][]{
                    new int[]{android.R.attr.state_selected},
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{}
            };
            setTextColor(new ColorStateList(st, new int[]{
                    kind == 0 ? 0xFFFFFFFF : txtIdle,
                    0xFF9A9B9D,
                    txtIdle
            }));
        }

        /** 简约主题下的主按钮：强调色。 */
        public void setPrimaryStyle() {
            if (LIQUID) {
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_pressed},
                        liquidFace(getContext(), 0x6634D399, 0xAAFFFFFF));
                bg.addState(new int[]{},
                        liquidFace(getContext(), 0x5534D399, 0xAAFFFFFF));
                setBackground(bg);
                setTextColor(0xFFFFFFFF);
                return;
            }
            if (ORE) {
                oreStyle(1);
                return;
            }
            if (!MINIMAL) return;
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    rounded(getContext(), SLOT_DARK, ACCENT, 1, 14));
            bg.addState(new int[]{}, rounded(getContext(), ACCENT, ACCENT, 1, 14));
            setBackground(bg);
            setTextColor(ON_ACCENT);
        }

        /** 亮红色危险按钮：用于清除数据等。 */
        public void setBrightDangerStyle() {
            if (LIQUID) {
                setBackground(liquidBg(getContext(), 0x55FF5A5A, 0x77FF6B6B, 0xAAFFFFFF));
                setTextColor(0xFFFFFFFF);
                return;
            }
            if (ORE) {
                oreStyle(2);
                return;
            }
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    rounded(getContext(), 0xFFC62828, 0xFF7F1111, 2, 0));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    rounded(getContext(), 0xFFE57373, 0xFF7F1111, 2, 0));
            bg.addState(new int[]{}, rounded(getContext(), 0xFFE53935, 0xFF7F1111, 2, 0));
            setBackground(bg);
            setTextColor(0xFFFFFFFF);
        }

        /** 危险操作按钮：红色背景，用于修复/删除等。 */
        public void setDangerStyle() {
            if (LIQUID) {
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_pressed},
                        liquidFace(getContext(), 0x66FF6B6B, 0xAAFFFFFF));
                bg.addState(new int[]{},
                        liquidFace(getContext(), 0x44FF6B6B, 0x88FFFFFF));
                setBackground(bg);
                setTextColor(0xFFFFFFFF);
                return;
            }
            if (ORE) {
                oreStyle(2);
                return;
            }
            if (MINIMAL) {
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_pressed},
                        rounded(getContext(), 0xFF7F1D1D, RED, 1, 14));
                bg.addState(new int[]{},
                        rounded(getContext(), RED, RED, 1, 14));
                setBackground(bg);
                setTextColor(0xFFFFFFFF);
                return;
            }
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed},
                    new BevelDrawable(0xFF7A1F1F, 0xFF220000, 0xFFD85A5A, 0xFF3A0000,
                            dp(getContext(), 2.5f), dp(getContext(), 2.5f)));
            bg.addState(new int[]{-android.R.attr.state_enabled},
                    new BevelDrawable(0xFF8A6A6A, 0xFF220000, 0xFFC09090, 0xFF5A3030,
                            dp(getContext(), 2.5f), dp(getContext(), 2.5f)));
            bg.addState(new int[]{},
                    new BevelDrawable(0xFFB43A3A, 0xFF220000, 0xFFE06060, 0xFF3A0000,
                            dp(getContext(), 2.5f), dp(getContext(), 2.5f)));
            setBackground(bg);
            setTextColor(0xFFFFF2F2);
        }
    }

    /** Minecraft 风格滑块开关。 */
    /**
     * 分段选择控件（Ore UI 风格）：一排按钮，选中项为绿色 + 白色下划线。
     * 非苔石主题下退化为旧的「（当前）」后缀写法，保持原有观感。
     */
    public static class McSegment extends LinearLayout {
        public interface OnPick {
            void onPick(int index);
        }

        private final java.util.List<Button> buttons = new java.util.ArrayList<>();
        private final boolean ore;
        private int selected;
        private OnPick pick;

        public McSegment(Context c, String[] labels) {
            super(c);
            ore = ORE;
            setOrientation(HORIZONTAL);
            int pad = ore ? dp(c, 1) : 0;
            setPadding(pad, pad, pad, pad);
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                Button b = new McButton(c, labels[i]);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                LayoutParams lp = new LayoutParams(0, dp(c, ore ? 46 : 44), 1f);
                if (i > 0) lp.leftMargin = dp(c, ore ? 3 : 4);
                addView(b, lp);
                b.setOnClickListener(v -> {
                    setSelectedIndex(idx);
                    if (pick != null) pick.onPick(idx);
                });
                buttons.add(b);
            }
            setSelectedIndex(0);
        }

        public void setOnPick(OnPick p) {
            pick = p;
        }

        public int getSelectedIndex() {
            return selected;
        }

        /** 只改选中态；非苔石主题用文字后缀表示。 */
        public void setSelectedIndex(int index) {
            if (index < 0 || index >= buttons.size()) return;
            selected = index;
            for (int i = 0; i < buttons.size(); i++) {
                Button b = buttons.get(i);
                if (ore) {
                    b.setSelected(i == index);
                } else {
                    String label = String.valueOf(b.getTag() == null ? "" : b.getTag());
                    if (label.isEmpty()) {
                        label = labelOf(b);
                        b.setTag(label);
                    }
                    b.setText(i == index ? label + I18n.sp() + I18n.t("（当前）") : label);
                }
            }
        }

        private static String labelOf(Button b) {
            String t = String.valueOf(b.getText());
            String suffix = I18n.sp() + I18n.t("（当前）");
            return t.endsWith(suffix) ? t.substring(0, t.length() - suffix.length()) : t;
        }
    }

    public static class McSwitch extends View {
        public interface OnCheckedChangeListener {
            void onCheckedChanged(McSwitch view, boolean checked);
        }

        private boolean checked;
        private boolean enabled = true;
        private OnCheckedChangeListener listener;
        private final Paint paint = new Paint();

        public McSwitch(Context c) {
            super(c);
            setContentDescription(I18n.t("开关"));
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
            invalidate();
        }

        public boolean isChecked() {
            return checked;
        }

        public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
            listener = l;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            super.setEnabled(enabled);
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!enabled) return false;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                checked = !checked;
                invalidate();
                performClick();
                if (listener != null) listener.onCheckedChanged(this, checked);
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (LIQUID) {
                int th = dp(getContext(), 32);
                int top = (h - th) / 2;
                paint.setColor(0x2EFFFFFF);
                canvas.drawRoundRect(0, top, w, top + th, th, th, paint);
                paint.setColor(0x66FFFFFF);
                canvas.drawRoundRect(0, top, w, top + th, th, th, paint);
                paint.setColor(0x2EFFFFFF);
                canvas.drawRoundRect(2, top + 2, w - 2, top + th - 2, th, th, paint);
                paint.setColor(checked ? 0xCC34D399 : 0xF2FFFFFF);
                int kw = dp(getContext(), 26);
                int left = checked ? w - kw - 4 : 4;
                canvas.drawRoundRect(left, top + 3, left + kw, top + th - 3,
                        kw, kw, paint);
                return;
            }
            if (ORE) {
                Context c = getContext();
                int bw = Math.max(2, dp(c, 1.3f));
                int hi = Math.max(2, dp(c, 1.6f));
                int lo = Math.max(3, dp(c, 3.0f));
                int knobW = Math.round(w * 0.46f);
                int trackRight = w - knobW - bw;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ORE_BORDER);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setColor(ORE_SLOT);
                canvas.drawRect(bw, bw, w - bw, h - bw, paint);
                paint.setColor(darken(ORE_SLOT, 0.40f));
                canvas.drawRect(bw, bw, w - bw, bw + hi, paint);
                if (checked) {
                    paint.setColor(ORE_GREEN);
                    canvas.drawRect(bw, bw, trackRight, h - bw, paint);
                    paint.setColor(ORE_GREEN_HI);
                    canvas.drawRect(bw, bw, trackRight, bw + hi, paint);
                    paint.setColor(ORE_GREEN_LO);
                    canvas.drawRect(bw, h - bw - lo, trackRight, h - bw, paint);
                    paint.setColor(0xFFFFFFFF);
                    int barW = Math.max(2, dp(c, 2.4f));
                    int cx = (bw + trackRight) / 2;
                    canvas.drawRect(cx, h / 2f - dp(c, 4), cx + barW, h / 2f + dp(c, 4), paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(c, 1.3f));
                    paint.setColor(0xFF8B8C8E);
                    float rr = Math.min(h, w - knobW) * 0.26f;
                    float cx = (trackRight + w) / 2f;
                    canvas.drawCircle(cx, h / 2f, rr, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                int knobX = checked ? w - knobW - bw : bw;
                paint.setColor(ORE_BORDER);
                canvas.drawRect(knobX, bw, knobX + knobW, h - bw, paint);
                paint.setColor(enabled ? ORE_LIGHT : ORE_DIS);
                canvas.drawRect(knobX + bw, bw + bw, knobX + knobW - bw, h - bw - bw, paint);
                paint.setColor(ORE_LIGHT_HI);
                canvas.drawRect(knobX + bw, bw + bw, knobX + knobW - bw, bw + bw + hi, paint);
                paint.setColor(ORE_LIGHT_LO);
                canvas.drawRect(knobX + bw, h - bw - bw - lo, knobX + knobW - bw, h - bw - bw, paint);
                return;
            }
            if (MINIMAL) {
                float r = dp(getContext(), 14);
                float top = (h - dp(getContext(), 28)) / 2f;
                float bottom = top + dp(getContext(), 28);
                paint.setAntiAlias(true);
                paint.setColor(enabled ? (checked ? ACCENT : SLOT_DARK) : SLOT);
                canvas.drawRoundRect(0, top, w, bottom, r, r, paint);
                float knob = dp(getContext(), 22);
                float left = checked ? w - knob - dp(getContext(), 3) : dp(getContext(), 3);
                paint.setColor(enabled ? PANEL : SLOT);
                canvas.drawRoundRect(left, top + dp(getContext(), 3),
                        left + knob, bottom - dp(getContext(), 3), knob / 2, knob / 2, paint);
                return;
            }
            int trackH = dp(getContext(), 14);
            int top = (h - trackH) / 2;
            int bottom = top + trackH;
            int b = dp(getContext(), 2);

            // 轨道外框
            paint.setColor(0xFF151515);
            canvas.drawRect(0, top, w, bottom, paint);
            // 轨道主体
            paint.setColor(enabled ? (checked ? 0xFF3F6B3F : 0xFF6E6E6E) : 0xFF8A8A8A);
            canvas.drawRect(b, top + b, w - b, bottom - b, paint);
            // 顶部高光
            paint.setColor(checked ? 0xFF8FD07A : 0xFF9A9A9A);
            canvas.drawRect(b, top + b, w - b, top + b + dp(getContext(), 2), paint);
            // 底部阴影
            paint.setColor(0x55000000);
            canvas.drawRect(b, bottom - b - dp(getContext(), 2), w - b, bottom - b, paint);

            int knobW = dp(getContext(), 22);
            int knobH = dp(getContext(), 20);
            int kTop = (h - knobH) / 2;
            int kLeft = checked ? w - knobW - b : b;
            paint.setColor(0xFF151515);
            canvas.drawRect(kLeft, kTop, kLeft + knobW, kTop + knobH, paint);
            paint.setColor(enabled ? (checked ? 0xFFB8FFB0 : 0xFFD0D0D0) : 0xFFB0B0B0);
            canvas.drawRect(kLeft + b, kTop + b, kLeft + knobW - b, kTop + knobH - b, paint);
            paint.setColor(0x33FFFFFF);
            canvas.drawRect(kLeft + b, kTop + b, kLeft + knobW - b, kTop + b + dp(getContext(), 2), paint);
            paint.setColor(0x44000000);
            canvas.drawRect(kLeft + b, kTop + knobH - b - dp(getContext(), 2),
                    kLeft + knobW - b, kTop + knobH - b, paint);
        }
    }

    public static void styleInput(android.widget.EditText input) {
        if (!MINIMAL && !ORE && !LIQUID) return;
        Context c = input.getContext();
        input.setTextColor(PANEL_TEXT);
        input.setHintTextColor(PANEL_MUTED);
        input.setBackground(terminalDrawable(c));
        input.setPadding(dp(c, 9), dp(c, 8), dp(c, 9), dp(c, 8));
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    /** 与控制台一致的输入框样式。 */
    public static android.widget.EditText terminalInput(Context c, String hint) {
        android.widget.EditText input = new android.widget.EditText(c);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (MINIMAL || ORE || LIQUID) {
            input.setTextColor(PANEL_TEXT);
            input.setHintTextColor(PANEL_MUTED);
            input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } else {
            input.setTextColor(0xFFB8FFB0);
            input.setHintTextColor(0xFF6A8A6A);
            input.setTypeface(mono());
        }
        input.setHint(I18n.t(hint));
        input.setBackground(terminalDrawable(c));
        input.setPadding(dp(c, 9), dp(c, 8), dp(c, 9), dp(c, 8));
        return input;
    }

    /** 分段像素进度条。 */
    public static class SegmentBar extends View {
        private int total = 10;
        private int filled = 0;
        private int fillColor = GREEN_BRIGHT;

        public SegmentBar(Context c) {
            super(c);
        }

        public void set(int total, int filled) {
            this.total = Math.max(1, total);
            this.filled = Math.max(0, Math.min(total, filled));
            invalidate();
        }

        public void setColor(int color) {
            this.fillColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (LIQUID) {
                float gap = dp(getContext(), 3f);
                float w2 = (getWidth() - gap * (total - 1)) / total;
                Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (int i = 0; i < total; i++) {
                    p2.setColor(i < filled ? 0xCC6EE7B7 : 0x24FFFFFF);
                    canvas.drawRoundRect(i * (w2 + gap), 0, i * (w2 + gap) + w2,
                            getHeight(), 4, 4, p2);
                }
                return;
            }
            if (ORE) {
                float gap = dp(getContext(), 2f);
                float w = (getWidth() - gap * (total - 1)) / total;
                Paint p = new Paint();
                for (int i = 0; i < total; i++) {
                    p.setColor(i < filled ? fillColor : 0xFF555555);
                    canvas.drawRect(i * (w + gap), 0, i * (w + gap) + w, getHeight(), p);
                }
                return;
            }
            float gap = dp(getContext(), MINIMAL ? 2f : 1.2f);
            float w = (getWidth() - gap * (total - 1)) / total;
            Paint p = new Paint();
            float radius = MINIMAL ? dp(getContext(), 3) : 0;
            int track = MINIMAL ? BORDER : 0xFF7B7B7B;
            for (int i = 0; i < total; i++) {
                float l = i * (w + gap);
                p.setColor(i < filled ? fillColor : track);
                if (MINIMAL) {
                    canvas.drawRoundRect(l, 0, l + w, getHeight(), radius, radius, p);
                } else {
                    canvas.drawRect(l, 0, l + w, getHeight(), p);
                    p.setColor(0x33000000);
                    canvas.drawRect(l, getHeight() - dp(getContext(), 2), l + w, getHeight(), p);
                }
            }
        }
    }

    /** 12x12 像素图标。 */
    public static class PixelIconView extends View {
        public static final int HOME = 0;
        public static final int CONSOLE = 1;
        public static final int AI = 2;
        public static final int SETTINGS = 3;
        public static final int CPU = 4;
        public static final int MEM = 5;
        public static final int NET = 6;
        public static final int TPS = 7;

        private static final String[][] PATTERNS = new String[][]{
                { // HOME
                        ".....##.....",
                        "....####....",
                        "...######...",
                        "..########..",
                        ".##########.",
                        "############",
                        "...######...",
                        "...#....#...",
                        "...#....#...",
                        "...#....#...",
                        "...#....#...",
                        "............"
                },
                { // CONSOLE
                        "............",
                        ".##########.",
                        ".#........#.",
                        ".#.>......#.",
                        ".#..>.....#.",
                        ".#...>....#.",
                        ".#........#.",
                        ".#.....___#.",
                        ".##########.",
                        "............",
                        "............",
                        "............"
                },
                { // AI
                        ".....##.....",
                        ".....##.....",
                        "....####....",
                        "..########..",
                        ".##########.",
                        "############",
                        ".##########.",
                        "..########..",
                        "....####....",
                        ".....##.....",
                        ".....##.....",
                        "............"
                },
                { // SETTINGS
                        "....####....",
                        "...#....#...",
                        "..#..##..#..",
                        ".#..#..#..#.",
                        "#..#..#..#..",
                        "#..####..#..",
                        "#........#..",
                        ".#..##..#...",
                        "..#....#....",
                        "...####.....",
                        "............",
                        "............"
                },
                { // CPU
                        "............",
                        "..#..##..#..",
                        "..#.####.#..",
                        "..########..",
                        "..#......#..",
                        "..#.####.#..",
                        "..#......#..",
                        "..########..",
                        "..#.####.#..",
                        "..#..##..#..",
                        "............",
                        "............"
                },
                { // MEM
                        "............",
                        "............",
                        ".##########.",
                        ".#.#.#.#.#..",
                        ".#........#.",
                        ".#.#.#.#.#..",
                        ".#........#.",
                        ".##########.",
                        "..#......#..",
                        "..#......#..",
                        "............",
                        "............"
                },
                { // NET
                        ".....##.....",
                        "....####....",
                        "...######...",
                        ".....##.....",
                        ".....##.....",
                        ".....##.....",
                        ".....##.....",
                        "...######...",
                        "....####....",
                        ".....##.....",
                        "............",
                        "............"
                },
                { // TPS
                        "....####....",
                        "..##....##..",
                        ".#........#.",
                        "#..........#",
                        "#....##....#",
                        "#...####...#",
                        "#....##....#",
                        ".#........#.",
                        "..##....##..",
                        "....####....",
                        "............",
                        "............"
                }
        };

        private int kind = HOME;
        private int color = 0xFF1B1B1B;

        public PixelIconView(Context c) {
            super(c);
        }

        public void set(int kind, int color) {
            this.kind = kind;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            String[] p = PATTERNS[kind];
            float cell = Math.min(getWidth(), getHeight()) / 12f;
            float ox = (getWidth() - cell * 12) / 2f;
            float oy = (getHeight() - cell * 12) / 2f;
            Paint paint = new Paint();
            paint.setColor(color);
            for (int y = 0; y < 12; y++) {
                String row = p[y];
                for (int x = 0; x < 12; x++) {
                    if (row.charAt(x) == '#') {
                        canvas.drawRect(ox + x * cell, oy + y * cell,
                                ox + (x + 1) * cell, oy + (y + 1) * cell, paint);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- layouts

    public static LinearLayout panel(Context c, String title) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(panelDrawable(c));
        p.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 12));
        if (title != null) {
            TextView t = text(c, title, 13, PANEL_TEXT, true);
            t.setLetterSpacing(0.12f);
            t.setPadding(0, 0, 0, dp(c, 8));
            p.addView(t);
        }
        return p;
    }

    public static LinearLayout slot(Context c) {
        LinearLayout s = new LinearLayout(c);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setBackground(slotDrawable(c));
        s.setPadding(dp(c, 9), dp(c, 8), dp(c, 9), dp(c, 8));
        return s;
    }

    public static LinearLayout infoRow(Context c, String label, TextView value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(slotDrawable(c));
        row.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        TextView k = text(c, label, 12, PANEL_MUTED, true);
        row.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    public static TextView valueText(Context c, String s) {
        TextView v = text(c, s, 13, 0xFF111111, true);
        v.setGravity(Gravity.END);
        return v;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return p;
    }

    public static LinearLayout.LayoutParams weight(Context c, float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams box(Context c, int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(dp(c, widthDp), dp(c, heightDp));
    }

    public static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(v.getContext(), l), dp(v.getContext(), t),
                    dp(v.getContext(), r), dp(v.getContext(), b));
        }
    }
}
