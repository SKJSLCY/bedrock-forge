# MC BDS App v1（可用）

APK：`Q:\MC BDS APP\app\build\app-debug.apk`（22MB，已安装到手机）

## 架构

```
MC BDS App (Java, targetSdk 28)
  └─ ServerService（前台服务）
       └─ Shizuku IShizukuService.newProcess（以 shell 身份启动进程）
            └─ /data/local/tmp/termux-qemu/qemu-x86_64
                 └─ -L /data/local/tmp/mcbds/rootfs-amd64 ./bedrock_server
                      └─ BDS 1.26.62（离线模式，端口 19134）
```

- QEMU 使用 Termux 的 bionic 版（`qemu-user-x86-64` 11.1.3），随 APK 以 assets 形式携带。
- 为什么必须走 Shizuku：Android 17 的 App 沙箱下，QEMU 加载 x86_64 客户机二进制会失败（SIGSYS / Exec format error）；同一套 QEMU/BDS 在 shell 身份下完全正常。Shizuku 提供 shell 身份，不需要 root。
- Shizuku 已安装（v13.6），服务已启动；App 已授权。

## 手机上的运行环境

- `/data/local/tmp/termux-qemu/`：`qemu-x86_64` + `lib/`（Termux QEMU 及依赖）
- `/data/local/tmp/mcbds/rootfs-amd64/`：x86_64 Ubuntu 基础库
- `/data/local/tmp/mcbds/server12662/`：BDS 1.26.62、世界、`keys/server_identity_key.pem`（身份密钥已持久化）

> 重启手机后 `/data/local/tmp` 仍在，但 Shizuku 服务需要重新用 ADB 启动。

## 启动 Shizuku（每次手机重启后）

```powershell
powershell -ExecutionPolicy Bypass -File "Q:\MC BDS APP\.tooling\start-shizuku.ps1"
```

然后打开 MC BDS → 启动。

## 已验证

- App 启动 / 停止服务端（`stop` 后 BDS `Quit correctly`，退出码 0）
- 控制台命令：`list` → `There are 0/10 players online:`
- `/v1/join` 返回 `Phone12662B / 1.26.62 / Nether62`
- 局域网世界可发现并进入（沿用之前的 NetherNet 验证）
- QEMU 内存约 520MB，空闲 TPS 约 20

## 已知问题

1. **手动地址仍会 124**：离线服务器身份不被客户端手动路径接受；局域网世界正常。需要在线模式才能修，而手机网络无法完成微软认证。
2. **停止后立即启动可能不监听端口**：BDS 重启时 19134 还在 TIME_WAIT，会“启动成功但不监听”。等 60–75 秒再启动即可。App 的“重启”按钮当前只等 12 秒，偏短，下一版改。
3. **设置页修改不会同步到 Shizuku 使用的 `/data/local/tmp` 环境**：App 写的是内部 `server.properties`，实际运行的是 `/data/local/tmp/mcbds/server12662/server.properties`。下一版加 Shizuku 同步。
4. **TPS 显示为 `--`**：尚未实现采样。
5. **首次安装的“导入环境”只复制到 App 内部**，Shizuku 模式实际使用 `/data/local/tmp`；需要下一版把环境安装也走 Shizuku。
6. 手机重启后 Shizuku 需 ADB 重新启动（无 root 的限制）。

## 下一版

- App 内一键通过 Shizuku 安装/修复 `/data/local/tmp` 环境
- 设置页通过 Shizuku 读写实际 server.properties
- 启动后检测端口监听，自动等待/重试，避免“假启动”
- TPS 采样、玩家列表、日志导出
- 在线模式/手动地址（需解决手机认证网络）
- Endstone / LeviLamina 加载器
