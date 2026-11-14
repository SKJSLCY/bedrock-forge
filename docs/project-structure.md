# 基岩工坊 · 项目结构

> 版本：0.2.0（versionCode 2）　包名：`com.skjslcy.bedrockforge`　许可证：GPL-3.0-or-later
> 本文档依据源码实测整理（55 个 Java 文件 / 20,630 行），用于快速定位代码、理解分层与数据流。

---

## 1. 项目概览

### 1.1 一句话

在 Android 手机上，用 **proot + Box64 + Ubuntu arm64 rootfs** 跑官方 **Minecraft Bedrock Dedicated Server（x86_64）**，
并提供一个完全用 Java 代码绘制的 Minecraft 风格管理界面；所有对 `/data/local/tmp` 的读写都通过 **Shizuku** 以 shell 身份完成。

### 1.2 关键事实

| 项目 | 取值 |
|---|---|
| 语言 / 构建 | 纯 Java 8（`-source 8 -target 8`），**不用 Gradle**，`app/build.ps1` 直调 aapt2 / javac / d8 / zipalign / apksigner |
| Android | minSdk **26**（8.0）、targetSdk **28**、arm64-v8a，`android:debuggable="true"` |
| 第三方库 | 仅 `app/libs/shizuku-api13.jar`、`shizuku-provider13.jar`；另有 `androidx.annotation` 的 3 个本地桩类 |
| UI | 无 XML 布局，全部代码构建（`MinecraftUi` + 各 `*View`），4 个一级页面（底部物品栏切换）+ 服务器管理分区 |
| 权限模型 | 无 root；`moe.shizuku.manager.permission.API_V23` + Shizuku Binder 拉起 shell 进程 |
| 运行目标 | 官方 BDS `1.26.62.x / 1.26.63.1`（Linux x86_64），默认端口 **19134** |
| 界面语言 | 简体中文（默认）/ 繁體中文 / English，词条 **1405** 条 × 2 |
| 体积构成 | `toolchain-rootfs.tar.gz` 269 MB + `box64-main.tar.gz` 19.7 MB + `libqemu.so` 4.2 MB → APK ≈ 277 MB |

### 1.3 能力清单

运行环境部署（proot 复制 + rootfs 解压 + 手机内编译 Box64）、服务端部署/多实例/升级、启停重启、控制台命令与日志、
服务器配置汉化编辑、addons 管理、世界新建/切换/导入/导出、离线玩家数据读写（LevelDB + NBT）、玩家统计与名字映射、
备份与回滚、AI 助手（多服务商、工具调用、本地加密 Key）、权限与屏幕常亮管理、诊断与 FAQ、三语言界面。

---

## 2. 顶层目录

```text
MC BDS APP/
├─ app/                          应用模块（源码 + 资源 + 构建脚本）
│  ├─ AndroidManifest.xml        minSdk 26 / targetSdk 28；2 个 Activity、2 个前台服务、Shizuku Provider
│  ├─ build.ps1                  唯一构建入口（产物 app/build/app-debug.apk）
│  ├─ debug.keystore             测试签名（已被 .gitignore 忽略，构建时若缺失会自动生成）
│  ├─ aidl/                      Shizuku 服务端 AIDL：IShizukuService / IRemoteProcess / …
│  ├─ gen-aidl/                  上面 AIDL 生成的 Java（手工放入仓库，与源码一起编译）
│  ├─ gen/                       aapt2 link 生成的 R.java（构建期产生）
│  ├─ libs/                      Shizuku API / Provider 的 jar
│  ├─ jniLibs/arm64-v8a/libqemu.so   历史遗留：QEMU 方案时期的原生库，当前代码不加载
│  ├─ res/                       极少量资源：drawable（启动背景/图标）、values 系列（app_name、主题、三语言启动器名）
│  ├─ assets/                    运行所需的大件与文本资源（见 §6.5）
│  └─ src/
│     ├─ androidx/annotation/    NonNull / Nullable / RestrictTo 三个桩注解（避免引入 AndroidX）
│     └─ com/skjslcy/bedrockforge/   55 个 Java 文件（全部业务代码）
├─ docs/                         开发文档（本文件 + 方案/UI/i18n/历史 PoC）
├─ design/                       UI 设计稿（3 套方案的 HTML + PNG）
├─ .tooling/                     本地工具链与脚本（git 忽略）：android-sdk、JDK 依赖、i18n 工具、源码备份
├─ LICENSE / NOTICE / THIRD_PARTY_NOTICES / SOURCES.md
├─ README.md                     项目门面（快速开始、构建、许可）
└─ 其他临时文件                   *.docx、hs_err_pid*.log 等（git 忽略）
```

---

## 3. 构建管线

### 3.1 环境（`app/build.ps1` 顶部常量，换机器需按实际路径修改）

```powershell
$sdk     = 'Q:\MC BDS APP\.tooling\android-sdk'
$bt      = "$sdk\build-tools\35.0.0"
$platform= "$sdk\platforms\android-35\android.jar"
$jdk     = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
```

### 3.2 步骤

