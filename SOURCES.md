基岩工坊（Bedrock Forge）第三方源码获取说明

版本：1.0
更新日期：2026-11-13
适用软件：基岩工坊（com.skjslcy.bedrockforge）

本文件说明本软件随附第三方组件的源码获取方式，以及本软件自身的源码信息。

项目仓库：https://github.com/SKJSLCY/bedrock-forge
反馈渠道：https://github.com/SKJSLCY/bedrock-forge/issues
作者：SKJSLCY
许可证：GPL-3.0-or-later

================================================================
一、本软件自身源码
================================================================

软件名称：基岩工坊 / Bedrock Forge
包名：com.skjslcy.bedrockforge
许可证：GPL-3.0-or-later（见 LICENSE）

源码仓库：
  https://github.com/SKJSLCY/bedrock-forge

源码范围：
  · app/src 下的全部 Java 源码
  · app/res 下的资源
  · app/assets/legal 与 i18n
  · AndroidManifest.xml
  · app/build.ps1 构建脚本
  · gen-aidl / AIDL 接口定义
  · 本文件与 THIRD_PARTY_NOTICES、NOTICE、LICENSE

构建环境：
  · JDK 17
  · Android SDK Platform 35
  · Android Build Tools（aapt2 / d8 / zipalign / apksigner）
  · Windows PowerShell 或兼容 shell

构建方式：
  · 在项目根目录执行：app/build.ps1
  · 产物：app/build/app-debug.apk
  · 脚本使用 debug keystore 签名，仅用于测试

说明：
  · 构建脚本中的 Android SDK 路径可按本机环境调整
  · assets 中的 box64-main.tar.gz、proot、toolchain-rootfs.tar.gz
    为第三方组件，许可见 THIRD_PARTY_NOTICES

================================================================
二、Box64
================================================================

组件名：Box64
许可证：MIT
随包文件：app/assets/box64-main.tar.gz
SHA-256：
  E46F23926FBF0FD00382D18194B39436878BEF0F9600FB4806F415FBB12037E0

源码来源：
  https://github.com/ptitSeb/box64

说明：
  · 随包文件为 Box64 上游源码压缩包
  · 应用在设备上解压后编译，安装到 /data/local/tmp/box64
  · 本软件未对 Box64 源码做持久性修改；
    如构建过程中有补丁，应在源码仓库中记录

获取方式：
  · 直接使用随包源码压缩包
  · 或从上述 GitHub 仓库获取对应版本源码

================================================================
三、proot
================================================================

组件名：proot（Termux 版本）
版本：5.1.107.102
许可证：GPL-2.0
随包文件：
  app/assets/proot/proot
  app/assets/proot/lib/*
  app/assets/proot/libexec/*
proot 可执行文件 SHA-256：
  3621937C1C5A98D0D39CA599AF573A6BC15128690606D535E8B05DA00CEC4F41

源码来源：
  https://github.com/termux/proot

说明：
  · 本软件未修改 proot 的版权与许可证声明
  · 对应版本源码可通过上述仓库的标签/提交获取
  · 如无法定位完全一致的版本，可联系作者获取对应源码

源码提供承诺：
  · 自本版本分发之日起 3 年内，
    可向本软件作者索取 proot 对应源码
  · 联系方式见本文件末尾

================================================================
四、Ubuntu Base rootfs
================================================================

组件名：Ubuntu Base 24.04.5 LTS（ARM64）
提供方：Canonical Ltd. / Ubuntu 项目
随包文件：app/assets/toolchain-rootfs.tar.gz
SHA-256：
  E47722F3C6EBECD145A7E6C806A3BCF7D64217B10391456AB8E365632521E8C1

rootfs 说明：
  · 基于 Ubuntu Base 24.04.5 ARM64
  · 为在手机上编译 Box64，安装了编译工具链
  · 已安装包清单见 rootfs 内 /var/lib/dpkg/status
  · 包含但不限于：
      build-essential、gcc、g++、make、
      git、curl、ca-certificates、
      libc6-dev、binutils、python3 等
  · 未删除各软件包的 /usr/share/doc/*/copyright

各软件包源码获取：
  · Ubuntu 源码包仓库：
      https://archive.ubuntu.com/ubuntu/pool/
  · Ubuntu 源码查询：
      https://sources.ubuntu.com/
  · Ubuntu Base 镜像：
      https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/
  · 本软件使用的下载镜像：
      https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/

获取对应源码的方法：
  · 在相同版本的 Ubuntu 24.04（noble）环境中启用 deb-src 后执行：
      sudo apt-get source <包名>
  · 或从 archive.ubuntu.com 下载对应版本的 .dsc / .orig.tar.* / .debian.tar.*

源码提供承诺：
  · 自本版本分发之日起 3 年内，
    可向本软件作者索取 rootfs 内 GPL/LGPL 软件包的对应源码
  · 如需完整软件包清单，可提供 /var/lib/dpkg/status

================================================================
五、Shizuku
================================================================

组件名：Shizuku API / Provider
许可证：Apache-2.0
随包文件：
  app/libs/shizuku-api13.jar
  app/libs/shizuku-provider13.jar
SHA-256：
  shizuku-api13.jar
    4315FCD853F487321548BE5A997749FE43105FA29AAAA6F6E7A8CDC958A38357
  shizuku-provider13.jar
    35AEE86B938CB8D06B315C9D73C30F41EC7CFDC10009A5DE83733BC5C4DB5051

源码来源：
  https://github.com/RikkaApps/Shizuku

说明：
  · 两个 jar 为 Shizuku 项目提供的依赖
  · 未修改其版权与许可证声明
  · Shizuku 项目许可证全文：
      https://www.apache.org/licenses/LICENSE-2.0

================================================================
六、Android Open Source Project
================================================================

项目：AOSP
许可证：Apache-2.0

说明：
  · 本软件基于 Android SDK 构建
  · 未单独分发 AOSP 源代码
  · 源码获取：https://source.android.com/

================================================================
七、服务与数据来源
================================================================

MCAPKS
  · 历史版本列表接口
  · https://mcapks.net
  · 不是随包分发的开源代码

Mojang / Microsoft
  · Minecraft 基岩版专用服务端官方下载
  · 本软件不内置 BDS 本体

清华大学 TUNA 镜像
  · Ubuntu Base rootfs 下载镜像
  · https://mirrors.tuna.tsinghua.edu.cn/

上述服务的使用应遵守其各自条款；本软件不控制其可用性。

================================================================
八、未包含组件
================================================================

本软件不包含：

  · QEMU（已在 0.1 版本移除）
  · Minecraft 客户端、官方服务端本体、官方素材
  · Mojang / Microsoft 商标素材
  · 付费闭源组件

================================================================
九、源码提供承诺与联系方式
================================================================

源码提供承诺：
  · 本软件自身源码：见项目仓库（GPL-3.0-or-later）
  · proot 源码：自本版本分发之日起 3 年内可索取
  · Ubuntu rootfs 内 GPL/LGPL 软件包源码：
    自本版本分发之日起 3 年内可索取

联系方式：
  · 作者：SKJSLCY
  · 项目仓库：https://github.com/SKJSLCY/bedrock-forge
  · 反馈渠道：https://github.com/SKJSLCY/bedrock-forge/issues

如发现源码获取方式有遗漏或错误，请通过上述渠道反馈。
