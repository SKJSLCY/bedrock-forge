# 基岩工坊（Bedrock Forge）

手机上的 Minecraft 基岩版服务端管理工具。

在 Android 设备上部署 Box64 + Ubuntu 运行环境，运行和管理的
Minecraft Bedrock Dedicated Server（BDS）。

> 本项目为非官方工具，与 Mojang Studios、Microsoft 及其关联公司
> 无隶属、授权或合作关系。

---

## 功能

- 运行环境部署（Box64、proot、Ubuntu Base）
- 服务端部署、启动、停止、重启
- 控制台命令与日志查看
- 服务器设置、白名单、权限管理
- 玩家数据查看、编辑、删除、备份
- 世界新建、切换、重命名、导入导出、备份回滚
- addons 导入、启用、禁用、删除
- AI 助手与 AI 智能体（多服务商、API Key 本地加密）
- 用户协议、关于页、环境诊断、常见问题

## 状态

```text
版本：0.1
平台：Android 8.0+ / arm64
许可证：GPL-3.0-or-later
```

本软件由 AI 制作。

## 重要说明

- 本软件不内置 Minecraft 客户端、官方服务端或官方美术素材。
- BDS 由应用从官方地址下载，或由用户自行导入。
- 部署与运行需要 Shizuku 提供 shell 权限。
- 修改玩家数据、回滚备份前必须停止服务端。
- 删除世界或玩家数据不可恢复，请先备份。

## 目录结构

```text
LICENSE                   GPL-3.0 许可证全文
NOTICE                    版权与项目声明
THIRD_PARTY_NOTICES       第三方组件与许可证
SOURCES.md                第三方源码获取说明
README.md                 本文件

app/
  AndroidManifest.xml
  build.ps1               构建脚本
  src/                    Java 源码
  res/                    资源
  assets/                 内置资源
    box64-main.tar.gz     Box64 源码
    proot/                proot 二进制与运行库
    toolchain-rootfs.tar.gz
                          Ubuntu Base rootfs 与工具链
    legal/                用户协议、更新日志
    i18n/                 玩家数据翻译
  libs/                   Shizuku 依赖

docs/                     开发文档
design/                   设计资料
```

## 构建

### 环境要求

```text
Windows + PowerShell
JDK 17
Android SDK Platform 35
Android Build Tools 35.0.0
```

### 修改构建路径

`app/build.ps1` 顶部包含本机路径：

```powershell
$sdk = 'Q:\MC BDS APP\.tooling\android-sdk'
$bt = Join-Path $sdk 'build-tools\35.0.0'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
```

请按你的实际环境修改。

### 执行构建

```powershell
.\app\build.ps1
```

产物：

```text
app/build/app-debug.apk
```

构建脚本会生成并使用 debug keystore 签名，仅用于测试。

### 大型资源

`app/assets/toolchain-rootfs.tar.gz` 约 269 MB，超过普通 Git 仓库单文件限制。

推荐方式：

```text
A. 使用 Git LFS 跟踪该文件
B. 将 rootfs 作为 Release 附件分发，构建前放入 app/assets/
C. 从 SOURCES.md 中的 Ubuntu Base 地址自行构建
```

如果未包含该文件，构建仍可完成，但 APK 内不会带 rootfs。

## 第三方组件

| 组件 | 许可证 | 说明 |
|---|---|---|
| Box64 | MIT | 在 ARM64 上运行 x86_64 服务端 |
| proot | GPL-2.0 | 无 Root 的 chroot 环境 |
| Ubuntu Base | 各软件包分别适用 | Linux 用户空间 |
| Shizuku | Apache-2.0 | shell 权限 |
| AOSP | Apache-2.0 | Android 平台 |

详见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) 与 [SOURCES.md](SOURCES.md)。

## 许可证

本项目以 **GPL-3.0-or-later** 发布，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

```text
你可以自由使用、修改、分发本软件；
衍生版本需同样以 GPL 发布；
本软件不提供任何担保。
```

## 反馈

```text
项目仓库：<待补充>
反馈渠道：<待补充>
作者：SKJSLCY
```

## 免责声明

本软件按“现状”提供，不提供任何明示或默示担保。
使用本软件部署、修改服务端产生的风险与后果由用户自行承担。

Minecraft、Minecraft Bedrock Edition 及相关商标归其权利人所有。