| 步骤 | 命令要点 | 说明 |
|---|---|---|
| 1. 资源编译 | `aapt2 compile --dir res -o build/res.zip` | `values` / `values-en` / `values-zh-rTW` 一起编 |
| 2. 资源链接 | `aapt2 link -o build/base.apk -I android.jar --manifest … -R res.zip --java gen` | 生成 `R.java` 与含 `resources.arsc` 的壳 APK |
| 3. 编译 Java | `javac -source 8 -target 8 -encoding UTF-8 -classpath android.jar;libs/*.jar -d build/classes src gen gen-aidl` | 三个源码根：业务、R、AIDL |
| 4. dex | `d8 --lib android.jar --min-api 26 --output build/dex allclasses.jar` | 业务类 + 解包后的 Shizuku 类合并成单个 `classes.dex` |
| 5. 打包 | 用 `System.IO.Compression` 往 base.apk 里塞 `classes.dex`、`lib/arm64-v8a/libqemu.so`、`assets/**` | assets 采用 StreamingZip 逐文件写入 |
| 6. 对齐 / 签名 | `zipalign -f 4` → `apksigner sign --v1 --v2 --v3` | keystore 缺失时自动 `keytool -genkeypair` |
| 7. 校验 | `apksigner verify -v` | 失败即中断构建 |

### 3.3 产物

```text
app/build/app-debug.apk     ≈ 277 MB（含 269 MB rootfs）
```

### 3.4 注意

- `app/assets/toolchain-rootfs.tar.gz` 超过 Git 单文件限制，需用 LFS 或 Release 附件分发；缺失时构建仍可完成，但 APK 内没有工具链，部署 Box64 会失败。
- `.tooling/`、`app/build/`、`app/gen/` 均已加入 `.gitignore`。

---

## 4. 运行时架构

### 4.1 进程模型

```text
┌─ App 进程（uid = 应用自身）────────────────────────────────────────────┐
│  SplashActivity ──▶ MainActivity（UI 线程 + 1s 轮询 Handler）           │
│  ServerService（前台服务 dataSync）：接收 START/STOP/COMMAND            │
│  DeployKeepAliveService（前台服务 dataSync）：部署期间保活 + 进度通知     │
│  ServerManager（单例）：持有 BDS 子进程、stdin、2000 行环形日志          │
│  PlayerTracker / PlayerMapping / DeviceMonitor / AppLog（静态状态）      │
└───────────────▲───────────────────────────────┬───────────────────────┘
                │ Shizuku Binder（IShizukuService.newProcess）
                │                               │
┌───────────────┴─ shell 进程（uid 2000, cwd=/data/local/tmp）────────────┐
│  sh -c …  →  proot --link2symlink -r <rootfs> -0 -b /dev -b /proc -b /sys│
│              -b <实例目录>:/root/bds -w /root/bds                        │
│              /usr/local/bin/box64 /root/bds/bedrock_server               │
│  （box64 把 x86_64 的 BDS 翻译到 arm64，proot 提供 Ubuntu 用户空间）      │
└─────────────────────────────────────────────────────────────────────────┘
```

- 服务器进程的 **stdout/stdin** 由 `ServerManager` 通过 `ShizukuProcess` 的管道读写：stdout → 日志环形缓冲 → UI；stdin ← 控制台命令。
- 部署与编译（cmake/make）、终端页同样走 Shizuku，但用独立的 `sh -c` 会话，不占用服务端进程。

### 4.2 进程内分层

```text
表现层   SplashActivity / MainActivity / *SectionView / *Dialog / TerminalView
   │           （统一用 MinecraftUi 构件，文本统一走 I18n.t）
框架层   MinecraftUi（控件+绘制）/ ThemeManager（4 套主题）/ LiquidWallpaper
   │
领域层   DeployManager / Box64Installer / ServerManager / InstanceStore / DeployState
   │     PlayerTracker / ServerLogTail / PlayerMapping / DeviceMonitor / ScreenOnManager
   │
数据层   LevelDbReader / LevelDbWriter / Snappy / Lz4 / Crc32c / BedrockNbt / NbtCodec
   │     OfflinePlayerData / PlayerDataEditor / PlayerDataView / Translations / ItemNames
   │
系统层   ShizukuShell / ShizukuProcess / ShizukuGate / EnvManager
   │
AI 子系统 AiConfig（密钥/服务商）/ AiHttp（三种协议）/ AiTools（工具定义）/ AiAssistantView
```

### 4.3 线程模型

| 场景 | 线程 | 说明 |
|---|---|---|
| UI 更新 | 主线程 | `handler`（主 Looper）驱动 1 秒轮询：设备采样、状态刷新、日志刷新、`list` 每 15 秒 |
| 文件 / shell | `io`（单线程池）或 `new Thread` | 部署、备份、导入导出、玩家数据读写都在后台，结果 `handler.post` 回主线程 |
| 服务端日志 | `bds-stdout`（守护线程） | `ServerManager.readLoop()` 逐行 `addLog` → `PlayerTracker.handleLine` |
| 进程回收 | `bds-wait` / `bds-killer` | 前者 `waitFor` 后置 `running=false`；后者 stop 后 12 秒强杀 |
| 部署保活 | `DeployKeepAliveService` | 前台服务 + 通知进度，防止锁屏/切后台被系统回收 |

---

## 5. 源码结构（`app/src/com/skjslcy/bedrockforge/`）

### 5.1 应用外壳与导航

