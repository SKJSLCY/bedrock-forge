# 方案 C 已实装（Minecraft GUI）

APK：`Q:\MC BDS APP\app\build\app-debug.apk`（已安装）

## 已实现

- **首页**（方案 C）
  - 服务器信息：类型、服务端版本、游戏版本、**IP 地址**、端口 / 世界
  - 运行状态、玩家在线
  - 服务器负荷：CPU / 内存 / 网络上下行 / TPS（像素分段条）
  - **启动 / 停止 / 重启** 三个 Minecraft 风格按钮
  - 下方另有全宽 **「服务器管理」** 按钮，点击进入服务器管理页
  - 底部物品栏导航：首页 / 控制台 / AI / 设置
- **控制台**：真实 BDS 日志（黑底绿字），命令输入 + 发送，`list` 可用
- **AI**：像素占位页「界面占位 · 暂未实装」
- **设置**：主题 / 权限管理 / AI 智能体 / 用户协议 / 关于（占位菜单）
- 状态灯：已停止（红）/ 启动中（琥珀）/ 运行中（绿）/ 停止中（琥珀）
- IP 和玩家数实时刷新；启动/停止/重启已接到现有 ServerService + Shizuku

## 未实装（按当前阶段要求）

- CPU / 内存 / 网络 / TPS 真实采样：显示为占位，面板标注「监控数据待接入」
- AI 对话
- 设置菜单各项功能
- 玩家列表、日志导出、备份

## 已知问题

1. 停止后立即启动可能不监听端口（19134 TIME_WAIT），等 60–75 秒再启动；“重启”按钮已改为 75 秒后自动启动。
2. 应用更新/强制停止也会让旧服务端进入 TIME_WAIT，第一次启动可能显示“启动中”但端口未就绪，需要再等一次。
3. 手机重启后 Shizuku 要用 `start-shizuku.ps1` 重新启动。

## 服务器管理页

首页「服务器管理」进入完整管理页，底部导航仍在：

- 服务端
- **运行环境**（占位）
- 插件加载器
- **服务器设置**（已实装）
- 插件管理
- addons管理
- 存档管理
- 玩家管理
- 服务器备份

除「服务器设置」外，其余都是“界面占位 · 暂未实装”，可进入、可返回。

### 服务器设置

- 读取实际路径 `/data/local/tmp/mcbds/server12662/server.properties`（通过 Shizuku）
- **覆盖当前 server.properties 的全部 48 个键**，另补 `server-ip`、`emit-server-telemetry`；运行时如出现未知键会自动追加到「高级（自动）」分组
- 汉化分组：基础 / 玩家 / 世界 / 性能 / 网络 / 权威移动与反作弊 / 日志与脚本
- 文本、整数、小数、开关（开/关）、枚举选择（游戏模式、难度、默认权限、聊天限制、压缩算法、传输协议、日志级别）
- **仅在服务端停止时可编辑**；保存时重新读取当前文件再合并写入，避免旧页面覆盖新修改
- 已实测：允许作弊、游戏模式的开关/枚举修改会写入实际文件，测试后已恢复原值
- 修改端口/传输协议后仍需重启服务端

## 设置菜单

底部「设置」按草图改为五项：

- 主题
- 权限管理
- AI 智能体
- 用户协议
- 关于

点击后弹出 Minecraft 风格占位弹窗「界面占位 · 功能暂未实装」。

截图：
- `Q:\MC BDS APP\.tooling\ui-manage-list3.png`（9 项管理列表）
- `Q:\MC BDS APP\.tooling\ui-settings-menu.png`（设置菜单）
- `Q:\MC BDS APP\.tooling\ui-settings-full1.png`（服务器设置 基础）
- `Q:\MC BDS APP\.tooling\ui-full-mid1.png`（服务器设置 日志与脚本）
- `Q:\MC BDS APP\.tooling\ui-settings-placeholder.png`（设置占位弹窗）

## 控制台

