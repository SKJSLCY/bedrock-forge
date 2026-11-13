package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements DeploySectionView.Host {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private static final int REQ_PICK_FILE = 200;
    private DeploySectionView.PickCallback pendingPick;
    private int pendingPickType = -1;
    private PermissionView activePermissionView;

    private FrameLayout content;
    private Runnable splashFinish;
    private boolean agreementFlowStarted;
    private long splashStart;
    private final View[] pages = new View[4];
    private final LinearLayout[] navSlots = new LinearLayout[4];
    private final MinecraftUi.PixelIconView[] navIcons = new MinecraftUi.PixelIconView[4];
    private final TextView[] navLabels = new TextView[4];

    private View managePage;
    private FrameLayout manageHost;
    private LinearLayout homeCol;
    private LinearLayout homeDeployGuide;
    private TextView homeGuideText;
    private Button homeGuideRuntimeBtn;
    private Button homeGuideServerBtn;
    private int lastDeployVersion = 0;    private View manageList;
    private TextView manageTitle;
    private boolean manageSectionOpen = false;
    private final Button[] manageButtons = new Button[9];
    private volatile boolean canRuntime = false;
    private volatile boolean canServer = false;

    private volatile boolean gatingLoaded = false;

    private static final String[] MANAGE_LABELS = {
            "服务端", "运行环境", "服务器设置", "addons管理",
            "存档管理", "玩家管理", "备份"
    };
    private static final String[] MANAGE_DESCS = {
            "查看服务端运行状态、版本与进程",
            "查看 Box64、rootfs、BDS 与 Shizuku 环境状态",
            "汉化修改 server.properties 配置",
            "管理行为包与资源包",
            "导入、导出和切换世界存档",
            "玩家列表、白名单与权限",
            "一键备份与恢复世界"
    };

    private View lampDot;
    private TextView oreTitle;
    private TextView lampText;
    private TextView runStateValue;
    private TextView playersValue;
    private TextView serverTypeValue;
    private TextView serverVersionValue;
    private TextView gameVersionValue;
    private TextView ipValue;
    private TextView worldValue;
    private TextView onlineModeValue;
    private TextView uptimeValue;
    private String liveLevel = "";
    private String liveGameVersion = "";
    private int liveProtocol = 0;
    private TextView cpuValue;
    private TextView memValue;
    private TextView netValue;
    private TextView tpsValue;
    private MinecraftUi.SegmentBar cpuBar;
    private MinecraftUi.SegmentBar memBar;
    private MinecraftUi.SegmentBar tpsBar;
    private Button startBtn;
    private Button stopBtn;
    private Button restartBtn;
    private TextView shizukuNote;

    private TextView consoleText;
    private EditText consoleInput;
    private ScrollView consoleScroll;
    private int lastLogCount = -1;
    private long lastLogUpdateMs = 0L;

    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private EditText aiInput;
    private final LinearLayout[] aiTabs = new LinearLayout[2];

    private TextView settingsShizuku;
    private TextView settingsEnv;
    private TextView settingsIp;

    private int currentPage = 0;
    private int port = 19134;
    private String worldName = "Nether62";
    private volatile boolean onlineMode = true;
    private boolean shizukuPermissionRequested;
    private volatile String lanIp = null;
    private volatile boolean serverReady = false;
    private long lastLogTailAt;
    private long lastListAt;
    private volatile boolean logTailBusy;
    private boolean lastServerRunning;
    private long lastFetch = 0L;
    private long nextTpsQuery = 0L;
    private long pendingTpsAt = 0L;
    private long lastGametimeTicks = -1L;
    private long lastGametimeAt = 0L;
    private int tps = 0;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            DeviceMonitor.get().sample(MainActivity.this);
            sampleTps(now);
            updateState();
            updateLogs();
            int deployVersion = DeployState.completionVersion();
            if (deployVersion != lastDeployVersion) {
                lastDeployVersion = deployVersion;
                refreshHomeDeployState();
                refreshManageGating();
            }
            if (now - lastFetch > 3000) {
                lastFetch = now;
                fetchStatus();
            }
            boolean nowRunning = ServerManager.get().isRunning();
            if (nowRunning != lastServerRunning) {
                ScreenOnManager.apply(MainActivity.this);
            }
            if (nowRunning && !lastServerRunning) {
                io.execute(new Runnable() {
                    @Override public void run() {
                        PlayerMapping.refreshBaseline(MainActivity.this);
                    }
                });
            }
            if (lastServerRunning && !nowRunning) {
                io.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            ServerLogTail.tail(MainActivity.this);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
            lastServerRunning = nowRunning;
            final String bindName = PlayerTracker.pollPendingBind();
            if (bindName != null) {
                io.execute(new Runnable() {
                    @Override public void run() {
                        PlayerMapping.learn(MainActivity.this, bindName);
                    }
                });
            }
            if (now - lastListAt > 15000) {
                lastListAt = now;
                if (ServerManager.get().isRunning()) {
                    ServerManager.get().send("list");
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        if (!MinecraftUi.MINIMAL && !MinecraftUi.ORE) {
            getWindow().setStatusBarColor(0xFF1A120B);
            getWindow().setNavigationBarColor(0xFF1A120B);
        }
        buildUi();
        if (savedInstanceState == null) showSplash();
        AppLog.add("App 启动");
        loadConfig();
        requestNotificationPermission();
        showPage(0);
        handler.post(poll);
        if (savedInstanceState != null) {
            handler.postDelayed(new Runnable() {
                @Override public void run() { maybeShowAgreement(); }
            }, 400);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activePermissionView != null) activePermissionView.refresh();
        ScreenOnManager.apply(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 992) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                toast("通知权限已允许");
            } else {
                toast("通知未授权，正在打开系统设置");
                if (!PermissionView.showNotificationSettings(this)) {
                    toast("无法打开通知设置，请手动进入系统设置");
                }
            }
        }
        if (activePermissionView != null) activePermissionView.refresh();
    }

    @Override
    public void pickFile(final DeploySectionView.PickCallback callback) {
        pendingPick = callback;
        try {
            String title = manageTitle == null ? "" : manageTitle.getText().toString();
            pendingPickType = "服务端".equals(title) ? DeployManager.TYPE_SERVER
                    : ("运行环境".equals(title) ? DeployManager.TYPE_RUNTIME : -1);
        } catch (Throwable ignored) {
            pendingPickType = -1;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_FILE);
        } catch (Throwable t) {
            pendingPick = null;
            toast("无法打开文件选择器：" + t.getMessage());
            callback.onCancel();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AiAssistantView.REQ_IMAGE || requestCode == AiAssistantView.REQ_FILE) {
            if (aiAssistantView != null) {
                aiAssistantView.onActivityResult(requestCode, resultCode, data);
            }
            return;
        }
        if (requestCode != REQ_PICK_FILE) return;
        final DeploySectionView.PickCallback cb = pendingPick;
        pendingPick = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (cb != null) cb.onCancel();
            return;
        }
        final Uri uri = data.getData();
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String name = displayName(uri);
                    int dot = name.lastIndexOf('.');
                    String ext = dot >= 0 ? name.substring(dot) : ".bin";
                    lastPickedName = name;
                    File out = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ext);
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new java.io.IOException("无法读取文件");
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.close();
                    in.close();
                    final File file = out;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cb != null) cb.onPicked(file);
                            else orphanImport(file, pendingPickType);
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            toast("导入文件失败：" + e.getMessage());
                            if (cb != null) cb.onCancel();
                        }
                    });
                }
            }
        });
    }

    static volatile String lastPickedName = "";

    private void orphanImport(final File file, int type) {
        if (type != DeployManager.TYPE_SERVER) {
            toast("文件已复制，但页面状态丢失，请重新进入对应页面导入");
            return;
        }
        String name = file.getName();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)").matcher(name);
        if (m.find()) {
            startOrphanServerImport(file, m.group(1));
            return;
        }
        final android.widget.EditText input = new android.widget.EditText(this);
        MinecraftUi.styleInput(input);
        input.setHint("1.26.63.1");
        new android.app.AlertDialog.Builder(this)
                .setTitle("导入服务端版本")
                .setMessage("文件名没有版本号，请输入完整版本号，例如 1.26.63.1")
                .setView(input)
                .setPositiveButton("导入", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.matches("\\d+\\.\\d+\\.\\d+(\\.\\d+)?")) {
                        toast("版本号格式不正确");
                        return;
                    }
                    startOrphanServerImport(file, v);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startOrphanServerImport(final File file, final String version) {
        toast("开始导入服务端 " + version);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    DeployManager.installServer(MainActivity.this, file, "设备导入", version,
                            new DeployManager.Progress() {
                                @Override public void update(int percent, String message) {
                                }
                            });
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            toast("导入完成：" + version);
                            showManagePage();
                            openManageSection(0);
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            toast("导入失败：" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private String displayName(Uri uri) {
        String name = null;
        try {
            Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null) {
                if (c.moveToFirst()) name = c.getString(0);
                c.close();
            }
        } catch (Throwable ignored) {
        }
        if (name == null || name.isEmpty()) name = "import.bin";
        return name;
    }

    @Override
    public void onBackPressed() {
        if (currentPage == 4) {
            if (manageSectionOpen) {
                showManageList();
            } else {
                showPage(0);
            }
        } else if (currentPage != 0) {
            showPage(0);
        } else {
            super.onBackPressed();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
        }
    }

    private void maybeShowAgreement() {
        if (agreementFlowStarted) return;
        agreementFlowStarted = true;
        if (AgreementDialog.accepted(this)) {
            maybeOnboard();
            return;
        }
        AgreementDialog.show(this, true, new Runnable() {
            @Override public void run() {
                maybeOnboard();
            }
        });
    }

    private void maybeOnboard() {
        if (getSharedPreferences("app", MODE_PRIVATE).getBoolean("onboarded", false)) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(this, "欢迎使用 基岩工坊");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView text = MinecraftUi.text(this,
                "运行 x86_64 服务端需要 Shizuku 以 shell 身份启动 Box64。\n\n"
                        + "1. 安装 Shizuku\n"
                        + "2. 开发者选项 → 无线调试 → 配对并启动 Shizuku\n"
                        + "3. 回到本应用授予 Shizuku 权限\n\n"
                        + "通知权限用于部署进度与前台保活。",
                10, MinecraftUi.PANEL_TEXT, false);
        text.setPadding(0, dp(2), 0, dp(8));
        panel.addView(text);

        Button guide = new MinecraftUi.McButton(this, "查看 Shizuku 启动指引");
        panel.addView(guide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        guide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PermissionView.showShizukuGuide(MainActivity.this); }
        });

        Button perm = new MinecraftUi.McButton(this, "打开权限管理");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        plp.topMargin = dp(5);
        panel.addView(perm, plp);
        perm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                showPermissionsDialog();
            }
        });

        Button done = new MinecraftUi.McButton(this, "开始使用");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        dlp.topMargin = dp(6);
        panel.addView(done, dlp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply();
                dialog.dismiss();
            }
        });

        dialog.setContentView(wrap);
        dialog.setCancelable(false);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void loadConfig() {
        try {
            Map<String, String> p = EnvManager.readProperties(this);
            String ps = p.get("server-port");
            if (ps != null) port = Integer.parseInt(ps.trim());
            String ln = p.get("level-name");
            if (ln != null && !ln.isEmpty()) worldName = ln;
        } catch (Throwable ignored) {
        }
        ServerManager.get().setPort(port);
    }

    // ---------------------------------------------------------------- UI

    private void showSplash() {
        splashStart = System.currentTimeMillis();
        final FrameLayout splash = new FrameLayout(this);
        splash.setBackground(new MinecraftUi.DirtDrawable(this));
        splash.setClickable(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

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

        final MinecraftUi.SegmentBar bar = new MinecraftUi.SegmentBar(this);
        bar.set(10, 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(180), dp(10));
        box.addView(bar, blp);

        final TextView status = MinecraftUi.text(this, "正在准备运行环境…", 9,
                0xFFD8C7AE, false);
        status.setPadding(0, dp(10), 0, 0);
        box.addView(status);

        String[] tips = {
                "提示：运行服务端需要 Shizuku",
                "提示：备份可在“备份”页面回滚",
                "提示：AI 功能需要联网与 API Key",
                "提示：首次部署需要编译 Box64"
        };
        TextView tip = MinecraftUi.text(this,
                tips[(int) (Math.random() * tips.length)], 8.5f,
                MinecraftUi.PANEL_MUTED, false);
        tip.setPadding(0, dp(4), 0, 0);
        box.addView(tip);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = android.view.Gravity.CENTER;
        splash.addView(box, flp);
        addContentView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final android.animation.ValueAnimator anim =
                android.animation.ValueAnimator.ofInt(0, 10);
        anim.setDuration(2200);
        anim.addUpdateListener(a -> {
            int v = (Integer) a.getAnimatedValue();
            bar.set(10, v);
            if (v >= 8) status.setText("准备界面…");
            else if (v >= 6) status.setText("读取服务端配置…");
            else if (v >= 3) status.setText("检查运行环境…");
            else status.setText("正在准备运行环境…");
        });
        anim.start();
        splashFinish = () -> {
            status.setText("就绪");
            splash.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                ViewGroup parent = (ViewGroup) splash.getParent();
                if (parent != null) parent.removeView(splash);
                maybeShowAgreement();
            }).start();
        };
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new MinecraftUi.DirtDrawable(this));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        if (MinecraftUi.ORE) {
            shell.setPadding(0, 0, 0, 0);
        } else {
            shell.setPadding(dp(12), dp(10), dp(12), dp(8));
        }
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        if (MinecraftUi.ORE) {
            content.setPadding(dp(12), 0, dp(12), 0);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cp.topMargin = MinecraftUi.ORE ? 0 : dp(10);
        shell.addView(content, cp);

        shell.addView(buildHotbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        // 先把启动画面画出来，再构建各页面，避免冷启动时空白泥土停留过久。
        content.post(new Runnable() {
            @Override public void run() { buildPagesOnce(); }
        });
    }

    private void buildPagesOnce() {
        if (pages[0] != null) return;
        pages[0] = buildHome();
        pages[1] = buildConsole();
        pages[2] = buildAi();
        pages[3] = buildSettings();
        for (View p : pages) {
            p.setVisibility(View.GONE);
            content.addView(p, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        managePage = buildManagePage();
        managePage.setVisibility(View.GONE);
        content.addView(managePage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showPage(0);
        long elapsed = System.currentTimeMillis() - splashStart;
        long wait = Math.max(120, 700 - elapsed);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (splashFinish != null) {
                    splashFinish.run();
                    splashFinish = null;
                }
            }
        }, wait);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);

        if (MinecraftUi.ORE) {
            LinearLayout oreBar = new LinearLayout(this);
            oreBar.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            android.graphics.drawable.GradientDrawable bodyBg =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{0xFFF2F2F2, 0xFFE5E5E5});
            row.setBackground(bodyBg);
            row.setPadding(dp(20), dp(3), dp(20), dp(3));

            TextView brand = new TextView(this);
            brand.setText("基岩工坊");
            brand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
            brand.setTextColor(0xFF1B1B1B);
            brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            row.addView(brand, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            oreBar.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View lipTop = new View(this);
            lipTop.setBackgroundColor(0xFFDEDEDE);
            oreBar.addView(lipTop, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            View lip = new View(this);
            lip.setBackgroundColor(0xFFC6C6C6);
            oreBar.addView(lip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
            View lipBottom = new View(this);
            lipBottom.setBackgroundColor(0xFF9E9E9E);
            oreBar.addView(lipBottom, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            return oreBar;
        }

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (MinecraftUi.MINIMAL) {
            TextView title = new TextView(this);
            title.setText("基岩工坊");
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30);
            title.setTextColor(MinecraftUi.PANEL_TEXT);
            title.setTypeface(android.graphics.Typeface.create("serif",
                    android.graphics.Typeface.BOLD));
            title.setLetterSpacing(0.08f);
            title.setIncludeFontPadding(false);
            left.addView(title);
            TextView mtag = MinecraftUi.text(this, "BEDROCK FORGE", 8,
                    MinecraftUi.PANEL_MUTED, true);
            mtag.setLetterSpacing(0.28f);
            mtag.setPadding(0, dp(4), 0, 0);
            left.addView(mtag);

            LinearLayout mlamp = new LinearLayout(this);
            mlamp.setOrientation(LinearLayout.HORIZONTAL);
            mlamp.setGravity(Gravity.CENTER_VERTICAL);
            mlamp.setPadding(dp(12), dp(7), dp(14), dp(7));
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setColor(MinecraftUi.PANEL);
            g.setCornerRadius(dp(999));
            g.setStroke(dp(1), MinecraftUi.BORDER);
            mlamp.setBackground(g);
            lampDot = new View(this);
            lampDot.setBackgroundColor(MinecraftUi.RED);
            LinearLayout.LayoutParams mdot = new LinearLayout.LayoutParams(dp(9), dp(9));
            mdot.rightMargin = dp(7);
            mlamp.addView(lampDot, mdot);
            lampText = MinecraftUi.text(this, "已停止", 11, MinecraftUi.RED, true);
            mlamp.addView(lampText);
            header.addView(mlamp, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return header;
        }

        PixelLogoView logo = new PixelLogoView(this);
        logo.setPadding(0, 0, 0, dp(2));
        left.addView(logo);

        TextView tag = MinecraftUi.text(this, "BEDROCK FORGE", 8, 0xFFD8C7AE, true);
        tag.setLetterSpacing(0.28f);
        tag.setShadowLayer(dp(1.5f), dp(1.5f), 0, 0xFF1A1A1A);
        tag.setPadding(0, dp(4), 0, 0);
        left.addView(tag);

        LinearLayout lamp = new LinearLayout(this);
        lamp.setOrientation(LinearLayout.HORIZONTAL);
        lamp.setGravity(Gravity.CENTER_VERTICAL);
        lamp.setPadding(dp(9), dp(6), dp(10), dp(6));
        lamp.setBackground(new MinecraftUi.BevelDrawable(0xFF2A1D13, 0xFF120D08,
                0xFF4C3827, 0xFF0B0805, dp(2), dp(2)));

        lampDot = new View(this);
        lampDot.setBackgroundColor(MinecraftUi.RED);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.rightMargin = dp(6);
        lamp.addView(lampDot, dotLp);

        lampText = MinecraftUi.text(this, "已停止", 11, MinecraftUi.RED, true);
        lampText.setShadowLayer(dp(1.5f), dp(1.5f), 0, 0xFF000000);
        lamp.addView(lampText);

        header.addView(lamp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(6));
        homeCol = col;
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 服务器信息
        LinearLayout info = MinecraftUi.panel(this, "服务器信息");
        addPanel(col, info);

        serverTypeValue = addInfoRow(info, "服务器类型", "Minecraft BDS");
        serverVersionValue = addInfoRow(info, "服务端版本", "--");
        gameVersionValue = addInfoRow(info, "游戏版本", "--");
        ipValue = addInfoRow(info, "IP 地址", "--");
        worldValue = addInfoRow(info, "端口 / 世界", port + " · " + worldName);
        onlineModeValue = addInfoRow(info, "在线模式", "--");
        uptimeValue = addInfoRow(info, "运行时长", "未运行");

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = dp(7);
        info.addView(statusRow, srLp);

        LinearLayout stateBox = MinecraftUi.slot(this);
        stateBox.setPadding(dp(10), dp(8), dp(10), dp(10));
        TextView stateLabel = MinecraftUi.text(this, "运行状态", 10, MinecraftUi.PANEL_MUTED, true);
        stateBox.addView(stateLabel);
        runStateValue = MinecraftUi.text(this, "关闭", 23, MinecraftUi.RED, true);
        stateBox.addView(runStateValue);
        statusRow.addView(stateBox, weightParams(1, 0, 0, 4));

        LinearLayout playerBox = MinecraftUi.slot(this);
        playerBox.setPadding(dp(10), dp(8), dp(10), dp(10));
        TextView playerLabel = MinecraftUi.text(this, "玩家在线", 10, MinecraftUi.PANEL_MUTED, true);
        playerBox.addView(playerLabel);
        playersValue = MinecraftUi.text(this, "--/--", 23, MinecraftUi.GREEN, true);
        playerBox.addView(playersValue);
        statusRow.addView(playerBox, weightParams(1, 4, 0, 0));

        // 服务器负荷
        LinearLayout load = MinecraftUi.panel(this, "服务器负荷");
        addPanel(col, load);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1lp.topMargin = dp(2);
        load.addView(row1, r1lp);

        LinearLayout cpuCell = metricCell(MinecraftUi.PixelIconView.CPU, "设备 CPU");
        cpuValue = (TextView) cpuCell.getTag();
        cpuBar = new MinecraftUi.SegmentBar(this);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
        cblp.topMargin = dp(7);
        cpuCell.addView(cpuBar, cblp);
        row1.addView(cpuCell, metricParams(1, 0, 0, 4));

        LinearLayout memCell = metricCell(MinecraftUi.PixelIconView.MEM, "设备内存");
        memValue = (TextView) memCell.getTag();
        memBar = new MinecraftUi.SegmentBar(this);
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
        mblp.topMargin = dp(7);
        memCell.addView(memBar, mblp);
        row1.addView(memCell, metricParams(1, 4, 0, 0));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(7);
        load.addView(row2, r2lp);

        LinearLayout netCell = metricCell(MinecraftUi.PixelIconView.NET, "网络吞吐");
        netValue = (TextView) netCell.getTag();
        netValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        netValue.setSingleLine(true);
        netValue.setEllipsize(TextUtils.TruncateAt.END);
        row2.addView(netCell, metricParams(1, 0, 0, 4));

        LinearLayout tpsCell = metricCell(MinecraftUi.PixelIconView.TPS, "服务器 TPS");
        tpsValue = (TextView) tpsCell.getTag();
        tpsBar = new MinecraftUi.SegmentBar(this);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
        tblp.topMargin = dp(7);
        tpsCell.addView(tpsBar, tblp);
        row2.addView(tpsCell, metricParams(1, 4, 0, 0));

        // 启动 / 停止 / 重启
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        col.addView(buttons, blp);

        startBtn = new MinecraftUi.McButton(this, "启动");
        stopBtn = new MinecraftUi.McButton(this, "停止");
        restartBtn = new MinecraftUi.McButton(this, "重启");
        if (MinecraftUi.MINIMAL) {
            ((MinecraftUi.McButton) startBtn).setPrimaryStyle();
            stopBtn.setTextColor(MinecraftUi.PANEL_TEXT);
            restartBtn.setTextColor(MinecraftUi.PANEL_TEXT);
        } else if (MinecraftUi.ORE) {
            ((MinecraftUi.McButton) startBtn).setPrimaryStyle();
        } else {
            startBtn.setTextColor(0xFFE8FFE0);
            stopBtn.setTextColor(0xFFFFE0E0);
            restartBtn.setTextColor(Color.WHITE);
        }

        buttons.addView(startBtn, buttonParams(0, 4));
        buttons.addView(stopBtn, buttonParams(4, 4));
        buttons.addView(restartBtn, buttonParams(4, 0));

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startServer(); }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopServer(); }
        });
        restartBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { restartServer(); }
        });

        Button manageBtn = new MinecraftUi.McButton(this, "服务器管理");
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        manageLp.topMargin = dp(8);
        col.addView(manageBtn, manageLp);
        manageBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showManagePage(); }
        });

        shizukuNote = MinecraftUi.text(this, "Shizuku 检查中…", 9, 0xFFD8C7AE, false);
        shizukuNote.setPadding(0, dp(7), 0, 0);
        col.addView(shizukuNote);

        homeDeployGuide = MinecraftUi.panel(this, "开始部署");
        homeGuideText = MinecraftUi.text(this,
                "还没有部署服务器。\n\n"
                        + "1. 先部署运行环境（Box64 + Ubuntu）\n"
                        + "2. 再部署服务端（BDS）\n"
                        + "3. 回到首页启动服务器\n\n"
                        + "运行服务端需要 Shizuku；首次使用请看权限管理里的启动指引。",
                10, MinecraftUi.PANEL_TEXT, false);
        homeDeployGuide.addView(homeGuideText);

        homeGuideRuntimeBtn = new MinecraftUi.McButton(this, "去部署运行环境");
        LinearLayout.LayoutParams grlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        grlp.topMargin = dp(8);
        homeDeployGuide.addView(homeGuideRuntimeBtn, grlp);
        homeGuideRuntimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showManagePage();
                openManageSection(1);
            }
        });

        homeGuideServerBtn = new MinecraftUi.McButton(this, "去部署服务端");
        LinearLayout.LayoutParams gslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        gslp.topMargin = dp(5);
        homeDeployGuide.addView(homeGuideServerBtn, gslp);
        homeGuideServerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!canRuntime || !homeGuideServerBtn.isEnabled()) {
                    toast("请先部署运行环境");
                    return;
                }
                showManagePage();
                openManageSection(0);
            }
        });

        col.addView(homeDeployGuide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < col.getChildCount() - 1; i++) {
            col.getChildAt(i).setVisibility(View.GONE);
        }
        refreshHomeDeployState();

        return scroll;
    }

    private LinearLayout metricCell(int icon, String label) {
        LinearLayout cell = MinecraftUi.slot(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cell.setLayoutParams(lp);
        cell.setPadding(dp(9), dp(8), dp(9), dp(9));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        MinecraftUi.PixelIconView ic = new MinecraftUi.PixelIconView(this);
        ic.set(icon, MinecraftUi.MINIMAL ? MinecraftUi.PANEL_MUTED : 0xFF2F4F1F);
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(13), dp(13));
        iclp.rightMargin = dp(6);
        head.addView(ic, iclp);
        TextView lab = MinecraftUi.text(this, label, 10, MinecraftUi.PANEL_MUTED, true);
        head.addView(lab);
        cell.addView(head);

        TextView val = MinecraftUi.text(this, "--", 21, 0xFF111111, true);
        val.setPadding(0, dp(4), 0, 0);
        cell.addView(val);
        cell.setTag(val);
        return cell;
    }

    private TextView addInfoRow(LinearLayout panel, String label, String value) {
        TextView v = MinecraftUi.valueText(this, value);
        LinearLayout row = MinecraftUi.infoRow(this, label, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5);
        panel.addView(row, lp);
        return v;
    }

    private void addPanel(LinearLayout col, LinearLayout panel) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        col.addView(panel, lp);
    }

    private LinearLayout.LayoutParams metricParams(float weight, int left, int top, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), weight);
        lp.setMargins(dp(left), dp(top), dp(right), 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightParams(float weight, int left, int top, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(left), dp(top), dp(right), 0);
        return lp;
    }

    private LinearLayout.LayoutParams buttonParams(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(left), 0, dp(right), 0);
        return lp;
    }

    private View buildConsole() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = MinecraftUi.text(this, "控制台", 18, Color.WHITE, true);
        title.setShadowLayer(dp(2), dp(2), 0, 0xFF1A1A1A);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button termBtn = new MinecraftUi.McButton(this, "终端");
        LinearLayout.LayoutParams termLp = new LinearLayout.LayoutParams(dp(78), dp(40));
        termLp.rightMargin = dp(5);
        header.addView(termBtn, termLp);
        termBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTerminalDialog(); }
        });

        Button logBtn = new MinecraftUi.McButton(this, "日志");
        header.addView(logBtn, new LinearLayout.LayoutParams(dp(78), dp(40)));
        logBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLogDialog(); }
        });
        page.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout frame = MinecraftUi.panel(this, null);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        flp.topMargin = dp(8);
        page.addView(frame, flp);

        LinearLayout terminal = new LinearLayout(this);
        terminal.setOrientation(LinearLayout.VERTICAL);
        terminal.setBackground(MinecraftUi.terminalDrawable(this));
        terminal.setPadding(dp(8), dp(6), dp(8), dp(8));
        frame.addView(terminal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        addLegend(legend, "INFO", MinecraftUi.MINIMAL ? MinecraftUi.GREEN : 0xFF7EE787);
        addLegend(legend, "WARN", MinecraftUi.MINIMAL ? MinecraftUi.AMBER : 0xFFFFD166);
        addLegend(legend, "ERROR", MinecraftUi.MINIMAL ? MinecraftUi.RED : 0xFFFF6B6B);
        addLegend(legend, "DEBUG", MinecraftUi.MINIMAL ? MinecraftUi.ACCENT : 0xFF79C0FF);
        terminal.addView(legend, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        consoleScroll = new ScrollView(this);
        consoleScroll.setFillViewport(true);

        consoleText = MinecraftUi.text(this, "等待服务端日志…", 9.5f, 0xFFD0D0D0, false);
        consoleText.setTypeface(Typeface.MONOSPACE);
        consoleText.setTextIsSelectable(true);
        consoleText.setLineSpacing(dp(2), 1f);
        consoleScroll.addView(consoleText, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        terminal.addView(consoleScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView symbolLabel = MinecraftUi.text(this, "快捷符号", 10, Color.WHITE, true);
        symbolLabel.setShadowLayer(dp(1.5f), dp(1.5f), 0, 0xFF1A1A1A);
        symbolLabel.setPadding(0, dp(8), 0, dp(4));
        page.addView(symbolLabel);

        LinearLayout symbolFrame = MinecraftUi.panel(this, null);
        symbolFrame.setPadding(dp(6), dp(5), dp(6), dp(5));
        page.addView(symbolFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView symbolScroll = new HorizontalScrollView(this);
        symbolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout symbolRow = new LinearLayout(this);
        symbolRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] symbols = {"/", "~", "^", "@", ":", "\"", "[", "]", "{", "}", "<", ">", "!", "?", "=", ",", "."};
        for (final String symbol : symbols) {
            Button b = new MinecraftUi.McButton(this, symbol);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(42), dp(40));
            blp.setMargins(dp(2), 0, dp(2), 0);
            symbolRow.addView(b, blp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { insertSymbol(symbol); }
            });
        }
        symbolScroll.addView(symbolRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        symbolFrame.addView(symbolScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams irlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        irlp.topMargin = dp(6);
        page.addView(inputRow, irlp);

        consoleInput = new EditText(this);
        consoleInput.setSingleLine(true);
        consoleInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        consoleInput.setTextColor(MinecraftUi.MINIMAL ? MinecraftUi.PANEL_TEXT : 0xFFB8FFB0);
        consoleInput.setHintTextColor(MinecraftUi.MINIMAL ? MinecraftUi.PANEL_MUTED : 0xFF6A8A6A);
        consoleInput.setHint("输入命令，如 list");
        consoleInput.setTypeface(Typeface.MONOSPACE);
        consoleInput.setBackground(MinecraftUi.terminalDrawable(this));
        consoleInput.setPadding(dp(9), dp(8), dp(9), dp(8));
        consoleInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputRow.addView(consoleInput, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button send = new MinecraftUi.McButton(this, "发送");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(70), dp(42));
        slp.leftMargin = dp(7);
        inputRow.addView(send, slp);
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand(); }
        });
        consoleInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCommand();
                    return true;
                }
                return false;
            }
        });

        return page;
    }

    private void addLegend(LinearLayout row, String label, int color) {
        TextView t = MinecraftUi.text(this, label, 8, color, true);
        t.setPadding(0, 0, dp(10), 0);
        row.addView(t);
    }

    private void insertSymbol(String symbol) {
        if (consoleInput == null) return;
        int start = consoleInput.getSelectionStart();
        if (start < 0) start = consoleInput.getText().length();
        String text = consoleInput.getText().toString();
        String newText = text.substring(0, start) + symbol + text.substring(start);
        consoleInput.setText(newText);
        consoleInput.setSelection(start + symbol.length());
        consoleInput.requestFocus();
    }

    private void showTerminalDialog() {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端");
            return;
        }
        if (!ShizukuGate.ensure(this, "Ubuntu 终端")) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout panel = MinecraftUi.panel(this, "终端 · Ubuntu");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TerminalTabsView terminal = new TerminalTabsView(this);
        panel.addView(terminal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        wrap.addView(close, clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
            w.setLayout(width, height);
        }
    }

    private void showLogDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout panel = MinecraftUi.panel(this, "日志");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button jump = new MinecraftUi.McButton(this, "跳到最新");
        panel.addView(jump, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        jump.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (consoleScroll != null) consoleScroll.fullScroll(View.FOCUS_DOWN);
                dialog.dismiss();
            }
        });

        Button copy = new MinecraftUi.McButton(this, "复制全部");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        clp.topMargin = dp(5);
        panel.addView(copy, clp);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] logs = ServerManager.get().getLogs();
                if (logs.length == 0) {
                    toast("暂无日志");
                } else {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("BDS 日志", TextUtils.join("\n", logs)));
                    toast("已复制 " + logs.length + " 行");
                }
                dialog.dismiss();
            }
        });

        Button appLog = new MinecraftUi.McButton(this, "App 操作日志");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        alp.topMargin = dp(5);
        panel.addView(appLog, alp);
        appLog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Dialog d = new Dialog(MainActivity.this);
                d.requestWindowFeature(Window.FEATURE_NO_TITLE);
                d.setContentView(new AppLogView(MainActivity.this));
                Window dw = d.getWindow();
                if (dw != null) {
                    dw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    dw.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                            (int) (getResources().getDisplayMetrics().heightPixels * 0.7f));
                }
                d.show();
            }
        });

        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        closeLp.topMargin = dp(5);
        panel.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static int logColor(String line) {
        String u = line.toUpperCase();
        if (u.contains("WARN]") || u.contains("WARNING")) return 0xFFFFD166;
        if (u.contains("ERROR]") || u.contains("FATAL]") || u.contains("SEVERE]")) return 0xFFFF6B6B;
        if (u.contains("DEBUG]")) return 0xFF79C0FF;
        if (u.contains("INFO]")) return 0xFF7EE787;
        if (line.startsWith("at ") || line.startsWith("\tat ") || u.contains("CAUSED BY:")) return 0xFF9A9A9A;
        if (line.startsWith("#")) return 0xFF5BC236;
        if (u.contains("LOADING")) return 0xFF79C0FF;
        if (u.contains("SERVER STARTED")) return 0xFF7EE787;
        if (u.contains("ERROR") || u.contains("EXCEPTION") || u.contains("FAILED")) return 0xFFFF6B6B;
        return 0xFFD0D0D0;
    }

    private AiAssistantView aiAssistantView;

    private View buildAi() {
        aiAssistantView = new AiAssistantView(this);
        return aiAssistantView;
    }

    private void selectAiTab(int index) {
        for (int i = 0; i < aiTabs.length; i++) {
            LinearLayout tab = aiTabs[i];
            if (tab == null) continue;
            boolean active = i == index;
            tab.setBackground(new MinecraftUi.BevelDrawable(
                    active ? 0xFFC6C6C6 : 0xFFA8A8A8,
                    active ? MinecraftUi.GREEN_BRIGHT : 0xFF151515,
                    active ? 0xFFFFFFFF : 0xFFE0E0E0,
                    active ? 0xFF5A5A5A : 0xFF777777,
                    dp(2), dp(2)));
            TextView label = (TextView) tab.getChildAt(0);
            label.setTextColor(active ? 0xFF1B1B1B : 0xFF4A4A4A);
        }
    }

    private void addChatMessage(String who, String text, boolean right) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(right ? Gravity.END : Gravity.START);

        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.gravity = right ? Gravity.END : Gravity.START;
        blockLp.topMargin = dp(7);
        chatMessages.addView(block, blockLp);

        TextView label = MinecraftUi.text(this, who, 9,
                right ? 0xFFD8C7AE : 0xFFB8FFB0, true);
        label.setGravity(right ? Gravity.END : Gravity.START);
        block.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setBackground(new MinecraftUi.BevelDrawable(
                right ? 0xFF8FBF6A : 0xFFC6C6C6,
                0xFF151515,
                right ? 0xFFCBE8B0 : 0xFFFFFFFF,
                right ? 0xFF5A7F3A : 0xFF5A5A5A,
                dp(2), dp(2)));
        bubble.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView msg = MinecraftUi.text(this, text, 12, 0xFF1B1B1B, false);
        msg.setMaxWidth(dp(270));
        msg.setLineSpacing(dp(1), 1f);
        bubble.addView(msg);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(3);
        block.addView(bubble, blp);
    }

    private void sendAiMessage() {
        if (aiInput == null) return;
        String text = aiInput.getText().toString().trim();
        if (text.isEmpty()) return;
        toast("AI 功能暂未实装");
    }

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout panel = MinecraftUi.panel(this, "设置");
        addPanel(col, panel);

        String[] items = {"主题", "语言", "权限管理", "AI 智能体", "清除所有数据", "关于"};
        for (int i = 0; i < items.length; i++) {
            final String title = items[i];
            Button item = new MinecraftUi.McButton(this, title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            lp.topMargin = i == 0 ? 0 : dp(6);
            panel.addView(item, lp);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if ("主题".equals(title)) {
                        showThemeDialog();
                    } else if ("语言".equals(title)) {
                        toast("语言功能暂未实装");
                    } else if ("权限管理".equals(title)) {
                        showPermissionsDialog();
                    } else if ("AI 智能体".equals(title)) {
                        showAiAgentDialog();
                    } else if ("清除所有数据".equals(title)) {
                        showWipeFirst();
                    } else if ("关于".equals(title)) {
                        AboutDialog.show(MainActivity.this);
                    } else {
                        showSettingsPlaceholder(title);
                    }
                }
            });
        }

        return scroll;
    }

    private void showAgreementDialog() {
        String text;
        try {
            java.io.InputStream in = getAssets().open("legal/user_agreement.txt");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            text = new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            text = "用户协议读取失败：" + e.getMessage();
        }

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(8));
        LinearLayout panel = MinecraftUi.panel(this, "用户协议");
        TextView body = MinecraftUi.text(this, text, 8.8f, MinecraftUi.PANEL_TEXT, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(dp(2), dp(2), dp(2), dp(2));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        wrap.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
    }

    private void showWipeFirst() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(this, "清除所有数据");
        panel.addView(MinecraftUi.text(this,
                "将删除本应用产生的全部数据：\n"
                        + "· /data/local/tmp/mcbds 下的服务端实例、世界、备份、运行环境\n"
                        + "· 应用设置、AI 配置与 API Key、对话记录、日志与缓存\n\n"
                        + "服务端会先被停止；清理后需要重新部署运行环境。\n"
                        + "此操作不可恢复，请确认已自行保留需要的数据。",
                9f, MinecraftUi.PANEL_TEXT, false));
        Button next = new MinecraftUi.McButton(this, "继续");
        if (next instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) next).setDangerStyle();
        next.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        nlp.topMargin = dp(10);
        panel.addView(next, nlp);
        next.setOnClickListener(v -> {
            d.dismiss();
            showWipeSecond();
        });
        Button cancel = new MinecraftUi.McButton(this, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showWipeSecond() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(this, "二次确认");
        panel.addView(MinecraftUi.text(this,
                "请输入“清除”两个字以确认。", 9f, MinecraftUi.PANEL_TEXT, false));
        final EditText input = MinecraftUi.terminalInput(this, "清除");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ilp.topMargin = dp(6);
        panel.addView(input, ilp);
        Button ok = new MinecraftUi.McButton(this, "确认清除");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        ok.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(8);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            if (!"清除".equals(input.getText().toString().trim())) {
                toast("请输入“清除”后重试");
                return;
            }
            d.dismiss();
            performWipe();
        });
        Button cancel = new MinecraftUi.McButton(this, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    (int) (ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void performWipe() {
        new Thread(() -> {
            try {
                if (ServerManager.get().isRunning()) {
                    runOnUiThread(this::stopServer);
                    for (int i = 0; i < 60 && ServerManager.get().isRunning(); i++) {
                        Thread.sleep(500);
                    }
                }
                if (Shizuku.pingBinder()) {
                    ShizukuShell.sh("rm -rf /data/local/tmp/mcbds " + DeployManager.BOX64_BASE);
                }
                File prefs = new File(getApplicationInfo().dataDir, "shared_prefs");
                deleteTree(prefs);
                deleteTree(getCacheDir());
                deleteTree(getFilesDir());
                runOnUiThread(() -> {
                    toast("已清除全部数据，请重新打开应用");
                    finishAffinity();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> toast("清除失败：" + e.getMessage()));
            }
        }, "wipe-data").start();
    }

    private void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private void showAiAgentDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        ScrollView sv = new ScrollView(this);
        sv.addView(new AiSettingsView(this));
        wrap.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        wrap.addView(close, clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
    }

    private void showPermissionsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        PermissionView view = new PermissionView(this);
        activePermissionView = view;
        wrap.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        wrap.addView(close, clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { activePermissionView = null; }
        });
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showThemeDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(this, "主题");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final String oldStyle = ThemeManager.style(this);
        final String oldMode = ThemeManager.mode(this);
        final String[] sel = {oldStyle, oldMode};

        TextView styleLabel = MinecraftUi.text(this, "风格", 9, MinecraftUi.PANEL_MUTED, true);
        styleLabel.setPadding(0, dp(2), 0, dp(4));
        panel.addView(styleLabel);
        Button classic = new MinecraftUi.McButton(this, "经典风格");
        panel.addView(classic, dialogBtnLp(0));
        Button minimal = new MinecraftUi.McButton(this, "简约风格");
        panel.addView(minimal, dialogBtnLp(6));
        Button ore = new MinecraftUi.McButton(this, "Ore UI");
        panel.addView(ore, dialogBtnLp(6));

        TextView modeLabel = MinecraftUi.text(this, "简约风格明暗", 9,
                MinecraftUi.PANEL_MUTED, true);
        modeLabel.setPadding(0, dp(12), 0, dp(4));
        panel.addView(modeLabel);
        Button light = new MinecraftUi.McButton(this, "浅色模式");
        panel.addView(light, dialogBtnLp(0));
        Button dark = new MinecraftUi.McButton(this, "深色模式");
        panel.addView(dark, dialogBtnLp(6));

        final TextView summary = MinecraftUi.text(this, "", 8.5f,
                MinecraftUi.PANEL_MUTED, false);
        summary.setPadding(0, dp(10), 0, 0);
        panel.addView(summary);

        final Runnable refresh = () -> {
            classic.setText(ThemeManager.CLASSIC.equals(sel[0]) ? "经典风格（当前）" : "经典风格");
            minimal.setText(ThemeManager.MINIMAL.equals(sel[0]) ? "简约风格（当前）" : "简约风格");
            ore.setText(ThemeManager.ORE.equals(sel[0]) ? "Ore UI（当前）" : "Ore UI");
            light.setText(ThemeManager.MODE_LIGHT.equals(sel[1]) ? "浅色模式（当前）" : "浅色模式");
            dark.setText(ThemeManager.MODE_DARK.equals(sel[1]) ? "深色模式（当前）" : "深色模式");
            boolean minimalSel = ThemeManager.MINIMAL.equals(sel[0]);
            light.setEnabled(minimalSel);
            dark.setEnabled(minimalSel);
            light.setAlpha(minimalSel ? 1f : 0.5f);
            dark.setAlpha(minimalSel ? 1f : 0.5f);
            String modeText = ThemeManager.MODE_DARK.equals(sel[1]) ? "深色模式" : "浅色模式";
            if (ThemeManager.ORE.equals(sel[0])) {
                summary.setText("重启后使用：Ore UI · 标准外观（不支持明暗模式）");
            } else if (minimalSel) {
                summary.setText("重启后使用：简约风格 · " + modeText);
            } else {
                summary.setText("重启后使用：经典风格");
            }
        };
        refresh.run();

        classic.setOnClickListener(v -> { sel[0] = ThemeManager.CLASSIC; refresh.run(); });
        minimal.setOnClickListener(v -> { sel[0] = ThemeManager.MINIMAL; refresh.run(); });
        ore.setOnClickListener(v -> { sel[0] = ThemeManager.ORE; refresh.run(); });
        light.setOnClickListener(v -> { sel[1] = ThemeManager.MODE_LIGHT; refresh.run(); });
        dark.setOnClickListener(v -> { sel[1] = ThemeManager.MODE_DARK; refresh.run(); });

        Button apply = new MinecraftUi.McButton(this, "应用主题");
        panel.addView(apply, dialogBtnLp(12));
        apply.setOnClickListener(v -> {
            if (sel[0].equals(oldStyle) && sel[1].equals(oldMode)) {
                toast("当前已是该主题");
                return;
            }
            d.dismiss();
            showRestartPrompt(sel[0], sel[1]);
        });

        Button close = new MinecraftUi.McButton(this, "关闭");
        wrap.addView(close, dialogBtnLp(8));
        close.setOnClickListener(v -> d.dismiss());

        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout.LayoutParams dialogBtnLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void showRestartPrompt(final String style, final String mode) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(this, "重启应用");
        TextView msg = MinecraftUi.text(this,
                "主题已更改，需要重启应用后生效。\n\n"
                        + "点击“立即重启”保存并重启；点击“取消”放弃本次更改。",
                9.5f, MinecraftUi.PANEL_TEXT, false);
        msg.setPadding(0, dp(2), 0, dp(10));
        panel.addView(msg);

        Button restart = new MinecraftUi.McButton(this, "立即重启");
        panel.addView(restart, dialogBtnLp(0));
        restart.setOnClickListener(v -> {
            ThemeManager.setStyle(MainActivity.this, style);
            ThemeManager.setMode(MainActivity.this, mode);
            d.dismiss();
            android.content.Intent i = new android.content.Intent(
                    MainActivity.this, SplashActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            overridePendingTransition(0, 0);
            finishAffinity();
        });

        Button cancel = new MinecraftUi.McButton(this, "取消");
        panel.addView(cancel, dialogBtnLp(6));
        cancel.setOnClickListener(v -> d.dismiss());

        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showSettingsPlaceholder(String title) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout panel = MinecraftUi.panel(this, title);
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = MinecraftUi.text(this, "界面占位 · 功能暂未实装", 14,
                MinecraftUi.PANEL_TEXT, true);
        desc.setPadding(0, dp(6), 0, dp(10));
        panel.addView(desc);

        Button close = new MinecraftUi.McButton(this, "关闭");
        panel.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private View buildHotbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(MinecraftUi.hotbarDrawable(this));
        bar.setPadding(dp(5), dp(5), dp(5), dp(5));

        String[] labels = {"首页", "控制台", "AI", "设置"};
        int[] icons = {MinecraftUi.PixelIconView.HOME, MinecraftUi.PixelIconView.CONSOLE,
                MinecraftUi.PixelIconView.AI, MinecraftUi.PixelIconView.SETTINGS};

        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout slot = new LinearLayout(this);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.CENTER);
            slot.setPadding(dp(3), dp(6), dp(3), dp(5));
            slot.setBackground(MinecraftUi.hotbarSlotDrawable(this, i == 0));

            MinecraftUi.PixelIconView icon = new MinecraftUi.PixelIconView(this);
            if (MinecraftUi.MINIMAL || MinecraftUi.ORE) {
                icon.set(icons[i], i == 0 ? 0xFFFFFFFF : MinecraftUi.MUTED);
            } else {
                icon.set(icons[i], i == 0 ? MinecraftUi.GREEN_BRIGHT : 0xFFCBBBA4);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(23), dp(23));
            slot.addView(icon, ilp);

            TextView label = MinecraftUi.text(this, labels[i], 9,
                    i == 0 ? ((MinecraftUi.MINIMAL || MinecraftUi.ORE) ? 0xFFFFFFFF : 0xFFEAFFDF)
                            : ((MinecraftUi.MINIMAL || MinecraftUi.ORE) ? MinecraftUi.MUTED : 0xFFCBBBA4), true);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            slot.addView(label);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(slot, lp);

            navSlots[i] = slot;
            navIcons[i] = icon;
            navLabels[i] = label;

            slot.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showPage(index); }
            });
        }
        return bar;
    }

    private View buildManagePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new MinecraftUi.McButton(this, "← 返回");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(84), dp(40));
        header.addView(back, blp);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (manageSectionOpen) {
                    showManageList();
                } else {
                    showPage(0);
                }
            }
        });

        manageTitle = MinecraftUi.text(this, "服务器管理", 18, Color.WHITE, true);
        if (!MinecraftUi.MINIMAL) manageTitle.setShadowLayer(dp(2), dp(2), 0, 0xFF1A1A1A);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        header.addView(manageTitle, tlp);
        page.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        manageHost = new FrameLayout(this);
        manageList = buildManageList();
        manageHost.addView(manageList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hlp.topMargin = dp(8);
        page.addView(manageHost, hlp);
        return page;
    }

    private View buildManageList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(6));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout panel = MinecraftUi.panel(this, "管理选项");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.bottomMargin = dp(8);
        col.addView(panel, plp);

        for (int i = 0; i < MANAGE_LABELS.length; i++) {
            final int index = i;
            Button item = new MinecraftUi.McButton(this, MANAGE_LABELS[i]);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            ilp.topMargin = i == 0 ? 0 : dp(5);
            panel.addView(item, ilp);
            manageButtons[i] = item;
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openManageSection(index); }
            });
        }
        return scroll;
    }

    private void showManagePage() {
        if (oreTitle != null) oreTitle.setText("服务器管理");
        for (View p : pages) p.setVisibility(View.GONE);
        managePage.setVisibility(View.VISIBLE);
        currentPage = 4;
        manageTitle.setText("服务器管理");
        showManageList();
    }

    private boolean manageAllowed(int index) {
        if (index == 1) return true;
        if (index == 6) return true;   // 备份：没有服务端时也要能回滚恢复
        if (index == 0) return canRuntime;
        if (index == 2) return canRuntime && canServer;
        return canRuntime && canServer;
    }

    private String manageDeniedReason(int index) {
        if (index == 0) return "请先部署运行环境";
        if (index == 2) return "请先部署服务端";
        return "请先完成运行环境和服务端部署";
    }

    private void applyManageGating() {
        for (int i = 0; i < manageButtons.length; i++) {
            if (manageButtons[i] == null) continue;
            boolean a = manageAllowed(i);
            manageButtons[i].setEnabled(a);
            manageButtons[i].setAlpha(a ? 1f : 0.45f);
        }
    }

    private void refreshManageGating() {
        if (!gatingLoaded) {
            for (int i = 0; i < manageButtons.length; i++) {
                if (manageButtons[i] == null) continue;
                boolean a = i == 1 || i == 8;
                manageButtons[i].setEnabled(a);
                manageButtons[i].setAlpha(a ? 1f : 0.45f);
            }
        } else {
            applyManageGating();
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                boolean rt = false, srv = false, ld = false;
                try {
                    rt = DeployManager.detect(MainActivity.this, DeployManager.TYPE_RUNTIME).installed;
                } catch (Throwable ignored) {
                }
                try {
                    srv = DeployManager.detect(MainActivity.this, DeployManager.TYPE_SERVER).installed;
                } catch (Throwable ignored) {
                }
                canRuntime = rt;
                canServer = srv;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        gatingLoaded = true;
                        applyManageGating();
                    }
                });
            }
        });
    }

    private void showManageList() {
        manageSectionOpen = false;
        if (manageTitle != null) manageTitle.setText("服务器管理");
        if (manageList.getParent() != null) {
            ((ViewGroup) manageList.getParent()).removeView(manageList);
        }
        manageHost.removeAllViews();
        manageHost.addView(manageList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setHotbarActive(0);
        refreshManageGating();
    }

    private void openManageSection(int index) {
        if (!ShizukuGate.ensure(this, "服务器管理")) return;
        if (!manageAllowed(index)) {
            toast(manageDeniedReason(index));
            return;
        }
        manageSectionOpen = true;
        if (manageTitle != null) manageTitle.setText(MANAGE_LABELS[index]);
        manageHost.removeAllViews();
        String label = MANAGE_LABELS[index];
        View section = null;
        if ("服务端".equals(label)) {
            section = new DeploySectionView(this, DeployManager.TYPE_SERVER, this);
        } else if ("运行环境".equals(label)) {
            section = new DeploySectionView(this, DeployManager.TYPE_RUNTIME, this);
        } else if ("addons管理".equals(label)) {
            section = new AddonsSectionView(this, this);
        } else if ("服务器设置".equals(label)) {
            ServerSettingsView view = new ServerSettingsView(this, new Runnable() {
                @Override public void run() { showManageList(); }
            }, new ServerSettingsView.OnSaved() {
                @Override public void onSaved(Map<String, String> values) {
                    try {
                        if (values.containsKey("server-port")) {
                            port = Integer.parseInt(values.get("server-port").trim());
                        }
                        if (values.containsKey("level-name")) {
                            String ln = values.get("level-name").trim();
                            if (!ln.isEmpty()) worldName = ln;
                        }
                        ServerManager.get().setPort(port);
                    } catch (Throwable ignored) {
                    }
                }
            });
            section = view;
        } else if ("备份".equals(label)) {
            section = new BackupSectionView(this, this);
        } else if ("存档管理".equals(label)) {
            section = new WorldSectionView(this, this);
        } else if ("玩家管理".equals(label)) {
            section = new PlayerSectionView(this);
        }
        if (section != null) {
            manageHost.addView(section, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            manageHost.addView(buildPlaceholder(index), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setHotbarActive(0);
    }

    private View buildPlaceholder(int index) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout panel = MinecraftUi.panel(this, MANAGE_LABELS[index]);
        TextView desc = MinecraftUi.text(this, MANAGE_DESCS[index], 11, MinecraftUi.PANEL_MUTED, false);
        panel.addView(desc);

        TextView status = MinecraftUi.text(this, "界面占位 · 暂未实装", 15, MinecraftUi.PANEL_TEXT, true);
        status.setPadding(0, dp(14), 0, dp(10));
        panel.addView(status);

        Button back = new MinecraftUi.McButton(this, "← 返回列表");
        panel.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showManageList(); }
        });

        col.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void setHotbarActive(int index) {
        for (int i = 0; i < navSlots.length; i++) {
            boolean active = i == index;
            navSlots[i].setBackground(MinecraftUi.hotbarSlotDrawable(this, active));
            navIcons[i].set(i == 0 ? MinecraftUi.PixelIconView.HOME :
                    i == 1 ? MinecraftUi.PixelIconView.CONSOLE :
                    i == 2 ? MinecraftUi.PixelIconView.AI : MinecraftUi.PixelIconView.SETTINGS,
                    (MinecraftUi.MINIMAL || MinecraftUi.ORE)
                            ? (active ? 0xFFFFFFFF : MinecraftUi.MUTED)
                            : (active ? MinecraftUi.GREEN_BRIGHT : 0xFFCBBBA4));
            navLabels[i].setTextColor((MinecraftUi.MINIMAL || MinecraftUi.ORE)
                    ? (active ? 0xFFFFFFFF : MinecraftUi.MUTED)
                    : (active ? 0xFFEAFFDF : 0xFFCBBBA4));
        }
    }

    private String baseGameVersion(String version) {
        if (version == null || version.isEmpty() || "--".equals(version)) return "--";
        String[] p = version.split("\\.");
        if (p.length == 4) return p[0] + "." + p[1] + "." + p[2];
        return version;
    }

    private void refreshHomeDeployState() {
        if (homeCol == null || homeDeployGuide == null) return;
        io.execute(new Runnable() {
            @Override
            public void run() {
                boolean rt = false, srv = false, ld = false;
                DeployManager.Status serverStatus = null;
                try {
                    rt = DeployManager.detect(MainActivity.this, DeployManager.TYPE_RUNTIME).installed;
                } catch (Throwable ignored) {
                }
                try {
                    serverStatus = DeployManager.detect(MainActivity.this, DeployManager.TYPE_SERVER);
                    srv = serverStatus.installed;
                } catch (Throwable ignored) {
                }
                final boolean hasRuntime = rt;
                final boolean hasServer = srv;
                final DeployManager.Status st = serverStatus;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        canRuntime = hasRuntime;
                        canServer = hasServer;
                        gatingLoaded = true;
                        applyManageGating();
                        int count = homeCol.getChildCount();
                        for (int i = 0; i < count; i++) {
                            View v = homeCol.getChildAt(i);
                            if (v == homeDeployGuide) {
                                v.setVisibility(hasServer ? View.GONE : View.VISIBLE);
                            } else {
                                v.setVisibility(hasServer ? View.VISIBLE : View.GONE);
                            }
                        }
                        if (hasServer) {
                            if (st != null) {
                                if (serverTypeValue != null) serverTypeValue.setText("Minecraft BDS");
                                if (serverVersionValue != null) serverVersionValue.setText(st.version);
                                if (gameVersionValue != null
                                        && (!serverReady || liveGameVersion.isEmpty())) {
                                    gameVersionValue.setText(baseGameVersion(st.version));
                                }
                            }
                            return;
                        }
                        if (serverVersionValue != null) serverVersionValue.setText("--");
                        if (gameVersionValue != null) gameVersionValue.setText("--");
                        if (!ShizukuGate.isRunning()) {
                            homeGuideText.setText("还没有检测到 Shizuku，无法检测本地部署。\n\n"
                                    + "启动 Shizuku 后，这里会显示运行环境和部署指引。");
                            homeGuideRuntimeBtn.setText("去部署运行环境");
                            homeGuideRuntimeBtn.setEnabled(true);
                            homeGuideServerBtn.setEnabled(false);
                            return;
                        }
                        if (hasRuntime) {
                            homeGuideText.setText("运行环境已部署完成。\n\n"
                                    + "下一步：部署服务端（BDS）\n"
                                    + "部署完成后回到首页启动服务器。");
                            homeGuideRuntimeBtn.setText("运行环境已部署 ✓");
                            homeGuideRuntimeBtn.setEnabled(false);
                            homeGuideRuntimeBtn.setAlpha(0.6f);
                            homeGuideServerBtn.setText("去部署服务端");
                            homeGuideServerBtn.setEnabled(true);
                            homeGuideServerBtn.setAlpha(1f);
                        } else {
                            homeGuideText.setText("还没有部署运行环境。\n\n"
                                    + "1. 先部署运行环境（Box64 + Ubuntu）\n"
                                    + "2. 再部署服务端（BDS）\n"
                                    + "3. 回到首页启动服务器\n\n"
                                    + "运行服务端需要 Shizuku；首次使用请看权限管理里的启动指引。");
                            homeGuideRuntimeBtn.setText("去部署运行环境");
                            homeGuideRuntimeBtn.setEnabled(true);
                            homeGuideRuntimeBtn.setAlpha(1f);
                            homeGuideServerBtn.setText("请先部署运行环境");
                            homeGuideServerBtn.setEnabled(false);
                            homeGuideServerBtn.setAlpha(0.5f);
                        }
                    }
                });
            }
        });
    }

    private void showPage(int index) {
        if (index < 0 || index >= pages.length || pages[index] == null) return;
        if (index == 2 && aiAssistantView != null) {
            aiAssistantView.refreshStatus();
        }
        currentPage = index;
        if (oreTitle != null) {
            String[] names = {"首页", "控制台", "AI", "设置"};
            if (index >= 0 && index < names.length) oreTitle.setText(names[index]);
        }
        if (managePage != null) managePage.setVisibility(View.GONE);
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        setHotbarActive(index);
        if (index == 0) refreshHomeDeployState();
        if (index == 1) {
            consoleScroll.post(new Runnable() {
                @Override public void run() { consoleScroll.fullScroll(View.FOCUS_DOWN); }
            });
        }
    }

    // ---------------------------------------------------------------- actions

    private void showManageDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout panel = MinecraftUi.panel(this, "服务器管理");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean running = ServerManager.get().isRunning();
        String state = running ? (serverReady ? "运行中" : "启动中") : "已停止";
        TextView status = MinecraftUi.text(this, "状态：" + state, 13, MinecraftUi.PANEL_TEXT, true);
        panel.addView(status);

        String addr = (lanIp == null ? "--" : lanIp) + ":" + port;
        TextView address = MinecraftUi.text(this, "地址：" + addr, 12, MinecraftUi.PANEL_MUTED, true);
        address.setPadding(0, dp(5), 0, dp(8));
        panel.addView(address);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new MinecraftUi.McButton(this, "启动");
        Button stop = new MinecraftUi.McButton(this, "停止");
        Button restart = new MinecraftUi.McButton(this, "重启");
        if (MinecraftUi.MINIMAL) {
            ((MinecraftUi.McButton) start).setPrimaryStyle();
            stop.setTextColor(MinecraftUi.PANEL_TEXT);
            restart.setTextColor(MinecraftUi.PANEL_TEXT);
        } else if (MinecraftUi.ORE) {
            ((MinecraftUi.McButton) start).setPrimaryStyle();
        } else {
            start.setTextColor(0xFFE8FFE0);
            stop.setTextColor(0xFFFFE0E0);
            restart.setTextColor(Color.WHITE);
        }
        start.setEnabled(!running);
        stop.setEnabled(running);
        restart.setEnabled(running);
        start.setAlpha(start.isEnabled() ? 1f : 0.5f);
        stop.setAlpha(stop.isEnabled() ? 1f : 0.5f);
        restart.setAlpha(restart.isEnabled() ? 1f : 0.5f);
        row.addView(start, buttonParams(0, 4));
        row.addView(stop, buttonParams(4, 4));
        row.addView(restart, buttonParams(4, 0));
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); startServer(); }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); stopServer(); }
        });
        restart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); restartServer(); }
        });

        Button close = new MinecraftUi.McButton(this, "关闭");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        closeLp.topMargin = dp(8);
        panel.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        TextView note = MinecraftUi.text(this, "更多管理功能待接入", 9, MinecraftUi.PANEL_MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    void aiStartServer() {
        startServer();
    }

    void aiStopServer() {
        stopServer();
    }

    private void startServer() {
        if (!ShizukuGate.ensure(this, "启动服务端")) return;
        boolean ping;
        try {
            ping = Shizuku.pingBinder();
        } catch (Throwable t) {
            toast("Shizuku 不可用：" + t.getMessage());
            return;
        }
        if (!ping) {
            toast("Shizuku 未运行：请先启动 Shizuku 服务");
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0);
                toast("请在 Shizuku 弹窗中允许本应用");
                return;
            }
        } catch (Throwable t) {
            toast("Shizuku 权限检查失败：" + t.getMessage());
            return;
        }
        Intent i = new Intent(this, ServerService.class).setAction(ServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        serverReady = false;
        toast("正在启动服务端…");
    }

    private void stopServer() {
        Intent i = new Intent(this, ServerService.class).setAction(ServerService.ACTION_STOP);
        startService(i);
        serverReady = false;
        toast("正在停止…");
    }

    private void restartServer() {
        if (!ServerManager.get().isRunning()) {
            startServer();
            return;
        }
        stopServer();
        toast("已停止，75 秒后自动重启（释放端口）");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!ServerManager.get().isRunning()) {
                    startServer();
                }
            }
        }, 75000);
    }

    private void sendCommand() {
        if (consoleInput == null) return;
        String cmd = consoleInput.getText().toString().trim();
        if (cmd.isEmpty()) return;
        boolean ok = ServerManager.get().send(cmd);
        if (!ok) toast("服务端未运行");
        consoleInput.setText("");
    }

    // ---------------------------------------------------------------- polling

    private void sampleTps(long now) {
        ServerManager m = ServerManager.get();
        if (!m.isRunning() || !serverReady) {
            tps = 0;
            lastGametimeTicks = -1;
            lastGametimeAt = 0;
            pendingTpsAt = 0;
            nextTpsQuery = 0;
            return;
        }
        if (now >= nextTpsQuery) {
            m.send("time query gametime");
            nextTpsQuery = now + 10000;
            pendingTpsAt = now;
        }
        if (pendingTpsAt == 0) return;
        String[] logs = m.getLogs();
        for (int i = logs.length - 1; i >= 0 && i >= logs.length - 20; i--) {
            String line = logs[i];
            int idx = line.indexOf("Gametime is ");
            if (idx < 0) continue;
            long ticks;
            try {
                ticks = Long.parseLong(line.substring(idx + "Gametime is ".length()).trim());
            } catch (Throwable t) {
                continue;
            }
            if (ticks == lastGametimeTicks) break;
            if (lastGametimeTicks >= 0 && lastGametimeAt > 0) {
                double sec = (now - lastGametimeAt) / 1000.0;
                if (sec >= 5 && sec <= 20) {
                    int v = (int) Math.round((ticks - lastGametimeTicks) / sec);
                    tps = Math.max(0, Math.min(20, v));
                }
            }
            lastGametimeTicks = ticks;
            lastGametimeAt = now;
            pendingTpsAt = 0;
            break;
        }
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0 || days > 0) sb.append(hours).append("小时");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("分钟");
        sb.append(seconds).append("秒");
        return sb.toString();
    }

    private void updateState() {
        ServerManager m = ServerManager.get();
        boolean running = m.isRunning();
        boolean stopping = m.isStopping();
        boolean ready = serverReady;
        if (uptimeValue != null) {
            if (running && m.getStartTime() > 0) {
                uptimeValue.setText(formatDuration(System.currentTimeMillis() - m.getStartTime()));
            } else {
                uptimeValue.setText("未运行");
            }
        }

        String state;
        int color;
        if (running && stopping) {
            state = "停止中";
            color = MinecraftUi.AMBER;
        } else if (running && !ready) {
            state = "启动中";
            color = MinecraftUi.AMBER;
        } else if (running) {
            state = "运行中";
            color = MinecraftUi.GREEN;
        } else {
            state = "已停止";
            color = MinecraftUi.RED;
        }

        if (lampText != null) {
            lampText.setText(state);
            lampText.setTextColor(color);
        }
        if (lampDot != null) {
            lampDot.setBackgroundColor(color);
        }
        if (runStateValue != null) {
            runStateValue.setText(running ? state : "关闭");
            runStateValue.setTextColor(color);
        }
        if (startBtn != null) startBtn.setEnabled(!running);
        if (stopBtn != null) stopBtn.setEnabled(running);
        if (restartBtn != null) restartBtn.setEnabled(running);
        if (startBtn != null) startBtn.setAlpha(startBtn.isEnabled() ? 1f : 0.5f);
        if (stopBtn != null) stopBtn.setAlpha(stopBtn.isEnabled() ? 1f : 0.5f);
        if (restartBtn != null) restartBtn.setAlpha(restartBtn.isEnabled() ? 1f : 0.5f);

        if (!running) {
            if (playersValue != null) {
                playersValue.setText("--/--");
                playersValue.setTextColor(MinecraftUi.MINIMAL ? MinecraftUi.PANEL_MUTED : 0xFF4A4A4A);
            }
            serverReady = false;
        } else if (!ready && playersValue != null) {
            playersValue.setText("连接中…");
            playersValue.setTextColor(MinecraftUi.AMBER);
        }

        boolean shizuku;
        boolean granted = false;
        try {
            IBinder binder = Shizuku.getBinder();
            shizuku = binder != null && binder.isBinderAlive() && Shizuku.pingBinder();
            if (shizuku) {
                granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Throwable t) {
            shizuku = false;
        }
        if (shizuku && !granted && !shizukuPermissionRequested) {
            shizukuPermissionRequested = true;
            try {
                Shizuku.requestPermission(0);
                toast("检测到 Shizuku，正在请求授权");
            } catch (Throwable ignored) {
            }
        }
        String s;
        int sc;
        if (!shizuku) {
            s = "Shizuku 未运行 · 请先启动 Shizuku";
            sc = 0xFFFFB4AB;
        } else if (!granted) {
            s = "Shizuku 已连接 · 待授权";
            sc = 0xFFFFD180;
        } else {
            s = "Shizuku 已连接";
            sc = 0xFFB8FFB0;
        }
        if (shizukuNote != null) {
            shizukuNote.setText(s);
            shizukuNote.setTextColor(sc);
        }
        if (settingsShizuku != null) {
            settingsShizuku.setText(!shizuku ? "未运行" : granted ? "已连接" : "待授权");
        }
        if (settingsEnv != null) {
            settingsEnv.setText(EnvManager.isInstalled(this) ? "已安装" : "未安装");
        }
        if (settingsIp != null) {
            settingsIp.setText(lanIp == null ? "--" : lanIp + ":" + port);
        }

        if (ipValue != null) ipValue.setText(lanIp == null ? "获取中…" : lanIp);
        if (worldValue != null) {
            String level = (serverReady && !liveLevel.isEmpty()) ? liveLevel : worldName;
            worldValue.setText(port + " · " + level);
        }

        boolean showMetrics = running && ready;
        DeviceMonitor dm = DeviceMonitor.get();
        if (cpuValue != null) cpuValue.setText(showMetrics ? dm.cpuPercent + "%" : "--");
        if (memValue != null) {
            memValue.setText(showMetrics && dm.memoryTotal > 0
                    ? String.format(Locale.US, "%.1fGB", dm.memoryUsed / 1000000000.0) : "--");
        }
        if (netValue != null) {
            netValue.setText(showMetrics
                    ? String.format(Locale.US, "↑%.1f ↓%.1f", dm.txMbps, dm.rxMbps)
                    : "↑--  ↓--");
        }
        if (tpsValue != null) tpsValue.setText(showMetrics && tps > 0 ? String.valueOf(tps) : "--");
        if (cpuBar != null) cpuBar.set(10, showMetrics ? Math.min(10, dm.cpuPercent / 10) : 0);
        if (memBar != null) {
            int memSeg = showMetrics && dm.memoryTotal > 0
                    ? (int) Math.min(10, dm.memoryUsed * 10 / dm.memoryTotal) : 0;
            memBar.set(10, memSeg);
        }
        if (tpsBar != null) {
            int tpsColor = !showMetrics || tps <= 0 ? 0xFF7B7B7B
                    : (tps >= 15 ? MinecraftUi.GREEN_BRIGHT : (tps >= 8 ? MinecraftUi.AMBER : MinecraftUi.RED));
            tpsBar.set(10, showMetrics ? Math.min(10, tps / 2) : 0);
            tpsBar.setColor(tpsColor);
            if (tpsValue != null) tpsValue.setTextColor(MinecraftUi.MINIMAL ? MinecraftUi.PANEL_TEXT : 0xFF111111);
        }
    }

    private boolean isGametimeNoise(String line) {
        String t = line == null ? "" : line;
        return t.contains("time query gametime") || t.contains("Gametime is")
                || t.contains("players online:") || t.trim().equals("> list");
    }

    private void updateLogs() {
        if (consoleText == null) return;
        String[] logs = ServerManager.get().getLogs();
        if (logs.length == lastLogCount) return;
        long now = System.currentTimeMillis();
        if (now - lastLogUpdateMs < 350) return;
        lastLogUpdateMs = now;
        lastLogCount = logs.length;
        boolean stick = isConsoleNearBottom();
        int keepY = consoleScroll == null ? 0 : consoleScroll.getScrollY();
        int from = Math.max(0, logs.length - 500);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int[] listRemain = {-1};
        for (int i = from; i < logs.length; i++) {
            String line = logs[i];
            int onlineCnt = ListOutput.parseOnlineCount(line);
            if (onlineCnt >= 0) {
                listRemain[0] = onlineCnt;
                continue;
            }
            if (ListOutput.isNameLine(line, listRemain)) continue;
            if (isGametimeNoise(line) && logColor(line) != MinecraftUi.RED) continue;
            int start = sb.length();
            sb.append(line).append('\n');
            sb.setSpan(new ForegroundColorSpan(
                    ThemeManager.mapTextColor(logColor(line))), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        consoleText.setText(sb.length() == 0 ? "等待服务端日志…" : sb);
        if (consoleScroll != null) {
            final boolean toBottom = stick;
            final int restoreY = keepY;
            consoleScroll.post(new Runnable() {
                @Override public void run() {
                    if (toBottom) {
                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                        consoleScroll.post(new Runnable() {
                            @Override public void run() {
                                consoleScroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    } else {
                        consoleScroll.scrollTo(0, restoreY);
                    }
                }
            });
        }
    }

    /** 控制台是否已贴近底部；只有贴近时才自动跟随新日志。 */
    private boolean isConsoleNearBottom() {
        if (consoleScroll == null) return true;
        View child = consoleScroll.getChildAt(0);
        if (child == null) return true;
        int diff = child.getBottom() - (consoleScroll.getHeight() + consoleScroll.getScrollY());
        return diff <= dp(48);
    }

    private void fetchStatus() {
        final boolean running = ServerManager.get().isRunning();
        io.execute(new Runnable() {
            @Override
            public void run() {
                final String ip = EnvManager.getLanIp(MainActivity.this);
                lanIp = ip;
                int cfgPort = port;
                String cfgWorld = worldName;
                boolean cfgOnline = true;
                if (ShizukuGate.isRunning()) {
                    try {
                        String props = ShizukuShell.read(ShizukuShell.PROPERTIES);
                        for (String line : props.split("\n")) {
                            String t = line.trim();
                            if (t.startsWith("server-port=")) {
                                try {
                                    cfgPort = Integer.parseInt(t.substring("server-port=".length()).trim());
                                } catch (Throwable ignored) {
                                }
                            } else if (t.startsWith("level-name=")) {
                                String v = t.substring("level-name=".length()).trim();
                                if (!v.isEmpty()) cfgWorld = v;
                            } else if (t.startsWith("online-mode=")) {
                                cfgOnline = "true".equalsIgnoreCase(t.substring("online-mode=".length()).trim());
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                final int finalPort = cfgPort;
                final String finalWorld = cfgWorld;
                final boolean finalOnline = cfgOnline;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        port = finalPort;
                        worldName = finalWorld;
                        onlineMode = finalOnline;
                        if (onlineModeValue != null) {
                            onlineModeValue.setText(finalOnline ? "在线模式" : "离线模式");
                            onlineModeValue.setTextColor(finalOnline ? MinecraftUi.GREEN : MinecraftUi.AMBER);
                        }
                        ServerManager.get().setPort(port);
                    }
                });
                if (!running) return;
                String json = null;
                try {
                    json = httpGet("http://127.0.0.1:" + finalPort + "/v1/join", 1400);
                } catch (Throwable ignored) {
                }
                final String body = json;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!ServerManager.get().isRunning()) return;
                        if (body == null) {
                            serverReady = false;
                            if (playersValue != null) {
                                playersValue.setText("连接中…");
                                playersValue.setTextColor(MinecraftUi.AMBER);
                            }
                            return;
                        }
                        try {
                            JSONObject o = new JSONObject(body);
                            int players = o.optInt("players", 0);
                            int max = o.optInt("maxPlayers", 10);
                            String gv = o.optString("version", "");
                            int proto = o.optInt("protocol", 0);
                            String lvl = o.optString("level", "");
                            if (!gv.isEmpty()) {
                                liveGameVersion = gv;
                                liveProtocol = proto;
                                if (gameVersionValue != null) {
                                    gameVersionValue.setText(gv + (proto > 0 ? " · 协议 " + proto : ""));
                                }
                            }
                            if (!lvl.isEmpty()) {
                                liveLevel = lvl;
                                if (worldValue != null) worldValue.setText(port + " · " + lvl);
                            }
                            serverReady = true;
                            if (playersValue != null) {
                                playersValue.setText(players + "/" + max);
                                playersValue.setTextColor(MinecraftUi.GREEN);
                            }
                        } catch (Throwable t) {
                            serverReady = false;
                        }
                    }
                });
            }
        });
    }

    private String httpGet(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod("GET");
        c.setUseCaches(false);
        try {
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