| 文件 | 行数 | 职责 |
|---|---|---|
| `SplashActivity` | 103 | 启动页：初始化 `I18n`/主题，显示像素 Logo 或“基岩工坊”字样，200 ms 后跳 `MainActivity` |
| `MainActivity` | 3434 | 唯一主界面：底部物品栏导航（首页/控制台/AI/设置）、4 页容器、服务器管理 7 个分区、设置子页、1 秒轮询、启停接入 `ServerService`、文件选择与壁纸回调、语言/主题切换后 `recreate()` |
| `PixelLogoView` | 74 | 4 个汉字的像素标题：关闭抗锯齿 + 硬阴影，模拟像素字 |
| `AppLog` / `AppLogView` | 25 / 46 | 应用侧操作日志（部署/下载/修复）内存环形缓冲 + 只读终端视图 |

`MainActivity` 的三层页面容器：

```text
content(FrameLayout)
├─ pages[0..3]        首页 / 控制台 / AI / 设置（底部导航切换）
├─ managePage         服务器管理页（7 个入口按钮，manageButtons 预留 9 个容量 + manageHost 分区容器）
│    └─ manageHost ← DeploySectionView / ServerSettingsView / AddonsSectionView /
│                     WorldSectionView / PlayerSectionView / BackupSectionView
└─ settingsPage       设置子页容器（settingsHost + settingsTitle）
     └─ buildThemePage / buildLanguagePage / PermissionView / AiSettingsView / buildAboutPage
```

### 5.2 UI 框架层

| 文件 | 行数 | 职责 |
|---|---|---|
| `MinecraftUi` | 1142 | 全部控件的唯一来源：`text()`（自动 `I18n.t`）、`McButton`、`McSwitch`、`SegmentBar`、`PixelIconView`；各主题的 Drawable 绘制（`BevelDrawable`、`DirtDrawable`、圆角、液态玻璃）；`panel()`/`slot()`/`infoRow()`/`valueText()` 布局助手；`applyTheme()` 切换配色常量；AI 对话气泡的按主题取色（`chatBubbleDrawable` / `chatBubbleText` / `chatNameColor` / `chatMetaColor` / `chatInputText` / `chatInputHint`） |
| `ThemeManager` | 129 | 主题持久化与下发：`classic` 经典 / `minimal` 简约（浅/深）/ `ore` 苔石 / `liquid` 液态玻璃；状态栏与导航栏配色、文本色映射 |
| `LiquidWallpaper` | 136 | 液态玻璃背景：保存用户选择的壁纸 URI（`ui/liquid_wallpaper_uri`）、解码、模糊、慢速缩放渲染 |

主题状态会写进 `MinecraftUi` 的静态字段（`MINIMAL/ORE/LIQUID/PANEL/…`），因此**所有视图必须在 `ThemeManager.apply()` 之后创建**。

### 5.3 页面与分区视图

| 文件 | 行数 | 职责 |
|---|---|---|
| `DeploySectionView` | 1759 | 部署页（服务端 / 运行环境两用）：状态卡片、主操作按钮、进度条与进度日志、实例列表（升级/回滚/备份/改名/删除）、Box64 编译线程选项、文件选择与导入、Shizuku 引导 |
| `ServerSettingsView` | 587 | `server.properties` 汉化编辑器：按类型（布尔/整数/小数/枚举/文本）渲染控件，仅在服务端停止时可写，写回前确认 |
| `AddonsSectionView` | 495 | addons 管理：扫描 `behavior_packs`、`development_behavior_packs`、`resource_packs`、`development_resource_packs`，导入 `.mcpack/.zip`、启用/禁用（改 `manifest.json`）、删除 |
| `WorldSectionView` | 570 | 世界管理：列出 `worlds/*`、新建、切换（改 `level-name`）、重命名、删除、导入 `.zip/.mcworld`、导出到 `/sdcard/Download/MCBDS` |
| `PlayerSectionView` | 698 | 玩家管理：在线玩家、进服统计、XUID/名字映射、白名单、`op/deop`、游戏模式、经验等级、`inputpermission`、跳转玩家数据页 |
| `BackupSectionView` | 678 | 备份页：一键备份（服务端/世界/玩家/addons/插件）、列表与元数据、回滚（跨版本拦截）、导入、导出、删除 |
| `PlayerDataView` / `PlayerDataEditor` | 523 / 859 | 离线玩家数据：卡片式中文展示；编辑基础数值、背包物品、原始键值；写入前自动备份 + 写入后校验 |
| `PermissionView` | 421 | 设置 → 权限管理：通知、Shizuku、电池优化、存储、屏幕常亮的检测与跳转 |
| `TerminalView` / `TerminalTabsView` | 263 / 101 | 服务器停止时可用的 Ubuntu 终端（proot + rootfs）；多终端标签 + App 日志终端；按输出内容着色 |
| `AiAssistantView` | 833 | AI 助手页：多会话（重命名/删除/持久化）、图片与文本附件、模型与思考强度选择、工具调用前确认 |
| `AiSettingsView` | 423 | 设置 → AI 智能体：添加/移除服务商、Base URL、API Key（本地加密）、模型检测、系统提示词方案 |
| `AboutDialog` | 477 | 关于页：版本、简介、入口、环境信息、诊断、常见问题、致谢、声明；读取 `assets/legal/*` |
| `AgreementDialog` | 175 | 用户协议：首次启动必须滑到底才能同意；`VERSION` 变更后重新弹出 |

