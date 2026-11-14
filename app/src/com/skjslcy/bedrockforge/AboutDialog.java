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

    private static final String QQ_GROUP_URL =
            "https://qun.qq.com/universal-share/share?ac=1&authKey=aXbbDrFrIwkpxJWFhcclaTZyZFjQDRq%2FQDR0P3gUHSRNAbsG3aD9lb1CbRCc9yGq&busi_data=eyJncm91cENvZGUiOiIxMTI4OTM1NzE5IiwidG9rZW4iOiIveC9SQ3dvNXV1ZUFMNE5wbVFDYnJYSTkxenNoWjk0cy9VNGtxRUdhb3pFU2VZUEptRzJYWHZ3UGZPbGR1TWUvIiwidWluIjoiMTc5NDE5NjkxMCJ9&data=yGAGNaWN2jy6ZnJloDPda8gfQmSA1EsYMxjwmfDp3-KHEv5LlI0GSpgBilPvAARl0bzOz30CQcG-4XWs6yapRw&svctype=4&tempid=h5_group_info";

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
        String name = I18n.t("未知");
        String code = I18n.t("未知");
        String updated = I18n.t("未知");
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            name = pi.versionName == null ? I18n.t("未知") : pi.versionName;
            code = Build.VERSION.SDK_INT >= 28
                    ? String.valueOf(pi.getLongVersionCode()) : String.valueOf(pi.versionCode);
            updated = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(pi.lastUpdateTime));
        } catch (Throwable ignored) {
        }
        final String appName = name;
        final String appCode = code;
        LinearLayout ver = MinecraftUi.panel(a, "版本信息");
        addRow(a, ver, I18n.t("应用版本"), name + I18n.t("（") + code + I18n.t("）"));
        addRow(a, ver, I18n.t("安装/更新时间"), updated);
        addRow(a, ver, I18n.t("包名"), a.getPackageName());
        content.addView(ver, cardLp(a));

        // 简介
        LinearLayout intro = MinecraftUi.panel(a, "简介");
        intro.addView(MinecraftUi.text(a,
                I18n.t("手机上的 Minecraft 基岩版服务端管理工具。\n")
                        + I18n.t("支持运行环境部署、服务端启停、控制台与日志、")
                        + I18n.t("世界与玩家数据管理、备份恢复、addons 管理以及 AI 助手。\n\n")
                        + I18n.t("软件完全免费。"),
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

        Button feedback = new MinecraftUi.McButton(a, "反馈问题");
        links.addView(feedback, btnLp(a, 5));
        feedback.setOnClickListener(v -> openUrl(a,
                "https://github.com/SKJSLCY/bedrock-forge/issues"));

        Button qq = new MinecraftUi.McButton(a, "QQ 交流群（1128935719）");
        links.addView(qq, btnLp(a, 5));
        qq.setOnClickListener(v -> openUrl(a, QQ_GROUP_URL));

        Button license = new MinecraftUi.McButton(a, "开源许可证");
        links.addView(license, btnLp(a, 5));
        license.setOnClickListener(v -> showTextDialog(a,
                "GNU GPL v3.0", reflow(readAsset(a, "legal/gpl-3.0.txt"))));
        content.addView(links, cardLp(a));

        // 环境信息
        final LinkedHashMap<String, TextView> diag = new LinkedHashMap<>();
        LinearLayout env = MinecraftUi.panel(a, "环境信息");
        diag.put("Android", addRow(a, env, "Android",
                Build.VERSION.RELEASE + I18n.t("（API ") + Build.VERSION.SDK_INT + I18n.t("）")));
        diag.put("设备", addRow(a, env, "设备",
                (Build.MANUFACTURER + " " + Build.MODEL).trim()));
        String shz;
        try {
            shz = !ShizukuGate.isRunning() ? I18n.t("未运行")
                    : (ShizukuGate.isAuthorized() ? I18n.t("已授权") : I18n.t("待授权"));
        } catch (Throwable t) {
            shz = I18n.t("未知");
        }
        diag.put("Shizuku", addRow(a, env, "Shizuku", shz));
        addRow(a, env, I18n.t("界面语言"), I18n.label());
        diag.put("Box64", addRow(a, env, "Box64", "读取中…"));
        diag.put("Ubuntu", addRow(a, env, "Ubuntu", "读取中…"));
        diag.put("服务端", addRow(a, env, "服务端", "读取中…"));
        diag.put("世界", addRow(a, env, "世界", "读取中…"));
        Button copy = new MinecraftUi.McButton(a, "复制诊断信息");
        env.addView(copy, btnLp(a, 8));
        copy.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append(I18n.t("基岩工坊 ")).append(appName).append(I18n.t("（")).append(appCode).append("）\n");
            sb.append(I18n.t("包名：")).append(a.getPackageName()).append("\n");
            for (Map.Entry<String, TextView> e : diag.entrySet()) {
                sb.append(e.getKey()).append("：").append(e.getValue().getText()).append("\n");
            }
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(I18n.t("基岩工坊诊断信息"), sb.toString()));
                toast(a, I18n.t("诊断信息已复制"));
            } else {
                toast(a, I18n.t("剪贴板不可用"));
            }
        });
        content.addView(env, cardLp(a));

        new Thread(() -> {
            String box = I18n.t("未部署"), ubuntu = I18n.t("未部署"), bds = I18n.t("未部署"), world = I18n.t("未知");
            try {
                DeployManager.Status rt = DeployManager.detect(a, DeployManager.TYPE_RUNTIME);
                if (rt.installed) {
                    box = rt.box64
                            ? (rt.box64Version == null || rt.box64Version.isEmpty()
                            ? I18n.t("已编译") : rt.box64Version)
                            : I18n.t("未编译");
                    ubuntu = rt.version == null || rt.version.isEmpty() ? I18n.t("未知") : rt.version;
                }
            } catch (Throwable ignored) {
            }
            try {
                DeployManager.Status srv = DeployManager.detect(a, DeployManager.TYPE_SERVER);
                if (srv.installed) bds = srv.version == null || srv.version.isEmpty() ? I18n.t("未知") : srv.version;
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
                    world = w + (port == null || port.isEmpty() ? "" : I18n.t(" · 端口 ") + port);
                }
            } catch (Throwable ignored) {
            }
            final String fBox = box, fUbuntu = ubuntu, fBds = bds, fWorld = world;
            a.runOnUiThread(() -> {
                set(diag, "Box64", fBox);
                set(diag, "Ubuntu", fUbuntu);
                set(diag, I18n.t("服务端"), fBds);
                set(diag, I18n.t("世界"), fWorld);
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
                I18n.t("软件完全免费，赞助自愿，不影响使用与开源权利。\n")
                        + I18n.t("点击按钮前往爱发电赞助作者。"),
                8.5f, MinecraftUi.PANEL_MUTED, false));
        Button sponsorBtn = new MinecraftUi.McButton(a, "赞助作者");
        sponsor.addView(sponsorBtn, btnLp(a, 8));
        sponsorBtn.setOnClickListener(v -> openUrl(a, "https://afdian.com/a/SKJSLCY"));
        content.addView(sponsor, cardLp(a));

        // 兼容性
        LinearLayout compat = MinecraftUi.panel(a, "兼容性说明");
        String compatText =
                I18n.t("最低要求\n")
                        + I18n.t("· Android 8.0（API 26）及以上\n")
                        + I18n.t("· arm64 设备（不支持 32 位 / x86 模拟器）\n")
                        + I18n.t("· Shizuku（shell 权限）\n")
                        + I18n.t("· 至少 4 GB 可用存储\n")
                        + I18n.t("· 建议 6 GB 以上内存\n\n")
                        + I18n.t("网络\n")
                        + I18n.t("· 局域网可直接联机\n")
                        + I18n.t("· 公网联机需要端口映射或内网穿透\n")
                        + I18n.t("· AI 功能需要网络与 API Key\n\n")
                        + I18n.t("已知限制\n")
                        + I18n.t("· Android 11+ 可通过无线调试启动 Shizuku；\n")
                        + I18n.t("  Android 8–10 通常需要 Root 或电脑 ADB\n")
                        + I18n.t("· 部分 ROM 会限制后台，需要关闭电池优化\n")
                        + I18n.t("· Box64 运行 x86_64 服务端有性能损耗\n")
                        + I18n.t("· 已测试 BDS 1.26.60.6 / 1.26.63.1，\n")
                        + I18n.t("  其他版本可导入但不保证全部功能兼容\n")
                        + I18n.t("· 玩家数据编辑依赖 LevelDB 格式，未来版本可能变化\n")
                        + I18n.t("· addons 支持 .mcpack / .zip，不保证第三方兼容\n")
                        + I18n.t("· 不提供公网中继、账号系统或服务器托管\n")
                        + I18n.t("· 修改玩家数据、回滚备份前必须停止服务端\n")
                        + I18n.t("· 删除世界或玩家数据不可恢复，请先备份");
        android.text.SpannableStringBuilder csb =
                new android.text.SpannableStringBuilder(compatText);
        for (String h : new String[]{I18n.t("最低要求"), I18n.t("网络"), I18n.t("已知限制")}) {
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
                I18n.t("开发者：SKJSLCY\n")
                        + I18n.t("制作方式：由 AI 制作\n")
                        + I18n.t("数据与接口：MCAPKS（历史版本列表）、Mojang 官方 BDS 下载\n")
                        + I18n.t("测试与反馈：待补充\n")
                        + I18n.t("开源组件：Box64、proot、Ubuntu Base、Shizuku、AOSP\n")
                        + I18n.t("界面素材：自制像素纹理与图标\n")
                        + I18n.t("特别感谢：所有提交反馈与测试的用户"),
                8.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(credits, cardLp(a));

        // 致谢
        LinearLayout thanks = MinecraftUi.panel(a, "致谢");
        thanks.addView(MinecraftUi.text(a,
                I18n.t("感谢以下开源项目与服务：\n")
                        + "Box64、proot、Ubuntu Base、Shizuku、AOSP\n"
                        + I18n.t("MCAPKS 提供历史版本列表接口\n")
                        + I18n.t("清华大学 TUNA 镜像提供 rootfs 下载\n")
                        + I18n.t("以及所有提交反馈与测试的用户。"),
                8.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(thanks, cardLp(a));

        // 非官方声明
        LinearLayout notice = MinecraftUi.panel(a, "非官方声明");
        notice.addView(MinecraftUi.text(a,
                I18n.t("本软件由 AI 制作。\n")
                        + I18n.t("本软件为非官方工具，与 Mojang Studios、Microsoft 及其关联公司")
                        + I18n.t("无隶属、授权或合作关系。\n")
                        + I18n.t("Minecraft、Minecraft Bedrock Edition 及相关商标归其权利人所有。"),
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

    static void showFaq(final Activity a) {
        String text = I18n.t("Q1：Shizuku 显示未运行\n")
                + I18n.t("A：先在 Shizuku 内启动服务，再回到本应用授予权限。\n\n")
                + I18n.t("Q2：部署卡在编译 Box64\n")
                + I18n.t("A：编译耗时较长，请保持前台并确保存储空间充足；失败可点“修复运行环境”重试。\n\n")
                + I18n.t("Q3：服务端启动失败 / 端口被占用\n")
                + I18n.t("A：检查 server.properties 中的 server-port 是否被其他进程占用，停止后重启。\n\n")
                + I18n.t("Q4：玩家进服被拦截\n")
                + I18n.t("A：检查 allow-list 是否开启、玩家是否在白名单中，必要时在玩家管理中添加。\n\n")
                + I18n.t("Q5：AI 请求失败（401 / 超时）\n")
                + I18n.t("A：401 多为 API Key 无效或未选择模型；超时检查网络与服务商地址。\n\n")
                + I18n.t("Q6：修改玩家数据后未生效\n")
                + I18n.t("A：修改后需要启动服务端；跨版本数据可能被服务端忽略。\n\n")
                + I18n.t("Q7：备份回滚失败\n")
                + I18n.t("A：确认原世界仍存在、服务端已停止，且备份版本不高于当前实例。\n\n")
                + I18n.t("Q8：如何反馈问题\n")
                + I18n.t("A：在关于页复制诊断信息，并附上复现步骤与截图。\n\n")
                + I18n.t("Q9：这个软件会持续维护下去吗？\n")
                + I18n.t("A：会的。\n\n")
                + I18n.t("Q10：这个基岩工坊后续会支持插件加载器吗？\n")
                + I18n.t("A：不会，因为目前做不到。\n\n")
                + I18n.t("Q11：低内存的手机可以开服吗？\n")
                + I18n.t("A：低于8G内存的手机很难。\n\n")
                + I18n.t("Q12：为什么我是花钱买的软件？\n")
                + I18n.t("A：本软件开源完全免费，这个问题你应该问卖家。\n\n")
                + I18n.t("Q13：为什么我用不了AI助手?\n")
                + I18n.t("A：只有输入AI服务商提供给你的APIkey选择模型后才能使用。");
        showTextDialog(a, I18n.t("常见问题"), text);
    }

    static void showChangelog(final Activity a) {
        showTextDialog(a, I18n.t("更新日志"), readAsset(a, "legal/changelog.txt"));
    }

    static void showLicense(final Activity a) {
        showTextDialog(a, "GNU GPL v3.0", reflow(readAsset(a, "legal/gpl-3.0.txt")));
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

    private static String reflow(String s) {
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                out.append("\n\n");
            } else {
                out.append(t).append(' ');
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n");
    }

    private static String readAsset(Activity a, String name) {
        String localized = I18n.asset(name);
        String text = tryRead(a, localized);
        if (text == null && !localized.equals(name)) text = tryRead(a, name);
        if (text != null) return text;
        return I18n.t("读取失败：") + localized;
    }

    private static String tryRead(Activity a, String name) {
        try {
            java.io.InputStream in = a.getAssets().open(name);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            return null;
        }
    }

    private static void set(LinkedHashMap<String, TextView> map, String key, String value) {
        TextView t = map.get(key);
        if (t != null) t.setText(I18n.t(value));
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
            toast(a, I18n.t("无法打开链接：") + url);
        }
    }

    private static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Activity a, int v) {
        return MinecraftUi.dp(a, v);
    }
}