按草图重做：

- 顶部「控制台」+ 右侧「日志」按钮
- 灰色边框的大信息展示框，内部黑底终端
- 终端上方有 INFO / WARN / ERROR / DEBUG 颜色图例
- 输出按级别着色：
  - INFO：绿色
  - WARN / WARNING：琥珀色
  - ERROR / FATAL / SEVERE / Exception：红色
  - DEBUG：蓝色
  - 堆栈行 `at ...`：灰色
- 「快捷符号」横向条：`/ ~ ^ @ : " [ ] { } < > ! ? = , .`，点击插入到输入框光标处，不覆盖已有内容
- 输入栏 + 发送
- 「日志」弹窗：跳到最新 / 复制全部 / 关闭

实测：
- 输入 `abc` 后点 `~`，输入框变为 `abc~`，没有清空
- 发送非法命令后，BDS 返回的 `[ERROR] Unknown command` 显示为红色

截图：
- `Q:\MC BDS APP\.tooling\ui-console-final.png`（控制台布局与红色错误）
- `Q:\MC BDS APP\.tooling\ui-console-new.png`（无日志布局）
- `Q:\MC BDS APP\.tooling\ui-log-dialog.png`（日志弹窗）
- `Q:\MC BDS APP\.tooling\ui-symbol-insert.png`（快捷符号插入）

## AI 对话页

按草图做成聊天界面（功能未实装）：

- 顶部会话标签：新对话 / 新对话2，点击切换高亮
- 聊天区：用户消息右对齐绿色气泡，AI 助手左对齐灰色气泡
- 示例消息：用户「帮我检查一下日志」，AI「好的」
- 底部工具条：上传图片 / 上传文件 / 模型 / 思考强度
- 输入栏 + 发送
- 所有按钮和发送目前只弹「暂未实装」提示

截图：`Q:\MC BDS APP\.tooling\ui-ai-chat.png`

## 部署管理（服务端 / 运行环境 / 插件加载器）

服务器管理页的这三项已从占位改为实际部署页面：

- 自动检测本地是否已部署，显示名称、版本、路径、来源
- 已部署：显示「更换」+「删除」
- 未部署：显示「添加」
- 所有删除、更换前都有确认弹窗
- 下载/解压/复制/切换过程显示进度条、百分比、阶段日志，并同步写入控制台 `[部署]`
- 文件操作全部通过 Shizuku 以 shell 身份执行
- 服务端更换会保留 `worlds`、`server.properties`、白名单、权限、keys
- 支持从设备选择 `.zip` / `.tar.gz` 导入

### 服务端

- 下载源：MSL 国内镜像（`api.mslmc.cn`，页面注明「本服务由 MSL 开服器提供」）
- 版本列表：1.26.52.3 / 1.26.60.6 / 1.26.61.1 / 1.26.62.1 / 1.26.63.1
- 实测：下载并部署 1.26.63.1 成功，随后恢复回 1.26.62.1
- 下载支持断点续传，连接中断会自动重试

### 运行环境

- 检测：`rootfs-amd64` + Termux QEMU，显示 `Ubuntu 24.04.3 LTS · QEMU 11.1.3`
- 下载源：清华镜像 Ubuntu 24.04.3 / 22.04.5 rootfs
- 更换时会从旧环境复制 BDS 依赖库和 `resolv.conf`
- 若 QEMU 丢失，会从 APK 内置 assets 重新部署
- 实测：下载 Ubuntu 24.04.3 并切换成功，服务端可正常启动，`/v1/join` 返回 1.26.62

### 插件加载器

- Endstone 0.11.15（Linux 官方包，GitHub 代理）已实测下载并部署到 `server12662/endstone`
- LiteLoaderBDS 2.16.2 提供下载，但官方包只有 Windows DLL/EXE
- 从设备导入 ZIP，可选择导入为 Endstone 或 LiteLoaderBDS

### 已知限制