### 5.4 领域服务层

| 文件 | 行数 | 职责 |
|---|---|---|
| `DeployManager` | 878 | 部署核心：路径常量、状态检测、下载（官方 BDS / MCAPKS 版本表）、安装与解压、多实例、按部件备份/回滚、删除、版本探测 |
| `Box64Installer` | 186 | 在手机内完成 Box64 编译：复制 proot → 解压内置 rootfs 工具链 → 解压 Box64 源码 → `cmake -DARM64=ON -DBAD_SIGNAL=ON -DNOGIT=ON` + `make -jN` → `box64 --version` 校验 → 写 `.mcbds_ready` |
| `InstanceStore` | 173 | 多实例清单：`servers/<id>/instance.json` 读写、`server` 软链指向当前实例、名称校验与重命名 |
| `DeployState` | 71 | 部署进度共享快照（类型、百分比、消息、完成版本号），页面重建后能恢复显示 |
| `ServerManager` | 264 | BDS 进程单例：组启动命令（proot+box64）、`ulimit -n` 提升、stdout/stdin 管道、2000 行日志、`stop` 后 12 秒强杀、端口/服务器名状态 |
| `ServerService` | 125 | 前台服务：`ACTION_START/STOP/COMMAND` → `ServerManager`；通知栏带“停止”按钮 |
| `DeployKeepAliveService` | 88 | 部署期间的保活通知与进度更新 |
| `ScreenOnManager` | 55 | 屏幕常亮策略：关闭 / 仅开服时 / 始终保持（`screen_on/mode`） |
| `DeviceMonitor` | 152 | 设备级 CPU、内存、网络采样（读 `/proc`），供首页负荷条使用 |
| `PlayerTracker` | 283 | 从控制台日志解析进服/离服，累计在线时长，落盘 `server/players.json`，维护待绑定队列 |
| `ServerLogTail` | 173 | 增量读 `Dedicated_Server.txt`（UTC 时间转本地），补齐 App 未运行期间缺失的进出记录 |
| `ListOutput` | 39 | 解析 BDS `list` 命令输出（表头在上一行，玩家名逐行） |
| `PlayerMapping` / `MappingCore` | 145 / 18 | 名字 ↔ MSA ID 映射：进服前后对比 LevelDB 记录找出唯一新增项（`MappingCore` 为纯逻辑），映射存 `mcbds/msa_map` |

### 5.5 系统访问层（Shizuku）

| 文件 | 行数 | 职责 |
|---|---|---|
| `ShizukuGate` | 170 | Shizuku 运行/授权状态检查、启动引导对话框（打开 Shizuku、重试） |
| `ShizukuProcess` | 99 | 包装 `IShizukuService.newProcess`，返回标准 `Process`（含 stdout/stdin/stderr），支持指定工作目录与环境变量 |
| `ShizukuShell` | 139 | shell 门面：`sh()`/`read()`/`write()`、`copyIn()`（把 App 侧文件经 stdin 写进 `/data/local/tmp`，绕开 SELinux 与 `/sdcard` 限制）；常量 `SERVER_DIR`、`PROPERTIES`、`WORK_DIR=/data/local/tmp` |
| `EnvManager` | 190 | 环境探测：`isInstalled()` 查 `/data/local/tmp/box64` 的二进制与就绪标记、局域网 IP、大小格式化；`readProperties()`/`serverDir()` 仍指向旧的 App 私有路径（见 §11） |

### 5.6 数据层：LevelDB / NBT / 玩家

| 文件 | 行数 | 职责 |
|---|---|---|
| `LevelDbReader` | 574 | 只读 LevelDB：扫 `.ldb`/`.sst`（块、索引、布隆、Snappy/Lz4 解压）与 `.log` WAL，按键取最新 `sequence` 的值；提供“列出所有键值”的 `EntrySink` |
| `LevelDbWriter` | 139 | 停服状态下向最新 `.log` 追加记录（WriteBatch 格式 + CRC32C），实现写回 |
| `Snappy` / `Lz4` / `Crc32c` | 67 / 55 / 29 | LevelDB 所需的解压与校验原语（只解压、不压缩） |
| `BedrockNbt` | 168 | 基岩版小端 NBT 只读解析（`Tag` 树），用于展示 |
| `NbtCodec` | 233 | 类型保留的 NBT 读写（小端），用于把修改安全写回 |
| `OfflinePlayerData` | 779 | 离线读取：定位 `<实例>/worlds/<当前世界>/db`，按 `player_` / `player_server_` 前缀读出玩家记录，分组为“状态/出生与死亡/界面状态/背包/主手/副手/效果/能力/属性…”，字段名走 `Translations` |
| `Translations` | 193 | 内置玩家数据词典：字段名、物品名、效果、附魔、属性、维度、游戏模式、权限等级；按当前语言覆盖加载 `assets/i18n/<lang>/` |
| `ItemNames` | 30 | 物品 ID → 名称（走 `Translations`） |

### 5.7 AI 子系统

