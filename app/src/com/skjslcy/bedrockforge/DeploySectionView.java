package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 服务端 / 运行环境的部署页面。 */
public class DeploySectionView extends LinearLayout {

    public interface Host {
        void pickFile(PickCallback callback);
    }

    public interface PickCallback {
        void onPicked(File file);
        void onCancel();
    }

    private final Activity activity;
    private final int type;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final LinearLayout statusHost;
    private final Button actionBtn;
    private final Button deleteBtn;
    private final Button backupBtn;
    private final Button rollbackBtn;
    private final LinearLayout progressBox;
    private final ProgressBar progressBar;
    private final TextView progressText;
    private final TextView progressLog;

    private DeployManager.Status status;
    private boolean busy;
    private String upgradeSourceId;
    private boolean forceNewInstance;
    private Runnable statePoll;

    public DeploySectionView(Activity activity, int type, Host host) {
        super(activity);
        this.activity = activity;
        this.type = type;
        this.host = host;
        setOrientation(VERTICAL);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusHost = new LinearLayout(activity);
        statusHost.setOrientation(VERTICAL);
        body.addView(statusHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actionBtn = new MinecraftUi.McButton(activity, "检查中…");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        alp.topMargin = dp(8);
        body.addView(actionBtn, alp);
        actionBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (type == DeployManager.TYPE_RUNTIME) {
                    if (status != null && status.installed) {
                        showRepairConfirm();
                    } else {
                        showBox64Install();
                    }
                    return;
                }
                if (type == DeployManager.TYPE_SERVER) {
                    forceNewInstance = true;
                    showSourceDialog();
                    return;
                }
                if (status != null && status.installed) {
                    askBackupThen(new Runnable() {
                        @Override public void run() { showSourceDialog(); }
                    });
                } else {
                    showSourceDialog();
                }
            }
        });

        deleteBtn = new MinecraftUi.McButton(activity, "删除" + typeName());
        if (deleteBtn instanceof MinecraftUi.McButton) {
            ((MinecraftUi.McButton) deleteBtn).setDangerStyle();
        }
        deleteBtn.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        dlp.topMargin = dp(6);
        body.addView(deleteBtn, dlp);
        deleteBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { confirmDelete(); }
        });

        backupBtn = new MinecraftUi.McButton(activity, "备份当前" + typeName());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        blp.topMargin = dp(6);
        body.addView(backupBtn, blp);
        backupBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { backupNow(); }
        });

        rollbackBtn = new MinecraftUi.McButton(activity, "回滚");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rlp.topMargin = dp(5);
        body.addView(rollbackBtn, rlp);
        rollbackBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { showRollback(); }
        });

        progressBox = MinecraftUi.panel(activity, "部署进度");
        progressBox.setVisibility(GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(10);
        body.addView(progressBox, plp);

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(MinecraftUi.GREEN_BRIGHT));
        progressBox.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14)));

        progressText = MinecraftUi.text(activity, "", 10, MinecraftUi.PANEL_TEXT, true);
        progressText.setPadding(0, dp(6), 0, 0);
        progressBox.addView(progressText);

        progressLog = MinecraftUi.text(activity, "", 8.5f, MinecraftUi.PANEL_MUTED, false);
        progressLog.setPadding(0, dp(4), 0, 0);
        progressBox.addView(progressLog);

        if (type == DeployManager.TYPE_SERVER) {
            TextView credit = MinecraftUi.text(activity,
                    "实例版本由 MCAPKS 提供 · 官方地址下载", 8, MinecraftUi.PANEL_MUTED, false);
            credit.setPadding(0, dp(8), 0, 0);
            body.addView(credit);
        }

        refresh();
        restoreDeployState();
    }

    private void restoreDeployState() {
        DeployState.Snapshot s = DeployState.snapshot();
        if (!s.busy || s.type != type) return;
        busy = true;
        progressBox.setVisibility(VISIBLE);
        progressBar.setProgress(s.percent);
        progressText.setText(s.percent + "% · " + s.message);
        progressLog.setText("> " + s.message);
        actionBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        backupBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);
        actionBtn.setAlpha(0.55f);
        deleteBtn.setAlpha(0.55f);
        backupBtn.setAlpha(0.55f);
        rollbackBtn.setAlpha(0.55f);
        startStatePoll();
    }

    private void startStatePoll() {
        if (statePoll != null) handler.removeCallbacks(statePoll);
        statePoll = new Runnable() {
            @Override public void run() {
                if (getWindowToken() == null) {
                    statePoll = null;
                    return;
                }
                DeployState.Snapshot s = DeployState.snapshot();
                if (s.type != type) {
                    statePoll = null;
                    return;
                }
                if (s.busy) {
                    busy = true;
                    progressBox.setVisibility(VISIBLE);
                    progressBar.setProgress(s.percent);
                    progressText.setText(s.percent + "% · " + s.message);
                    if (progressLog.getText().toString().isEmpty()) {
                        progressLog.setText("> " + s.message);
                    }
                    handler.postDelayed(this, 500);
                } else {
                    busy = false;
                    progressBox.setVisibility(GONE);
                    actionBtn.setEnabled(true);
                    deleteBtn.setEnabled(true);
                    backupBtn.setEnabled(true);
                    rollbackBtn.setEnabled(true);
                    actionBtn.setAlpha(1f);
                    deleteBtn.setAlpha(1f);
                    backupBtn.setAlpha(1f);
                    rollbackBtn.setAlpha(1f);
                    statePoll = null;
                    refresh();
                }
            }
        };
        handler.post(statePoll);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (statePoll != null) {
            handler.removeCallbacks(statePoll);
            statePoll = null;
        }
        super.onDetachedFromWindow();
    }

    private void refresh() {
        statusHost.removeAllViews();
        LinearLayout loading = MinecraftUi.panel(activity, typeName());
        loading.addView(MinecraftUi.text(activity, "正在检测本地部署…", 10, MinecraftUi.PANEL_MUTED, false));
        statusHost.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionBtn.setEnabled(false);
        deleteBtn.setVisibility(GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final DeployManager.Status s = DeployManager.detect(activity, type);
                    handler.post(new Runnable() {
                        @Override public void run() { render(s); }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() { renderError(e.getMessage()); }
                    });
                }
            }
        }, "deploy-detect").start();
    }

    private void render(DeployManager.Status s) {
        status = s;
        statusHost.removeAllViews();
        LinearLayout panel = MinecraftUi.panel(activity, typeName() + " · 本地部署");
        statusHost.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (s.installed) {
            addRow(panel, "名称", s.name);
            addRow(panel, "版本", s.version);
            if (type == DeployManager.TYPE_RUNTIME) {
                addRow(panel, "Box64",
                        s.box64Version == null || s.box64Version.isEmpty() ? "已编译" : s.box64Version);
            }
            addRow(panel, "路径", s.path);
            addRow(panel, "来源", s.source == null ? "" : s.source
                    .replace("MSL 镜像", "官方地址")
                    .replace("MSL", "官方地址"));
            if (s.note != null && !s.note.isEmpty()) {
                TextView note = MinecraftUi.text(activity, s.note, 8.5f, MinecraftUi.PANEL_MUTED, false);
                note.setPadding(0, dp(7), 0, 0);
                panel.addView(note);
            }
        } else {
            TextView empty = MinecraftUi.text(activity, "未部署", 15, MinecraftUi.PANEL_TEXT, true);
            empty.setPadding(0, dp(4), 0, dp(2));
            panel.addView(empty);
            TextView hint = MinecraftUi.text(activity, emptyHint(), 9, MinecraftUi.PANEL_MUTED, false);
            panel.addView(hint);
            if (type == DeployManager.TYPE_RUNTIME) {
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = dp(6);
                LinearLayout box = new LinearLayout(activity);
                box.setBackground(MinecraftUi.slotDrawable(activity));
                box.setPadding(dp(8), dp(6), dp(8), dp(6));
                box.addView(MinecraftUi.text(activity, "Box64：", 9,
                        MinecraftUi.PANEL_MUTED, true), new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                box.addView(MinecraftUi.text(activity, "未编译", 9.5f,
                        MinecraftUi.PANEL_TEXT, false));
                panel.addView(box, blp);
            }
        }

        actionBtn.setEnabled(true);
        if (type == DeployManager.TYPE_SERVER) {
            actionBtn.setText(s.installed ? "新增实例" : "添加服务端");
        } else {
            actionBtn.setText(s.installed ? "更换" + typeName() : "添加" + typeName());
        }
        deleteBtn.setVisibility(s.installed ? VISIBLE : GONE);
        deleteBtn.setEnabled(true);
        backupBtn.setVisibility(s.installed ? VISIBLE : GONE);
        rollbackBtn.setVisibility(s.installed ? VISIBLE : GONE);
        if (type == DeployManager.TYPE_SERVER) {
            backupBtn.setVisibility(GONE);
            rollbackBtn.setVisibility(GONE);
        }
        if (type == DeployManager.TYPE_RUNTIME && s.box64) {
            actionBtn.setText("修复运行环境");
            if (actionBtn instanceof MinecraftUi.McButton) {
                ((MinecraftUi.McButton) actionBtn).setDangerStyle();
            }
            actionBtn.setTextColor(0xFFFFF2F2);
            actionBtn.setEnabled(true);
            actionBtn.setAlpha(1f);
            deleteBtn.setVisibility(GONE);
            backupBtn.setVisibility(GONE);
            rollbackBtn.setVisibility(GONE);
        }
        if (progressBox.getVisibility() != VISIBLE) {
            actionBtn.setAlpha(1f);
            deleteBtn.setAlpha(1f);
        }

        if (type == DeployManager.TYPE_SERVER && s.installed && s.versions != null && s.versions.size() > 0) {
            LinearLayout versions = MinecraftUi.panel(activity, "服务端实例");
            TextView hint = MinecraftUi.text(activity,
                    "点击实例可重命名、回滚、升级；非当前实例还可切换或删除。", 8, MinecraftUi.PANEL_MUTED, false);
            versions.addView(hint);
            for (int i = 0; i < s.versions.size(); i++) {
                final String version = s.versions.get(i);
                boolean active = version.equals(s.activeVersion);
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(5);
                versions.addView(row, rlp);

                String display = version;
                try {
                    InstanceStore.Instance ins = InstanceStore.read(version);
                    if (ins.name != null && !ins.name.isEmpty()) display = ins.name;
                } catch (Throwable ignored) {
                }
                Button b = new MinecraftUi.McButton(activity,
                        display + (active ? "（当前）" : ""));
                b.setSingleLine(true);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                b.setEllipsize(android.text.TextUtils.TruncateAt.END);
                b.setEnabled(true);
                b.setAlpha(1f);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(42), 1f);
                row.addView(b, blp);
                b.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) { showInstanceActions(version, active); }
                });
            }
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vlp.topMargin = dp(8);
            statusHost.addView(versions, vlp);
        }

        DeployState.Snapshot ds = DeployState.snapshot();
        if (ds.busy && ds.type == type) {
            busy = true;
            progressBox.setVisibility(VISIBLE);
            progressBar.setProgress(ds.percent);
            progressText.setText(ds.percent + "% · " + ds.message);
            actionBtn.setEnabled(false);
            deleteBtn.setEnabled(false);
            backupBtn.setEnabled(false);
            rollbackBtn.setEnabled(false);
            actionBtn.setAlpha(0.55f);
            deleteBtn.setAlpha(0.55f);
            backupBtn.setAlpha(0.55f);
            rollbackBtn.setAlpha(0.55f);
        }
    }

    private void renderError(String message) {
        statusHost.removeAllViews();
        LinearLayout panel = MinecraftUi.panel(activity, "检测失败");
        TextView t = MinecraftUi.text(activity, message == null ? "未知错误" : message,
                9.5f, MinecraftUi.RED, false);
        panel.addView(t);

        if (!ShizukuGate.isRunning()) {
            Button guide = new MinecraftUi.McButton(activity, "启动 Shizuku");
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            glp.topMargin = dp(6);
            panel.addView(guide, glp);
            guide.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    ShizukuGate.showDialog(activity, "检测运行环境", null);
                }
            });
        }

        Button retry = new MinecraftUi.McButton(activity, "重试");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(6);
        panel.addView(retry, lp);
        retry.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        statusHost.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionBtn.setEnabled(false);
        actionBtn.setText("不可用");
        deleteBtn.setVisibility(GONE);
        backupBtn.setVisibility(GONE);
        rollbackBtn.setVisibility(GONE);
    }

    private void addRow(LinearLayout panel, String label, String value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(VERTICAL);
        box.setBackground(MinecraftUi.slotDrawable(activity));
        box.setPadding(dp(9), dp(7), dp(9), dp(7));

        TextView k = MinecraftUi.text(activity, label, 9, MinecraftUi.PANEL_MUTED, true);
        box.addView(k);
        TextView v = MinecraftUi.text(activity, value == null ? "" : value, 11,
                MinecraftUi.PANEL_TEXT, true);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5);
        panel.addView(box, lp);
    }

    private String typeName() {
        if (type == DeployManager.TYPE_SERVER) return "服务端";
        if (type == DeployManager.TYPE_RUNTIME) return "运行环境";
        return "运行环境";
    }

    private String emptyHint() {
        if (type == DeployManager.TYPE_SERVER) {
            return "通过实例下载获取官方 BDS，或导入设备上的 Linux 服务端 ZIP。";
        }
        if (type == DeployManager.TYPE_RUNTIME) {
            return "下载 Ubuntu 24.04.3 rootfs，或导入完整的 Linux 运行环境包。";
        }
        return "";
    }

    private void showRepairConfirm() {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "修复运行环境");
        panel.addView(MinecraftUi.text(activity,
                "此操作会删除以下全部数据，且不可撤销：\n\n"
                        + "· 所有服务端实例（世界存档、配置、白名单、权限）\n"
                        + "· 现有 Box64 / Ubuntu 运行环境\n\n"
                        + "备份文件不会被删除，可在“备份”页面回滚恢复。",
                9.5f, MinecraftUi.RED, false));
        Button go = new MinecraftUi.McButton(activity, "我已确认，删除并重建");
        if (go instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) go).setDangerStyle();
        go.setTextSize(12);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        glp.topMargin = dp(8);
        panel.addView(go, glp);
        go.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                startRepairRuntime();
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window dw = d.getWindow();
        if (dw != null) {
            dw.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void startRepairRuntime() {
        DeployManager.setRepairingRuntime(true);
        startDeploy("修复运行环境", new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                try {
                    DeployManager.wipeServersAndState(activity, p);
                } catch (Exception e) {
                    DeployManager.setRepairingRuntime(false);
                    throw e;
                }
                handler.post(new Runnable() {
                    @Override public void run() { refresh(); }
                });
                Box64Installer.install(activity, tail(p, 10), 1);
            }
        }, new Runnable() {
            @Override public void run() {
                DeployManager.setRepairingRuntime(false);
                refresh();
            }
        });
    }

    private void showBox64Install() {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout panel = MinecraftUi.panel(activity, "部署 Box64 运行环境");
        TextView text = MinecraftUi.text(activity,
                "将按文档在 App 内从零部署：\n\n"
                        + "1. 下载 Ubuntu 24.04.5 ARM64 rootfs\n"
                        + "2. 配置 DNS 和清华软件源\n"
                        + "3. 安装编译工具与 x86_64 运行库\n"
                        + "4. 下载 Box64 源码\n"
                        + "5. 单线程编译并安装 Box64\n\n"
                        + "仅依赖内置工具链与 Box64；需要 Shizuku。\n"
                        + "编译约 20–40 分钟，请保持 App 在前台并连接网络。",
                10, MinecraftUi.PANEL_TEXT, false);
        panel.addView(text);

        Button j1 = new MinecraftUi.McButton(activity, "-j1 单线程 · 稳定");
        j1.setTextSize(13.5f);
        LinearLayout.LayoutParams j1lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        j1lp.topMargin = dp(8);
        panel.addView(j1, j1lp);
        j1.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                startBox64Install(1);
            }
        });

        Button j2 = new MinecraftUi.McButton(activity, "-j2 双线程 · 较快");
        j2.setTextSize(13.5f);
        LinearLayout.LayoutParams j2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        j2lp.topMargin = dp(5);
        panel.addView(j2, j2lp);
        j2.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                startBox64Install(2);
            }
        });

        Button j4 = new MinecraftUi.McButton(activity, "-j4 四线程 · 高风险");
        j4.setTextSize(13.5f);
        LinearLayout.LayoutParams j4lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        j4lp.topMargin = dp(5);
        panel.addView(j4, j4lp);
        j4.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                startBox64Install(4);
            }
        });

        TextView warn = MinecraftUi.text(activity,
                "线程越多编译越快，但内存峰值和发热越高；8GB 手机建议 -j1 或 -j2。",
                8.5f, MinecraftUi.PANEL_MUTED, false);
        warn.setPadding(0, dp(6), 0, 0);
        panel.addView(warn);

        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        wrap.addView(panel);
        dialog.setContentView(wrap);
        dialog.show();
    }

    private void startBox64Install(final int threads) {
        startDeploy("部署 Box64 运行环境 -j" + threads, new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                Box64Installer.install(activity, p, threads);
            }
        }, new Runnable() {
            @Override public void run() { refresh(); }
        });
    }

    private void showSourceDialog() {
        if (type == DeployManager.TYPE_SERVER) upgradeSourceId = null;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout panel = MinecraftUi.panel(activity,
                type == DeployManager.TYPE_SERVER ? "添加实例" : ((status != null && status.installed ? "更换" : "添加") + typeName()));
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (type == DeployManager.TYPE_SERVER) {
            addOption(panel, "实例下载", new Runnable() {
                @Override public void run() { dialog.dismiss(); chooseServerVersion(); }
            }, 0);
            addOption(panel, "从设备导入 ZIP / TAR.GZ", new Runnable() {
                @Override public void run() { dialog.dismiss(); pickLocal(); }
            }, 5);
            TextView credit = MinecraftUi.text(activity,
                    "版本列表由 MCAPKS 提供 · 通过官方地址下载", 8,
                    MinecraftUi.PANEL_MUTED, false);
            credit.setPadding(0, dp(8), 0, 0);
            panel.addView(credit);
        }
        Button close = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        clp.topMargin = dp(8);
        panel.addView(close, clp);
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addOption(LinearLayout panel, String label, final Runnable action, int topDp) {
        Button b = new MinecraftUi.McButton(activity, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(topDp);
        panel.addView(b, lp);
        b.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
    }

    private void chooseServerVersion() {
        final Dialog loading = simpleDialog("获取版本列表", "正在请求实例版本列表…");
        loading.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> versions = DeployManager.serverVersions();
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            showVersionList(versions);
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("获取版本失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "deploy-versions").start();
    }

    private int compareBds(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? parseIntPart(x[i]) : 0;
            int yi = i < y.length ? parseIntPart(y[i]) : 0;
            if (xi != yi) return xi < yi ? -1 : 1;
        }
        return 0;
    }

    private int parseIntPart(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Throwable t) { return 0; }
    }

    private void showVersionList(List<String> versions) {
        if (upgradeSourceId != null) {
            String current = "";
            try { current = InstanceStore.read(upgradeSourceId).bdsVersion; } catch (Throwable ignored) {}
            List<String> higher = new ArrayList<>();
            for (String v : versions) {
                if (compareBds(v, current) > 0) higher.add(v);
            }
            versions = higher;
            if (versions.isEmpty()) {
                toast("没有高于当前实例 " + current + " 的 BDS 版本");
                return;
            }
        }
        if (versions.isEmpty()) {
            toast("镜像没有 Linux 正式版");
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "选择服务端版本");
        ScrollView sv = new ScrollView(activity);
        sv.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(440), dp(56) * versions.size() + dp(20))));

        for (int i = 0; i < versions.size(); i++) {
            final String version = versions.get(i);
            Button b = new MinecraftUi.McButton(activity, version);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            lp.topMargin = i == 0 ? 0 : dp(4);
            panel.addView(b, lp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    dialog.dismiss();
                    if (upgradeSourceId != null) {
                        final String source = upgradeSourceId;
                        upgradeSourceId = null;
                        startInstanceUpgrade(version, source);
                    } else {
                        confirmDeploy("部署服务端 " + version, new Runnable() {
                            @Override public void run() { downloadServer(version); }
                        });
                    }
                }
            });
        }
        TextView credit = MinecraftUi.text(activity, "实例版本由 MCAPKS 提供 · 官方地址下载", 8,
                MinecraftUi.PANEL_MUTED, false);
        credit.setPadding(0, dp(8), 0, 0);
        panel.addView(credit);

        Button close = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        wrap.addView(close, clp);
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void downloadServer(final String version) {
        if (forceNewInstance) {
            forceNewInstance = false;
            promptInstanceName(version);
            return;
        }
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "部署方式 · " + version);
        panel.addView(MinecraftUi.text(activity,
                "升级当前实例会保留当前世界、配置、白名单和权限。\n新建独立实例会创建空白世界，不影响当前实例。",
                9, MinecraftUi.PANEL_MUTED, false));
        Button keep = new MinecraftUi.McButton(activity, "升级当前实例（保留世界）");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp1.topMargin = dp(8);
        panel.addView(keep, lp1);
        keep.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                startDownloadServer(version, false);
            }
        });
        Button fresh = new MinecraftUi.McButton(activity, "新建独立实例（空白世界）");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp2.topMargin = dp(5);
        panel.addView(fresh, lp2);
        fresh.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                promptInstanceName(version);
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp3.topMargin = dp(5);
        panel.addView(cancel, lp3);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void promptInstanceName(final String version) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "实例名称");
        panel.addView(MinecraftUi.text(activity,
                "只支持字母和数字，不支持中文、空格、下划线、横线或其他符号。\n最长 32 个字符。",
                9, MinecraftUi.AMBER, false));
        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setHint("例如 Survival1");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ilp.topMargin = dp(8);
        panel.addView(input, ilp);
        Button ok = new MinecraftUi.McButton(activity, "确定并开始部署");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(8);
        panel.addView(ok, lp);
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                String name = input.getText().toString().trim();
                if (!name.matches("[A-Za-z0-9]{1,32}")) {
                    toast("实例名只支持字母和数字，最长 32 个字符");
                    return;
                }
                d.dismiss();
                startDownloadServer(version, true, name);
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { forceNewInstance = false; d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
    }

    private void startDownloadServer(final String version, final boolean fresh) {
        startDownloadServer(version, fresh, null);
    }

    private void startDownloadServer(final String version, final boolean fresh, final String instanceName) {
        startDeploy((fresh ? "新建独立实例 " : "升级服务端 ") + version, new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                p.update(2, "解析镜像地址…");
                String url = DeployManager.resolveServerUrl(version);
                p.update(5, "开始下载…");
                File zip = DeployManager.download(activity, url, "bds-" + version + ".zip", p);
                DeployManager.installServer(activity, zip, "实例下载 " + version, version,
                        fresh, instanceName, tail(p, 70));
                zip.delete();
            }
        }, new Runnable() {
            @Override public void run() { refresh(); }
        });
    }

    private void pickLocal() {
        host.pickFile(new PickCallback() {
            @Override
            public void onPicked(final File file) {
                confirmDeploy("导入 " + file.getName(), new Runnable() {
                    @Override public void run() { startLocalDeploy(file, 0); }
                });
            }

            @Override
            public void onCancel() {
                toast("已取消");
            }
        });
    }
    private void startLocalDeploy(final File file, final int loader) {
        if (type == DeployManager.TYPE_SERVER && guessVersion(file).isEmpty()) {
            askImportVersion(file, loader);
            return;
        }
        installLocal(file, loader, type == DeployManager.TYPE_SERVER ? guessVersion(file) : "");
    }

    private void askImportVersion(final File file, final int loader) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "导入服务端版本");
        panel.addView(MinecraftUi.text(activity,
                "压缩包文件名里没有版本号，无法自动识别。\n请输入该服务端的完整版本号，例如 1.26.63.1。",
                9, MinecraftUi.AMBER, false));
        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setSingleLine(true);
        input.setHint("1.26.63.1");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ilp.topMargin = dp(8);
        panel.addView(input, ilp);
        Button ok = new MinecraftUi.McButton(activity, "确定并导入");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        olp.topMargin = dp(8);
        panel.addView(ok, olp);
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                String ver = input.getText().toString().trim();
                if (!ver.matches("\\d+\\.\\d+\\.\\d+(\\.\\d+)?")) {
                    toast("请输入正确版本号，例如 1.26.63.1");
                    return;
                }
                d.dismiss();
                installLocal(file, loader, ver);
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
    }

    private void installLocal(final File file, final int loader, final String version) {
        startDeploy("导入 " + file.getName(), new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                if (type != DeployManager.TYPE_SERVER) {
                    throw new Exception("运行环境仅支持 Box64 部署");
                }
                DeployManager.installServer(activity, file, "设备导入", version, p);
            }
        });
    }

    private void confirmDeploy(String message, final Runnable action) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "确认操作");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView msg = MinecraftUi.text(activity, message, 10.5f, MinecraftUi.PANEL_TEXT, false);
        msg.setPadding(0, dp(4), 0, dp(8));
        panel.addView(msg);

        Button ok = new MinecraftUi.McButton(activity, "确定");
        panel.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); action.run(); }
        });

        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showInstanceActions(final String id, final boolean active) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window iw = d.getWindow();
        if (iw != null) iw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "实例操作 · " + id);
        Button rename = new MinecraftUi.McButton(activity, "重命名实例");
        if (active && ServerManager.get().isRunning()) {
            rename.setEnabled(false);
            rename.setAlpha(0.55f);
            rename.setText("重命名实例（运行中不可用）");
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        panel.addView(rename, lp);
        rename.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (active && ServerManager.get().isRunning()) {
                    toast("请先停止服务端，再重命名实例");
                    return;
                }
                d.dismiss();
                promptRename(id);
            }
        });
        Button rollback = new MinecraftUi.McButton(activity, "回滚此实例");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rlp.topMargin = dp(5);
        panel.addView(rollback, rlp);
        rollback.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                loadInstanceBackups(id);
            }
        });
        Button upgrade = new MinecraftUi.McButton(activity, "升级此实例");
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ulp.topMargin = dp(5);
        panel.addView(upgrade, ulp);
        upgrade.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                beginInstanceUpgrade(id);
            }
        });
        Button backupInst = new MinecraftUi.McButton(activity, "备份实例");
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        blp2.topMargin = dp(5);
        panel.addView(backupInst, blp2);
        backupInst.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                backupInstance(id);
            }
        });
        Button backupParts = new MinecraftUi.McButton(activity, "分类备份");
        LinearLayout.LayoutParams bplp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bplp.topMargin = dp(5);
        panel.addView(backupParts, bplp);
        backupParts.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                showBackupParts(id);
            }
        });
        if (!active) {
            Button sw = new MinecraftUi.McButton(activity, "切换到该实例");
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            slp.topMargin = dp(5);
            panel.addView(sw, slp);
            sw.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); confirmSwitch(id); }
            });
            Button del = new MinecraftUi.McButton(activity, "删除该实例");
            if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            dlp.topMargin = dp(5);
            panel.addView(del, dlp);
            del.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); confirmDeleteVersion(id); }
            });
        }
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
    }

    private void showBackupParts(final String id) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再备份");
            return;
        }
        try {
            if (!id.equals(DeployManager.activeVersion())) {
                toast("请先切换到该实例，再分类备份");
                return;
            }
        } catch (Throwable ignored) {
        }
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "分类备份");
        String[] kinds = {"world", "addons", "players"};
        String[] labels = {"备份当前存档", "备份 addons", "备份玩家数据"};
        for (int i = 0; i < kinds.length; i++) {
            final String kind = kinds[i];
            Button b = new MinecraftUi.McButton(activity, labels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            lp.topMargin = i == 0 ? 0 : dp(5);
            panel.addView(b, lp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    d.dismiss();
                    backupPartAsync(kind);
                }
            });
        }
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
    }

    private void backupPartAsync(final String kind) {
        final Dialog loading = simpleDialog("分类备份", "正在打包…");
        loading.show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DeployManager.backupPart(activity, kind, new DeployManager.Progress() {
                        @Override public void update(int percent, String message) {
                        }
                    });
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("分类备份完成：" + kind);
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("备份失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "backup-part").start();
    }

    private void backupInstance(final String id) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再备份");
            return;
        }
        final Dialog loading = simpleDialog("备份实例", "正在打包实例…");
        loading.show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DeployManager.backupInstance(activity, id, new DeployManager.Progress() {
                        @Override public void update(int percent, String message) {
                            handler.post(new Runnable() {
                                @Override public void run() {
                                    // 进度对话框保持简单，完成后统一关闭
                                }
                            });
                        }
                    });
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("实例备份完成");
                            refresh();
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("备份失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "backup-instance").start();
    }

    private void beginInstanceUpgrade(final String id) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端，再升级实例");
            return;
        }
        upgradeSourceId = id;
        chooseServerVersion();
    }

    private void startInstanceUpgrade(final String version, final String sourceId) {
        doInstanceUpgrade(version, sourceId);
    }

    private void doInstanceUpgrade(final String version, final String sourceId) {
        startDeploy("升级实例 " + sourceId + " → " + version, new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                String url = DeployManager.resolveServerUrl(version);
                File zip = DeployManager.download(activity, url, "bds-" + version + ".zip", p);
                DeployManager.installServer(activity, zip, "实例下载 " + version, version,
                        false, null, sourceId, tail(p, 70));
                zip.delete();
            }
        }, new Runnable() {
            @Override public void run() { refresh(); }
        });
    }

    private void loadInstanceBackups(final String id) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端，再回滚实例");
            return;
        }
        final Dialog loading = simpleDialog("实例备份", "正在读取该实例的备份…");
        loading.show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final List<String> list = DeployManager.listBackupsForInstance(id);
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            if (list.isEmpty()) {
                                toast("该实例还没有可回滚的备份");
                            } else {
                                showRollbackList(list);
                            }
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("读取备份失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "instance-backups").start();
    }

    private void promptRename(final String id) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window rw = d.getWindow();
        if (rw != null) rw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "重命名实例");
        panel.addView(MinecraftUi.text(activity,
                "只支持字母和数字，不支持中文、空格、下划线、横线或其他符号。最长 32 个字符。",
                9, MinecraftUi.AMBER, false));
        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setSingleLine(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ilp.topMargin = dp(8);
        panel.addView(input, ilp);
        Button ok = new MinecraftUi.McButton(activity, "保存名称");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(8);
        panel.addView(ok, olp);
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                final String name = input.getText().toString().trim();
                if (!name.matches("[A-Za-z0-9]{1,32}")) {
                    toast("实例名只支持字母和数字，最长 32 个字符");
                    return;
                }
                d.dismiss();
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            InstanceStore.rename(id, name);
                            handler.post(new Runnable() {
                                @Override public void run() {
                                    toast("已重命名为 " + name);
                                    refresh();
                                }
                            });
                        } catch (final Throwable e) {
                            handler.post(new Runnable() {
                                @Override public void run() { toast("重命名失败：" + e.getMessage()); }
                            });
                        }
                    }
                }, "rename-instance").start();
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
    }

    private void confirmSwitch(final String version) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端，再切换实例");
            return;
        }
        confirmDeploy("切换到服务端 " + version + "？", new Runnable() {
            @Override public void run() {
                startDeploy("切换服务端版本", new Task() {
                    @Override public void run(DeployManager.Progress p) throws Exception {
                        p.update(30, "切换 symlink…");
                        DeployManager.switchServer(activity, version);
                        p.update(100, "已切换到 " + version);
                    }
                });
            }
        });
    }

    private void confirmDeleteVersion(final String version) {
        confirmDeploy("删除服务端版本 " + version + "？\n该版本目录（含世界与配置）会一并删除，不可恢复。",
                new Runnable() {
                    @Override public void run() {
                        startDeploy("删除服务端版本", new Task() {
                            @Override public void run(DeployManager.Progress p) throws Exception {
                                p.update(30, "删除版本目录…");
                                DeployManager.deleteServerVersion(activity, version);
                                p.update(100, "已删除 " + version);
                            }
                        });
                    }
                });
    }

    private void askBackupThen(final Runnable next) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "更换前备份");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView msg = MinecraftUi.text(activity,
                "是否先备份当前" + typeName() + "？备份可用于一键回滚。", 10,
                MinecraftUi.PANEL_TEXT, false);
        msg.setPadding(0, dp(4), 0, dp(8));
        panel.addView(msg);

        Button yes = new MinecraftUi.McButton(activity, "备份并继续");
        panel.addView(yes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        yes.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                startDeploy("备份当前" + typeName(), new Task() {
                    @Override public void run(DeployManager.Progress p) throws Exception {
                        DeployManager.backup(activity, type, p);
                    }
                }, next);
            }
        });

        Button no = new MinecraftUi.McButton(activity, "不备份继续");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        nlp.topMargin = dp(5);
        panel.addView(no, nlp);
        no.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); next.run(); }
        });

        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void backupNow() {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端");
            return;
        }
        startDeploy("备份" + typeName(), new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                DeployManager.backup(activity, type, p);
            }
        });
    }

    private void showRollback() {
        final Dialog loading = simpleDialog("读取备份", "正在加载备份列表…");
        loading.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> backups = DeployManager.listBackups(type);
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            if (backups.isEmpty()) {
                                toast("还没有备份");
                            } else {
                                showRollbackList(backups);
                            }
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            loading.dismiss();
                            toast("读取备份失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "backup-list").start();
    }

    private void showRollbackList(List<String> backups) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "选择回滚点");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int max = Math.min(8, backups.size());
        for (int i = 0; i < max; i++) {
            final String name = backups.get(i);
            Button b = new MinecraftUi.McButton(activity, name);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = i == 0 ? 0 : dp(4);
            panel.addView(b, lp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    dialog.dismiss();
                    confirmDeploy("回滚到 " + name + "？当前" + typeName() + "会被覆盖。",
                            new Runnable() {
                                @Override public void run() {
                                    startDeploy("回滚" + typeName(), new Task() {
                                        @Override public void run(DeployManager.Progress p) throws Exception {
                                            DeployManager.restoreBackup(activity, type, name, p);
                                        }
                                    });
                                }
                            });
                }
            });
        }

        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private void confirmDelete() {
        String msg;
        if (type == DeployManager.TYPE_SERVER) {
            msg = "将删除整个服务端目录（含 worlds 世界存档、server.properties、白名单和权限）。此操作不可恢复。";
        } else {
            msg = "";
        }
        confirmDeploy(msg, new Runnable() {
            @Override public void run() { doDelete(); }
        });
    }

    private void doDelete() {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端");
            return;
        }
        startDeploy("删除" + typeName(), new Task() {
            @Override public void run(DeployManager.Progress p) throws Exception {
                p.update(20, "删除本地文件…");
                DeployManager.delete(activity, type);
                p.update(100, "已删除");
            }
        });
    }

    private void startDeploy(String label, Task task) {
        startDeploy(label, task, null);
    }

    private void startDeploy(String label, final Task task, final Runnable after) {
        if (!ShizukuGate.ensure(activity, label)) return;
        if (busy) {
            toast("正在部署，请稍候");
            return;
        }
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端");
            return;
        }
        busy = true;
        DeployState.start(type, label);
        progressBox.setVisibility(VISIBLE);
        progressBar.setProgress(0);
        progressText.setText("0% · " + label);
        progressLog.setText("");
        actionBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        backupBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);
        actionBtn.setAlpha(0.55f);
        deleteBtn.setAlpha(0.55f);
        backupBtn.setAlpha(0.55f);
        rollbackBtn.setAlpha(0.55f);
        DeployKeepAliveService.start(activity, label);
        ServerManager.get().log("[部署] " + label);
        AppLog.add("[部署] " + label);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run(new DeployManager.Progress() {
                        @Override
                        public void update(final int percent, final String message) {
                            handler.post(new Runnable() {
                                @Override public void run() {
                                    progressBar.setProgress(percent);
                                    progressText.setText(percent + "% · " + message);
                                    DeployState.update(type, percent, message);
                                    DeployKeepAliveService.progress(activity, percent, message);
                                    boolean verbose = message.startsWith("下载中") || message.startsWith("上传部署包");
                                    if (!verbose) {
                                        String old = progressLog.getText().toString();
                                        String line = "> " + message;
                                        if (!old.endsWith(line)) {
                                            progressLog.setText(old.isEmpty() ? line : old + "\n" + line);
                                        }
                                        ServerManager.get().log("[部署] " + percent + "% " + message);
                                        AppLog.add("  " + percent + "% " + message);
                                    }
                                }
                            });
                        }
                    });
                    handler.post(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            actionBtn.setEnabled(true);
                            deleteBtn.setEnabled(true);
                            backupBtn.setEnabled(true);
                            rollbackBtn.setEnabled(true);
                            actionBtn.setAlpha(1f);
                            deleteBtn.setAlpha(1f);
                            backupBtn.setAlpha(1f);
                            rollbackBtn.setAlpha(1f);
                            toast("部署完成");
                            AppLog.add("[完成] " + label);
                            DeployState.finish(type, "部署完成", true);
                            DeployKeepAliveService.stop(activity);
                            if (after != null) after.run();
                            else refresh();
                        }
                    });
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            DeployManager.setRepairingRuntime(false);
                            progressText.setText("失败：" + e.getMessage());
                            progressLog.setText(progressLog.getText() + "\n[错误] " + e.getMessage());
                            actionBtn.setEnabled(true);
                            deleteBtn.setEnabled(true);
                            backupBtn.setEnabled(true);
                            rollbackBtn.setEnabled(true);
                            actionBtn.setAlpha(1f);
                            deleteBtn.setAlpha(1f);
                            backupBtn.setAlpha(1f);
                            rollbackBtn.setAlpha(1f);
                            toast("部署失败：" + e.getMessage());
                            AppLog.add("[失败] " + label + "：" + e.getMessage());
                            DeployState.finish(type, "部署失败：" + e.getMessage(), false);
                            DeployKeepAliveService.stop(activity);
                            ServerManager.get().log("[部署][错误] " + e.getMessage());
                        }
                    });
                }
            }
        }, "deploy-task").start();
    }

    private Dialog simpleDialog(String title, String message) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(MinecraftUi.text(activity, message, 10, MinecraftUi.PANEL_MUTED, false));
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.85f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private String guessVersion(File file) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(file.getName());
        return m.find() ? m.group(1) : "";
    }

    private DeployManager.Progress tail(final DeployManager.Progress p, final int base) {
        return new DeployManager.Progress() {
            @Override
            public void update(int percent, String message) {
                int mapped = base + percent * (100 - base) / 100;
                p.update(mapped, message);
            }
        };
    }

    private interface Task {
        void run(DeployManager.Progress p) throws Exception;
    }

    private int dp(float v) {
        return MinecraftUi.dp(activity, v);
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }
}