1. **LiteLoaderBDS 官方没有 Linux 版**，部署后 Linux 服务端无法加载；需要改用 LeviLamina 或 Wine 方案。
2. **Endstone 目前只是部署文件**，启动仍执行原版 `bedrock_server`，尚未接入 `endstone/start.sh`；且 Endstone 0.11.15 官方支持到 BDS 1.26.60，当前 1.26.62 可能不兼容。
3. 运行环境下载的是 Ubuntu base，依赖库靠旧环境复制；首次从零安装可能还需要 apt 补依赖。
4. 下载镜像的 1.26.62.1 文件目前返回 404，1.26.63.1 可正常下载。

截图：
- `Q:\MC BDS APP\.tooling\ui-server-page2.png`
- `Q:\MC BDS APP\.tooling\ui-server-versions.png`
- `Q:\MC BDS APP\.tooling\ui-runtime-retry.png`
- `Q:\MC BDS APP\.tooling\ui-loader-done.png`

## 多版本 / 备份 / 保活（本轮）

- 服务端改为 `servers/<version>/` 多版本目录 + `/data/local/tmp/mcbds/server` 软链接
- 服务端页新增「已安装版本」列表，可切换/删除；切换会同时使用该版本目录下的插件加载器
- 更换服务端前弹出「更换前备份」：备份并继续 / 不备份继续 / 取消
- 新增「备份当前…」和「回滚」；备份在 `/data/local/tmp/mcbds/backups/`
- 部署期间启动前台保活服务 `DeployKeepAliveService`，通知栏显示进度，锁屏/切后台继续
- 运行环境新增 Ubuntu 20.04.5 / 22.04.5 / 24.04.3 三个清华镜像选项
- Endstone 启动：只有当 `endstone/.venv/bin/python` 存在时才用 proot + start.sh；当前只部署了文件、没有 Python 环境，会自动回退原版并写日志

### 仍未完成

- LeviLamina：官方 Linux 方案是 Docker/Wine 运行 Windows BDS，不是原生 Linux；尚未实现 Wine 环境
- 下载 SHA256 校验与多镜像自动 fallback
- LiteLoaderBDS 已标记为 Windows-only，不再作为 Linux 加载器

## 监控 / Python 环境（本轮）

### 首页监控

已接入真实数据，全部使用普通 App API，不需要 Root/ADB：

- 设备 CPU：`/proc/stat` 差值
- 设备内存：`ActivityManager.MemoryInfo`
- 网络吞吐：`TrafficStats` 总量差值
- 服务器 TPS：每 10 秒发送 `time query gametime`，按 `Gametime is <ticks>` 差值计算，上限 20
- 面板标注「设备级监控 · 无需 Root/ADB」

注意：CPU/内存/网络是**整机**数据，不是 QEMU 单进程；要单进程必须 root/Shizuku。

### Python 环境

运行环境页新增「Python 环境（Endstone 用）」：

- 部署 Python 3.13.18（GitHub 代理，约 76MB）
- 显示已部署/未部署
- 可单独卸载

Endstone 的 venv 自动创建、`pip install` 和启动集成**还没接完**；目前 Python 环境是独立可部署项。

### 加载器清理

- 已移除 LiteLoaderBDS 选项
- 不再提供 LeviLamina（官方 Linux 方案是 Wine + Windows BDS，不是原生 Linux）
- 只保留 Endstone

## 代码

- `app/src/com/skjslcy/mcbds/MainActivity.java`：四页 + 底部导航 + 首页 + 管理页
- `app/src/com/skjslcy/mcbds/MinecraftUi.java`：像素面板/按钮/图标/进度条/泥土背景
- `app/src/com/skjslcy/mcbds/ServerSettingsView.java`：server.properties 读取、汉化、编辑、写回
- `app/src/com/skjslcy/mcbds/ShizukuShell.java`：通过 Shizuku 以 shell 身份读写实际文件
- 旧版单页 UI 已替换