| 文件 | 行数 | 职责 |
|---|---|---|
| `AiConfig` | 380 | 服务商表（OpenAI / DeepSeek / 通义千问 / Kimi / 智谱 / 硅基流动 / OpenRouter / Gemini / Anthropic / 自定义）、模型与 Base URL、API Key 用 Android Keystore（`CryptoBox`）加密后存 `ai_config`、会话与系统提示词方案 |
| `AiHttp` | 468 | 非流式请求的三种方言：OpenAI 兼容（`/chat/completions`）、Gemini（`:generateContent`）、Anthropic（`/messages`，`x-api-key` + `anthropic-version`）；模型列表、工具定义与解析 |
| `AiTools` | 141 | 工具集：`server_status`、`read_logs`、`list_players`（只读）＋ `start_server`、`stop_server`、`send_command`（需用户确认） |
| `AiAssistantView` | 833 | 对话页：气泡渲染、附件、工具调用确认、会话管理 |
| `AiSettingsView` | 423 | 服务商与 Key 管理、模型检测、提示词编辑 |

### 5.8 国际化

| 文件 | 行数 | 职责 |
|---|---|---|
| `I18n` | 175 | 语言状态与查表：`t()`/`f()`、`lang()`/`setLang()`、`LANGUAGES`、资源后缀 `suffix()` 与 `asset()` 改名、`readAsset()` |
| `assets/i18n/ui/lang_<code>.json` | — | 词条表，**键 = 简体中文原文**（`zh_CN` 直接返回原文，不建表） |
| `assets/i18n/<lang>/*` | — | 玩家数据词典的语言覆盖层 |
| `res/values-en`、`values-zh-rTW` | — | 启动器名称（跟随系统语言） |

```text
I18n.t("取消")            → zh_CN: "取消" | zh_TW: "取消" | en: "Cancel"
I18n.f("切换为 %s？", n)   → 占位符在译文中按目标语言重排
```

---

## 6. 数据与存储布局

### 6.1 运行目录（`/data/local/tmp/mcbds`，shell 身份读写）

```text
/data/local/tmp/mcbds/
├─ proot/
│  ├─ proot                 可执行（来自 assets/proot/proot）
│  ├─ libexec/loader, loader32
│  └─ lib/libtalloc.so.2, libandroid-shmem.so
├─ server -> servers/<当前实例id>        （软链，BDS 永远从这里启动）
├─ servers/
│  └─ <实例id>/                          例：1.26.63.1、1.26.63.1-1763000000
│     ├─ bedrock_server                  官方 Linux x86_64 可执行
│     ├─ server.properties               服务器配置（被 Settings 页编辑）
│     ├─ worlds/<世界名>/db/…            LevelDB（玩家与区块数据）
│     ├─ behavior_packs/ development_behavior_packs/
│     ├─ resource_packs/ development_resource_packs/
│     ├─ instance.json                   {id, name, bdsVersion, createdAt, loader}
│     ├─ .mcbds_version                  版本探测标记
│     ├─ .mcbds_source                   来源标记（官方下载 / 本地导入 / MCAPKS）
│     ├─ players.json                    PlayerTracker 的进服统计
│     └─ Dedicated_Server.txt            BDS 持久日志（ServerLogTail 增量读取）
├─ backups/
│  ├─ server-<tag>-<ts>.tar.gz           服务端整体
│  ├─ world-<世界>-<ts>.tar.gz           世界
│  ├─ players-<世界>-<ts>.tar.gz         玩家数据
│  ├─ addons-<世界>-<ts>.tar.gz          addons
│  ├─ plugins-…                          旧插件备份
│  └─ playeredit-<ts>.tar.gz             编辑玩家数据前的自动备份
└─ tmp/                                  部署暂存（解压、上传中转）
```

### 6.2 运行环境（`/data/local/tmp/box64`）

```text
/data/local/tmp/box64/
├─ rootfs/                      Ubuntu 24.04 arm64（内置工具链 + 编译产物）
│  └─ usr/local/bin/box64
├─ .mcbds_ready                 Box64 就绪标记（内容为 box64 --version 首行）
└─ tmp/                         proot 临时目录
```

### 6.3 其他外部路径

| 路径 | 用途 |
|---|---|
| `/sdcard/Download/MCBDS/` | 导出的 `.mcworld`、备份压缩包 |
| `filesDir/mcbds/server12662/` | 旧版 App 私有服务端目录（`EnvManager` 残留，见 §11） |
| `cacheDir/` | assets 中转文件（`asset-*.tar.gz`）、导出时生成的 zip |

### 6.4 SharedPreferences

| 文件 | 关键键 | 用途 |
|---|---|---|
| `ui` | `theme`、`theme_mode`、`language`、`liquid_wallpaper_uri` | 主题风格、明暗、界面语言、液态壁纸 |
| `legal` | `agreement_version` | 用户协议版本（与 `AgreementDialog.VERSION` 比对） |
| `app` | `onboarded` | 首次使用引导是否完成 |
| `deploy_meta` | `server_source`、`server_source_<实例id>`、`box64_source`、`server_version`、`runtime_check_at` | 部署来源与版本元数据、Box64 版本探测节流 |
| `ai_config` | `provider`、`added`、`model_<pid>`、`base_<pid>`、`models_<pid>`、`key_<pid>`（加密）、`ai_name`、`user_name`、`context_limit`、`reasoning`、`system_preset`、`system`、`sessions` | AI 全部配置 |
| `mcbds` | `msa_map` | 名字 ↔ MSA ID 映射（JSON） |
| `screen_on` | `mode` | 屏幕常亮策略 |

