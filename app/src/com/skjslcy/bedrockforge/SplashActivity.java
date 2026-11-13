package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 独立启动页：先于主界面显示，展示 Logo 与加载进度。 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.SplashTheme);
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF1A120B);
        getWindow().setNavigationBarColor(0xFF1A120B);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new MinecraftUi.DirtDrawable(this));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        PixelLogoView logo = new PixelLogoView(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin = dp(6);
        box.addView(logo, llp);

        TextView tag = MinecraftUi.text(this, "BEDROCK FORGE", 8, 0xFFD8C7AE, true);
        tag.setLetterSpacing(0.28f);
        tag.setShadowLayer(dp(1.5f), dp(1.5f), 0, 0xFF1A1A1A);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(26);
        box.addView(tag, tlp);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER;
        root.addView(box, flp);
        setContentView(root);

        handler.postDelayed(this::goMain, 200);
    }

    private void goMain() {
        if (started) return;
        started = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(float v) {
        return MinecraftUi.dp(this, v);
    }
}
