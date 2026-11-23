# 设备导入（服务端 / addons / 存档 / 备份）：一次 I/O + 实时进度

## 1. 一句话

从设备选文件导入时，不再「先拷进 App 缓存、再拷进 /data/local/tmp」走两遍，而是把 ContentResolver
的流直接灌进 shell 进程；解压改为逐条回报进度；最后落到目标目录用 `mv`（同一分区改名）而不是
`cp -a`。真机 96.6 MB / 4104 条目的服务端包，5 秒时进度条已在「解压 1109/4104（44%）」。

## 2. 改动

| 层 | 改前 | 改后 |
|---|---|---|
| 文件来源 | `onActivityResult` 把 content:// 整份拷到 `cache/import_*.ext`，再交给各流程 | 新增 `ImportSource`（持有 Uri + 名字 + 大小），流程直接流式读取；页面状态丢失时才 `materialize()` 落盘 |
| 上传 | `ShizukuShell.copyIn(File)`，64 KB 缓冲 | `copyInStream(ContentResolver, Uri)` 直通 `cat > 远端`，1 MB 缓冲；`copyIn` 同步提到 1 MB |
| 解压进度 | `unzip -q -o` / `tar -xzf` 阻塞执行，只有「正在解压…」的转圈 | `countEntries()` 先数条目（zip 用 `unzip -l`，tar 用 `tar -tzf \| wc -l`），再 `runLines()` 逐条读 `inflating:/extracting:/creating:`，实时 0..100% |
| 落位 | `rm -rf dest; cp -a root/. dest/`（整份再复制一次） | `rm -rf dest; mv root dest`（同分区改名，失败才退回复制） |
| 进度分配 | 服务端 15% → 55% 之间无反馈 | 上传 1..30%，解压 33..75%（按条目数），其余按阶段 |

四个入口都走同一套：`DeploySectionView`（服务端）、`AddonsSectionView`、`WorldSectionView`、
`BackupSectionView`，回调签名统一为 `PickCallback.onPicked(ImportSource)`。

## 3. 真机实测（Redmi apollo / Android 17，`/data` 剩余 164 GB）

| 导入 | 体积 | 结果 |
|---|---|---|
| 服务端 TAR.GZ | 96.6 MB / 4104 条目 | 上传+解压约 8 s；进度条实测「44% · 解压 1109/4104」，日志逐条 `解压 N/4104`；成功后自动切到新实例 |
| addons (.mcpack) | 110.6 MB / 21 条目 | 5 s 内完成（上传 ~25 MB/s，与 `adb push` 的 29 MB/s 同量级），落盘 101 MB，列表立即出现该行为包 |
| 存档 (.mcworld) | 245 B | 秒级完成，`worlds/myworld` 出现 |
| 备份 (.tar.gz) | 2.1 KB | 秒级完成，列表顶部出现该备份（类型/实例/时间正确） |

减少的 I/O：旧路径每个包都要「读一遍内容流、写一遍 App 缓存、再读缓存、写一遍 /data/local/tmp」，
96 MB 的包约 288 MB 往返；现在只有 ~192 MB，且解压后不再整份 `cp -a`。

## 4. 测试后已还原

服务端导入会新建实例并切换 `server` 软链，测试后已 `rm -rf servers/1.26.62` → `ln -sfn servers/1.26.62.1 server`，
2 GB 世界在测试期间临时移出、测试后移回（`worlds/Bedrock level` 2095 MB 完好），
测试产生的 addon / 世界 / 备份、以及 `/sdcard/Download` 下的测试文件均已删除；
App 内显示「版本 1.26.62.1 · 来源 实例下载 · 实例 SC」。

## 5. 假设与限制

- 解压进度依赖 `unzip`/`tar` 的详细输出；`unzip` 若对某些条目只输出摘要行，进度会略快于实际，
  但不会卡在 0% —— 结束时强制置 100。
- `mv` 优化要求源目录与目标在同一分区（本工程都在 `/data`，成立）；不成立时自动退回 `cp -a`。
- 内容流的 `SIZE` 取决于文件提供方（部分网盘不返回），拿不到时进度按已读字节自增而不是百分比。