### 6.5 assets 与 res

```text
app/assets/
├─ box64-main.tar.gz          19.7 MB  Box64 源码（编译用）
├─ toolchain-rootfs.tar.gz    269 MB   Ubuntu 24.04 arm64 + gcc/cmake/make
├─ proot/                     proot 可执行与 loader、依赖库
├─ legal/                     user_agreement*.txt（3 语言）、changelog*.txt（3 语言）、gpl-3.0.txt
└─ i18n/
   ├─ ui/lang_zh_TW.json、lang_en.json          界面词条（各 1405 条）
   ├─ fields.json、effects.json、enchants.json、attributes.json、item_names.tsv   简体中文原表
   └─ zh_TW/、en/                                上述词典的语言覆盖层（按目录覆盖加载）

app/res/
├─ drawable/splash_window.xml、splash_dirt_tile.xml、ic_launcher.xml
├─ drawable-nodpi/splash_dirt.png
└─ values/strings.xml（app_name=基岩工坊）、values-en（Bedrock Forge）、values-zh-rTW、values/styles.xml（SplashTheme/AppTheme）
```

---

## 7. 关键流程

### 7.1 启动

```text
SplashActivity.onCreate
  └ I18n.init(this) → 载入当前语言词条 → Translations.invalidate()
  └ ThemeManager.apply(this) → MinecraftUi.applyTheme(...)
  └ 200ms 后 startActivity(MainActivity)

MainActivity.onCreate
  └ I18n.init / ThemeManager.apply → buildUi()
  └ showSplash()（应用内启动动画：Logo + BEDROCK FORGE + 进度条，首次状态就绪后收起）
  └ loadConfig()（读 server.properties 的端口与世界）
  └ AgreementDialog（未同意协议时，必须滑到底才能同意）
  └ showPage(0) → handler.post(poll)（1 秒循环）
```

### 7.2 部署运行环境（Box64）

```text
DeploySectionView「部署运行环境」
  └ ShizukuGate.ensure()（弹窗引导授权）
  └ DeployKeepAliveService.start()（前台保活）
  └ Box64Installer.install(ctx, progress, threads)
       1) 复制 assets/proot/* → /data/local/tmp/mcbds/proot（chmod 755/644）
       2) 解压 toolchain-rootfs.tar.gz → /data/local/tmp/box64/rootfs
       3) 校验 /usr/bin/gcc、/usr/bin/cmake 存在
       4) 解压 box64-main.tar.gz → rootfs/root/box64-main
       5) proot + cmake -DARM64=ON -DBAD_SIGNAL=ON -DNOGIT=ON + make -jN + make install
       6) box64 --version 校验 → 写 .mcbds_ready
  └ DeployState 记录进度；页面可重建后恢复
```

### 7.3 部署服务端

```text
DeploySectionView「部署服务端」
  ├ 官方下载：DeployManager.download() → cacheDir
  ├ 本地导入：MainActivity 文件选择（Shizuku 读不到 /sdcard，先拷到 cacheDir）
  └ DeployManager.installServer(ctx, archive, source, version, freshInstance, name, progress)
       1) 检查运行环境存在
       2) copyArchive → /data/local/tmp/mcbds/tmp/upload-*.zip（ShizukuShell.copyIn 走 stdin）
       3) extract → tmp/server-stage，find bedrock_server 定位真实根目录
       4) 迁移到 servers/<实例id>（重名自动加时间戳，绝不覆盖）
       5) 重建 server → servers/<id> 软链
       6) 写 .mcbds_version / .mcbds_source → InstanceStore.read(id) → 可选重命名
       7) prefs(deploy_meta) 记录 server_version / server_source
```

### 7.4 启停服务端

```text
MainActivity.startServer
  └ Shizuku 权限检查 → startForegroundService(ServerService.ACTION_START)
        └ ServerManager.start(ctx)
             └ 检查 box64 与 rootfs → 取 ulimit 硬限制
             └ sh -c "ulimit -n <hard>; exec proot … /usr/local/bin/box64 /root/bds/bedrock_server"
             └ ShizukuProcess.start(cmd, env, "/data/local/tmp")
             └ 起 bds-stdout（读日志）与 bds-wait（等退出）守护线程

MainActivity.stopServer → ServerService.ACTION_STOP → ServerManager.stop()
  └ 向 stdin 写 "stop"
  └ 12 秒后仍未退出 → process.destroy()
重启：停止后 75 秒再 start（让端口彻底释放）
```

### 7.5 控制台与日志

```text
BDS stdout → ServerManager.readLoop → addLog()
   ├ 追加到 2000 行环形缓冲（UI 每 1 秒拉取 getLogs()）
   └ PlayerTracker.handleLine()（识别 "Player connected/…", "players online:"）

MainActivity 控制台页 → ServerManager.send(cmd)（写 stdin，回显 "> cmd"）
每 15 秒自动发一次 list 以刷新在线人数
```

