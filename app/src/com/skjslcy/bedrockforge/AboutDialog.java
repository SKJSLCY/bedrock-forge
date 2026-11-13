package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 关于页面：版本、简介、入口、环境信息、诊断、FAQ、致谢、声明。 */
final class AboutDialog {

    private AboutDialog() {
    }

    static void show(final Activity a) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 8));

        ScrollView sv = new ScrollView(a);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);

        // 标题
        LinearLayout head = MinecraftUi.panel(a, "关于");
        LinearLayout logoBox = new LinearLayout(a);
        logoBox.setGravity(Gravity.CENTER_HORIZONTAL);
        logoBox.setOrientation(LinearLayout.VERTICAL);
        logoBox.addView(new PixelLogoView(a));
        TextView tag = MinecraftUi.text(a, "BEDROCK FORGE", 8, 0xFFD8C7AE, true);
        tag.setLetterSpacing(0.28f);
        tag.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(a, 4);
        logoBox.addView(tag, tlp);
        head.addView(logoBox);
        content.addView(head, cardLp(a));

        // 版本
        String name = "未知";
        String code = "未知";
        String updated = "未知";
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            name = pi.versionName == null ? "未知" : pi.versionName;
            code = Build.VERSION.SDK_INT >= 28
                    ? String.valueOf(pi.getLongVersionCode()) : String.valueOf(pi.versionCode);
            updated = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(pi.lastUpdateTime));
        } catch (Throwable ignored) {
        }
        final String appName = name;
        final String appCode = code;
        LinearLayout ver = MinecraftUi.panel(a, "版本信息");
        addRow(a, ver, "应用版本", name + "（" + code + "）");
        addRow(a, ver, "安装/更新时间", updated);
        addRow(a, ver, "包名", a.getPackageName());
        content.addView(ver, cardLp(a));

        // 简介
        LinearLayout intro = MinecraftUi.panel(a, "简介");
        intro.addView(MinecraftUi.text(a,
                "手机上的 Minecraft 基岩版服务端管理工具。\n"
                        + "支持运行环境部署、服务端启停、控制台与日志、"
                        + "世界与玩家数据管理、备份恢复、addons 管理以及 AI 助手。\n\n"
                        + "软件完全免费。",
                9f, MinecraftUi.PANEL_TEXT, false));
        content.addView(intro, cardLp(a));

        // 快捷入口
        LinearLayout links = MinecraftUi.panel(a, "快捷入口");
        Button agreement = new MinecraftUi.McButton(a, "用户协议");
        links.addView(agreement, btnLp(a, 0));
        agreement.setOnClickListener(v -> AgreementDialog.show(a, false, null));

        Button repo = new MinecraftUi.McButton(a, "项目仓库");
        links.addView(repo, btnLp(a, 5));
        repo.setOnClickListener(v -> openUrl(a, "https://github.com/SKJSLCY/bedrock-forge"));

        Button feedback = new MinecraftUi.McButton(a, "反馈问题（GitHub Issues）");
        links.addView(feedback, btnLp(a, 5));
        feedback.setOnClickListener(v -> openUrl(a,
                "https://github.com/SKJSLCY/bedrock-forge/issues"));

        Button license = new MinecraftUi.McButton(a, "开源许可证（GPL-3.0）");
        links.addView(license, btnLp(a, 5));
        license.setOnClickListener(v -> showTextDialog(a,
                "GNU GPL v3.0", readAsset(a, "legal/gpl-3.0.txt")));
        content.addView(links, cardLp(a));

        // 环境信息
        final LinkedHashMap<String, TextView> diag = new LinkedHashMap<>();
        LinearLayout env = MinecraftUi.panel(a, "环境信息");
        diag.put("Android", addRow(a, env, "Android",
                Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）"));
        diag.put("设备", addRow(a, env, "设备",
                (Build.MANUFACTURER + " " + Build.MODEL).trim()));
        String shz;
        try {
            shz = !ShizukuGate.isRunning() ? "未运行"
                    : (ShizukuGate.isAuthorized() ? "已授权" : "待授权");
        } catch (Throwable t) {
            shz = "未知";
        }
        diag.put("Shizuku", addRow(a, env, "Shizuku", shz));
        diag.put("Box64", addRow(a, env, "Box64", "读取中…"));
        diag.put("Ubuntu", addRow(a, env, "Ubuntu", "读取中…"));
        diag.put("服务端", addRow(a, env, "服务端", "读取中…"));
        diag.put("世界", addRow(a, env, "世界", "读取中…"));
        Button copy = new MinecraftUi.McButton(a, "复制诊断信息");
        env.addView(copy, btnLp(a, 8));
        copy.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append("基岩工坊 ").append(appName).append("（").append(appCode).append("）\n");
            sb.append("包名：").append(a.getPackageName()).append("\n");
            for (Map.Entry<String, TextView> e : diag.entrySet()) {
                sb.append(e.getKey()).append("：").append(e.getValue().getText()).append("\n");
            }
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("基岩工坊诊断信息", sb.toString()));
                toast(a, "诊断信息已复制");
            } else {
                toast(a, "剪贴板不可用");
            }
        });
        content.addView(env, cardLp(a));

        new Thread(() -> {
            String box = "未部署", ubuntu = "未部署", bds = "未部署", world = "未知";
            try {
                DeployManager.Status rt = DeployManager.detect(a, DeployManager.TYPE_RUNTIME);
                if (rt.installed) {
                    box = rt.box64
                            ? (rt.box64Version == null || rt.box64Version.isEmpty()
                            ? "已编译" : rt.box64Version)
                            : "未编译";
                    ubuntu = rt.version == null || rt.version.isEmpty() ? "未知" : rt.version;
                }
            } catch (Throwable ignored) {
            }
            try {
                DeployManager.Status srv = DeployManager.detect(a, DeployManager.TYPE_SERVER);
                if (srv.installed) bds = srv.version == null || srv.version.isEmpty() ? "未知" : srv.version;
            } catch (Throwable ignored) {
            }
            try {
                String props = ShizukuShell.read(
                        DeployManager.SERVER_DIR + "/server.properties");
                String w = null, port = null;
                for (String line : props.split("\n")) {
                    String s = line.trim();
                    if (s.startsWith("level-name=")) w = s.substring("level-name=".length()).trim();
                    if (s.startsWith("server-port=")) port = s.substring("server-port=".length()).trim();
                }
                if (w != null && !w.isEmpty()) {
                    world = w + (port == null || port.isEmpty() ? "" : " · 端口 " + port);
                }
            } catch (Throwable ignored) {
            }
            final String fBox = box, fUbuntu = ubuntu, fBds = bds, fWorld = world;
            a.runOnUiThread(() -> {
                set(diag, "Box64", fBox);
                set(diag, "Ubuntu", fUbuntu);
                set(diag, "服务端", fBds);
                set(diag, "世界", fWorld);
            });
        }, "about-diagnostics").start();

        // 帮助
        LinearLayout help = MinecraftUi.panel(a, "帮助");
        Button faq = new MinecraftUi.McButton(a, "常见问题");
        help.addView(faq, btnLp(a, 0));
        faq.setOnClickListener(v -> showFaq(a));
        Button changelog = new MinecraftUi.McButton(a, "更新日志");
        help.addView(changelog, btnLp(a, 5));
        changelog.setOnClickListener(v -> showChangelog(a));
        content.addView(help, cardLp(a));

        // 支持作者
        LinearLayout sponsor = MinecraftUi.panel(a, "支持作者");
        sponsor.addView(MinecraftUi.text(a,
                "软件完全免费，赞助自愿，不影响使用与开源权利。\n"
                        + "赞助方式尚未配置。",
                8.5f, MinecraftUi.PANEL_MUTED, false));
        Button sponsorBtn = new MinecraftUi.McButton(a, "赞助作者（未配置）");
        sponsor.addView(sponsorBtn, btnLp(a, 8));
        sponsorBtn.setOnClickListener(v -> toast(a, "赞助方式尚未配置"));
        content.addView(sponsor, cardLp(a));

        // 兼容性
        LinearLayout compat = MinecraftUi.panel(a, "兼容性说明");
        String compatText =
                "最低要求\n"
                        + "· Android 8.0（API 26）及以上\n"
                        + "· arm64 设备（不支持 32 位 / x86 模拟器）\n"
                        + "· Shizuku（shell 权限）\n"
                        + "· 至少 4 GB 可用存储\n"
                        + "· 建议 6 GB 以上内存\n\n"
                        + "网络\n"
                        + "· 局域网可直接联机\n"
                        + "· 公网联机需要端口映射或内网穿透\n"
                        + "· AI 功能需要网络与 API Key\n\n"
                        + "已知限制\n"
                        + "· Android 11+ 可通过无线调试启动 Shizuku；\n"
                        + "  Android 8–10 通常需要 Root 或电脑 ADB\n"
                        + "· 部分 ROM 会限制后台，需要关闭电池优化\n"
                        + "· Box64 运行 x86_64 服务端有性能损耗\n"
                        + "· 已测试 BDS 1.26.60.6 / 1.26.63.1，\n"
                        + "  其他版本可导入但不保证全部功能兼容\n"
                        + "· 玩家数据编辑依赖 LevelDB 格式，未来版本可能变化\n"
                        + "· addons 支持 .mcpack / .zip，不保证第三方兼容\n"
                        + "· 不提供公网中继、账号系统或服务器托管\n"
                        + "· 修改玩家数据、回滚备份前必须停止服务端\n"
                        + "· 删除世界或玩家数据不可恢复，请先备份";
        android.text.SpannableStringBuilder csb =
                new android.text.SpannableStringBuilder(compatText);
        for (String h : new String[]{"最低要求", "网络", "已知限制"}) {
            int idx = compatText.indexOf(h);
            if (idx >= 0) {
                csb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        idx, idx + h.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                csb.setSpan(new android.text.style.ForegroundColorSpan(0xFF3A3A3A),
                        idx, idx + h.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        android.widget.TextView compatView = MinecraftUi.text(a, "", 8.5f,
                MinecraftUi.PANEL_MUTED, false);
        compatView.setText(csb);
        compat.addView(compatView);
        content.addView(compat, cardLp(a));

        // 贡献者
        LinearLayout credits = MinecraftUi.panel(a, "贡献者名单");
        credits.addView(MinecraftUi.text(a,
                "开发者：SKJSLCY\n"
                        + "制作方式：由 AI 制作\n"
                        + "数据与接口：MCAPKS（历史版本列表）、Mojang 官方 BDS 下载\n"
                        + "测试与反馈：待补充\n"
                        + "开源组件：Box64、proot、Ubuntu Base、Shizuku、AOSP\n"
                        + "界面素材：自制像素纹理与图标\n"
                        + "特别感谢：所有提交反馈与测试的用户",
                8.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(credits, cardLp(a));

        // 致谢
        LinearLayout thanks = MinecraftUi.panel(a, "致谢");
        thanks.addView(MinecraftUi.text(a,
                "感谢以下开源项目与服务：\n"
                        + "Box64、proot、Ubuntu Base、Shizuku、AOSP\n"
                        + "MCAPKS 提供历史版本列表接口\n"
                        + "清华大学 TUNA 镜像提供 rootfs 下载\n"
                        + "以及所有提交反馈与测试的用户。",
                8.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(thanks, cardLp(a));

        // 非官方声明
        LinearLayout notice = MinecraftUi.panel(a, "非官方声明");
        notice.addView(MinecraftUi.text(a,
                "本软件由 AI 制作。\n"
                        + "本软件为非官方工具，与 Mojang Studios、Microsoft 及其关联公司"
                        + "无隶属、授权或合作关系。\n"
                        + "Minecraft、Minecraft Bedrock Edition 及相关商标归其权利人所有。",
                8.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(notice, cardLp(a));

        wrap.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new MinecraftUi.McButton(a, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44));
        clp.topMargin = dp(a, 6);
        wrap.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());

        d.setContentView(wrap, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (a.getResources().getDisplayMetrics().heightPixels * 0.88f));
        }
    }

    private static void showFaq(final Activity a) {
        String text = "Q1：Shizuku 显示未运行\n"
                + "A：先在 Shizuku 内启动服务，再回到本应用授予权限。\n\n"
                + "Q2：部署卡在编译 Box64\n"
                + "A：编译耗时较长，请保持前台并确保存储空间充足；失败可点“修复运行环境”重试。\n\n"
                + "Q3：服务端启动失败 / 端口被占用\n"
                + "A：检查 server.properties 中的 server-port 是否被其他进程占用，停止后重启。\n\n"
                + "Q4：玩家进服被拦截\n"
                + "A：检查 allow-list 是否开启、玩家是否在白名单中，必要时在玩家管理中添加。\n\n"
                + "Q5：AI 请求失败（401 / 超时）\n"
                + "A：401 多为 API Key 无效或未选择模型；超时检查网络与服务商地址。\n\n"
                + "Q6：修改玩家数据后未生效\n"
                + "A：修改后需要启动服务端；跨版本数据可能被服务端忽略。\n\n"
                + "Q7：备份回滚失败\n"
                + "A：确认原世界仍存在、服务端已停止，且备份版本不高于当前实例。\n\n"
                + "Q8：如何反馈问题\n"
                + "A：在关于页复制诊断信息，并附上复现步骤与截图。";
        showTextDialog(a, "常见问题", text);
    }

    private static void showChangelog(final Activity a) {
        showTextDialog(a, "更新日志", readAsset(a, "legal/changelog.txt"));
    }

    private static void showTextDialog(final Activity a, String title, String text) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 8));
        LinearLayout panel = MinecraftUi.panel(a, title);
        TextView body = MinecraftUi.text(a, text, 9f, MinecraftUi.PANEL_TEXT, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(a, 2), 1.05f);
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new MinecraftUi.McButton(a, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44));
        clp.topMargin = dp(a, 6);
        wrap.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        d.setContentView(wrap, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (a.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
    }

    private static String readAsset(Activity a, String name) {
        try {
            java.io.InputStream in = a.getAssets().open(name);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            return "读取失败：" + e.getMessage();
        }
    }

    private static void set(LinkedHashMap<String, TextView> map, String key, String value) {
        TextView t = map.get(key);
        if (t != null) t.setText(value);
    }

    private static TextView addRow(Activity a, LinearLayout panel, String label, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setBackground(MinecraftUi.slotDrawable(a));
        row.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        TextView k = MinecraftUi.text(a, label, 8.5f, MinecraftUi.PANEL_MUTED, false);
        row.addView(k, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f));
        TextView v = MinecraftUi.text(a, value, 9f, MinecraftUi.PANEL_TEXT, false);
        row.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 4);
        panel.addView(row, lp);
        return v;
    }

    private static LinearLayout.LayoutParams cardLp(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        return lp;
    }

    private static LinearLayout.LayoutParams btnLp(Activity a, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 42));
        lp.topMargin = dp(a, top);
        return lp;
    }

    private static void openUrl(Activity a, String url) {
        try {
            android.content.Intent i = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
            a.startActivity(i);
        } catch (Throwable t) {
            toast(a, "无法打开链接：" + url);
        }
    }

    private static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Activity a, int v) {
        return MinecraftUi.dp(a, v);
    }
}