### 7.6 玩家统计与名字映射

```text
玩家进服日志 → PlayerTracker 记录 firstSeen/lastSeen/totalMs → servers/<id>/players.json
ServerLogTail：App 未运行期间，增量读取 Dedicated_Server.txt 补齐记录

名字映射（BDS 存档不含名字）：
  进服时 PlayerMapping.refreshBaseline()  记下数据库中现有 player_ 记录
  离服后 PlayerMapping.learn(name)         取差集，唯一新增项即该玩家的 MSA ID
  → mcbds/msa_map（JSON）
```

### 7.7 离线玩家数据读写

```text
读取：OfflinePlayerData.load(ctx)
  └ 定位 SERVER_DIR/worlds/<level-name>/db
  └ LevelDbReader 扫 .ldb/.sst + .log，取每个 key 的最新值（Snappy/Lz4 解压，CRC32C 校验）
  └ 键前缀 player_ / player_server_ → NbtCodec 解析 → 分组成卡片

写入：PlayerDataEditor
  └ 要求服务端已停止
  └ 先在 BACKUPS_DIR 打一份 playeredit-<ts>.tar.gz
  └ LevelDbWriter 向最新 .log 追加 WriteBatch（新 sequence）
  └ 回读校验 → 失败自动还原备份
```

### 7.8 备份与回滚

```text
备份（服务端必须已停止，入口处统一拦截并提示"请先停止服务端再备份"）：
  DeployManager.backupPart(kind) / backupInstance(id) / backup(type)
  → BACKUPS_DIR/<kind>-<tag>-<ts>.tar.gz + 同名 .json 元数据（kind/instanceId/bdsVersion/time）

回滚（同样要求停服）：
  BackupSectionView → 版本比较（备份版本 > 当前实例 → 按钮禁用并拦截）
  → DeployManager.restorePart / restoreBackup（tar 覆盖 + 软链修正）
```

### 7.9 世界管理

```text
列出 worlds/*；新建（mkdir worlds/<名> 并切换 level-name，下次启动时由 BDS 生成世界）；
切换（改写 server.properties 的 level-name=）；重命名（改目录 + 同步 level-name）；
删除；导入（.zip/.mcworld 解到 worlds/）；导出（打 zip → /sdcard/Download/MCBDS/*.mcworld）
所有写操作前都走 ensureStopped()（"请先停止服务端"）
```

### 7.10 AI 对话与工具调用

```text
AiAssistantView 发送
  └ AiConfig 取 provider / model / baseUrl / key（Keystore 解密）/ system / reasoning
  └ AiHttp.chat() → 按 provider.style 分派 openai / gemini / anthropic
  └ 返回 tool_calls → AiTools.needsConfirm() ?
        ├ 只读（server_status / read_logs / list_players）→ 直接执行
        └ 操作（start_server / stop_server / send_command）→ 弹窗确认后执行
  └ 结果回灌 → 再请求一次（非流式）→ 渲染气泡 → 保存会话
```

### 7.11 语言切换

```text
设置 → 语言 → 选择语言 → confirmLanguage()
  └ I18n.setLang(this, code)：写 ui/language → 重载词条 → Translations.invalidate()
  └ applyLanguage()：handler.post(recreate())
        recreate → onCreate → I18n.init → 全部视图按新语言重建（不重启进程、不打断部署）
```

---

## 8. 权限与 Shizuku 边界

| 能力 | 依赖 |
|---|---|
| 读写 `/data/local/tmp`、启动服务端、编译 Box64 | Shizuku 授权（`IShizukuService.newProcess`，uid 2000 shell） |
| 前台服务常驻与通知 | `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`POST_NOTIFICATIONS` |
| 屏幕常亮 | `WAKE_LOCK` + `FLAG_KEEP_SCREEN_ON` |
| 导入导出（`/sdcard/Download`） | 通过 Shizuku 写文件，或 `ACTION_OPEN_DOCUMENT`；`requestLegacyExternalStorage=true` |
| 局域网 IP、网络状态 | `ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`（读 `NetworkInterface`） |
| 电池优化白名单 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

**设计红线**：普通 App 身份不直接碰 `/data/local/tmp`；所有此类操作一律走 `ShizukuShell`/`ShizukuProcess`，
失败时向用户展示“需要 Shizuku”的引导而不是静默降级。

---

## 9. 代码规模

| 层 | 文件数 | 行数合计 |
|---|---|---|
| 外壳与导航（MainActivity、Splash、Logo、AppLog*） | 5 | 3,682 |
| UI 框架（MinecraftUi、ThemeManager、LiquidWallpaper） | 3 | 1,407 |
| 页面视图（Deploy/Server/Addons/World/Player/Backup/PlayerData*/Permission/Terminal*/Ai*/Dialogs） | 15 | 8,862 |
| 领域服务（DeployManager、Box64Installer、InstanceStore、ServerManager、ServerService、PlayerTracker…） | 14 | 2,650 |
| 系统访问（Shizuku*、EnvManager） | 4 | 598 |
| 数据层（LevelDB/NBT/OfflinePlayerData/Translations/ItemNames） | 10 | 2,267 |
| AI（AiConfig、AiHttp、AiTools） | 3 | 989 |
| i18n（I18n） | 1 | 175 |
| **合计** | **55** | **20,630** |

最大的 5 个文件：`MainActivity`(3434) > `DeploySectionView`(1759) > `MinecraftUi`(1142) > `DeployManager`(878) > `PlayerDataEditor`(859)。
其中 `MainActivity` 与 `DeploySectionView` 是改动最频繁的两处，后续若继续演进，建议按“页面 / 控制器 / 服务”再拆一层。

---

## 10. 扩展点

| 想加什么 | 改哪里 |
|---|---|
| 新的一级页面 | `MainActivity`：`pages` 数组扩到 N、`buildNavBar()` 的 `labels`/`icons`、`showPage()` 分支；图标需在 `MinecraftUi.PixelIconView.PATTERNS` 加图案 |
| 新的服务器管理分区 | `MANAGE_LABELS`/`MANAGE_DESCS` 加条目 → `openManageSection()` 分支 → 新建 `XxxSectionView`（构造参数一般 `(Activity, Host)`） |
| 新的设置子页 | `buildSettingsPage()` 的 `items` 数组 + 点击分支 + `showSettingsSubPage(I18n.t("标题"), view)` |
| 新的设备操作 | 一律新增 `ShizukuShell.sh(...)` 封装，禁止直接 `Runtime.exec` 或 `File` 访问 `/data/local/tmp` |
| 新的 AI 工具 | `AiTools.tools()` 加 `ToolDef`（JSON Schema）→ `needsConfirm()` 判定 → `run()` 实现；必要时在 `AiHttp` 的三种方言里同步转换 |
| 新的语言 | `I18n.LANGUAGES` 登记 + `normalize()` 识别 + `assets/i18n/ui/lang_<code>.json` + `assets/i18n/<code>/` 词典 + `legal/*_<code>.txt`，详见 `docs/i18n.md` |
| 新的界面文案 | 直接写中文并用 `I18n.t("...")` 包裹；`docs/i18n.md` 的 `verify.py` 会检查是否漏翻译 |
| 新的主题 | `ThemeManager` 常量 + `MinecraftUi.applyTheme()` 分支 + 各处 `if (MINIMAL||ORE||LIQUID)` 的绘制分支 |

**约定**

1. 视图只做展示与交互，文件/进程操作放领域层。
2. 所有跨线程回 UI 用 `handler.post`；耗时操作放 `io` 单线程池或独立线程。
3. 任何会改写 `/data/local/tmp` 的操作先 `ShizukuGate.ensure()`。
4. 修改玩家数据/回滚备份前必须停服——这条在 UI 层和服务层都做了拦截。

---

## 11. 历史遗留与注意事项

| 事项 | 说明 |
|---|---|
| `app/jniLibs/arm64-v8a/libqemu.so`（4.2 MB） | QEMU 用户态方案时期的产物，`build.ps1` 仍会打进 APK，但**代码中无任何 `System.loadLibrary` 调用**。留作回退方案参考，可择机移除（能减小 APK）。 |
| `EnvManager.serverDir()` → `filesDir/mcbds/server12662` | 旧版 App 私有目录。当前只有 `readProperties()`（首页初始化读端口/世界名）在用，实际配置在 `/data/local/tmp`，因此首页在未授权 Shizuku 时可能读到空值——属已知行为。 |
| `ServerManager` 默认端口 19134、名称 `Phone12662` | 硬编码默认值，真实值由 `server.properties` 与设置页覆盖。 |
| `docs/poc-2026-11-09.md`、`docs/1.26-qemu-solution.md`、`docs/app-v1.md` | 历史方案记录（QEMU / proot 早期形态），与当前实现不同，阅读时以本文档与源码为准。 |
| `androidx.annotation` 三个桩类 | 为了给 Shizuku jar 提供 `@NonNull` 等注解而存在的极简桩，不引入 AndroidX 依赖。 |
| `targetSdk 28` | 刻意保持低目标版本以规避高版本对 `/data/local/tmp` 与前台服务的额外限制；升级 targetSdk 前需重新评估 Shizuku 与存储策略。 |
| `allowBackup=true`、`usesCleartextTraffic=true` | 前者便于换机迁移，后者用于 http 镜像下载；如需上架需重新评估。 |
| 部署/备份进度在页面销毁后 | 依赖 `DeployState` 静态快照与 `DeployKeepAliveService` 通知，重建页面后能续上显示。 |

---

## 12. 相关文档

| 文档 | 内容 |
|---|---|
| `README.md` | 项目门面：功能、状态、构建、许可 |
| `docs/i18n.md` | 语言功能实现：词表结构、用法、禁译清单、校验脚本、新增语言步骤 |
| `docs/ui-design-v2.md` | UI 三套方案的设计说明与取舍 |
| `docs/ui-c-implemented.md` | 方案 C（Minecraft GUI）落地清单 |
| `docs/app-v1.md` | v1 可用版本记录（QEMU 时代） |
| `docs/1.26-qemu-solution.md` | 1.26 兼容方案：QEMU 用户态（历史） |
| `docs/poc-2026-11-09.md` | 首次跑通 BDS 的 PoC 记录（历史） |
| `THIRD_PARTY_NOTICES`、`SOURCES.md` | 第三方组件、许可证与源码获取方式 |
