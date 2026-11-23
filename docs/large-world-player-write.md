# 大存档玩家数据写回失效：WAL 记录未分片，被 LevelDB 静默丢弃

## 1. 一句话

`LevelDbWriter` 把整条 WriteBatch 写成**一条 FULL 物理记录**，超过 LevelDB 的
单记录上限 32761 字节（`BLOCK(32768) - HEADER(7)`）时，服务端恢复阶段会把整块剩余部分
判为损坏并丢弃——记录从未进入数据库；而 `verifyWritten()` 只判断「读得到 + 能解析」，
读回旧值也报「校验 ok」。小世界玩家数据 7 KB 所以正常，大存档玩家数据 37~121 KB 全部中招。

## 2. 证据 / 数据（可复现）

### 2.1 设备侧（2 GB 世界 `Bedrock level`，1174 个文件 / 1171 个 .ldb）

```
MANIFEST-4927964: log_number=4927966  next_file_number=4928055  last_sequence=263574172
4927966.log（1.74 MB）：4 条逻辑记录
   FULL  seq=263574043 count=7
   LAST  seq=263574050 count=41      ← 分片记录
   LAST  seq=263574091 count=82
   LAST  seq=263574173 count=1448
```

- 全量扫描 80,081 个数据块：**块 CRC 全部是标准 masked CRC32C，压缩类型 4 = raw deflate**，
  `BlockHandle.size` 只含数据、类型字节在数据之后（旧读取器的假设是对的）。
- `player_server_*` 共 319 条，体积：37,736 / 39,670 / 40,974 / 42,878 / 50,831 / 92,570 / 112,682 / **120,984** 字节……
- 小世界 `cs`：唯一的 `player_server_` 记录 = **7,473 字节**（< 32,761）→ 一直是好的。

### 2.2 实验室（`.tooling\dbprobe\bds`，真实 `bedrock_server.exe`，每轮起停）

| 编号 | 记录体积 | 写入方式 | BDS 起停后 |
|---|---|---|---|
| T1 | 33 B | 旧实现（单条 FULL） | ✅ 落盘 |
| T2 | 40 KB | 旧实现（单条 FULL） | ❌ **静默消失**（读回 `长度越界 @block1 len=40073`） |
| T3 | 40 KB | 按 log::Writer 分片 | ✅ 落盘 |
| T4 | 120 KB | 按 log::Writer 分片（原型） | ✅ 落盘 |
| T5 | 121 KB | **修复后的生产类** | ✅ 落盘（`000021.ldb`，120,022 字节） |

复现要点：实验室日志的物理记录最大长度恒为 32761，说明服务端自己写的一定分片；
旧实现写出的 `len=40073` 在块内越界，`log::Reader` 报截断后丢弃该块。

### 2.3 真机（2 GB 世界，改 `PlayerLevel` 147 → 148）

```
写前：4927966.log 1,742,632 字节（20:12）
写后：4927966.log 1,793,544 字节（21:33）   +50,912
  物理记录 59  CRC 错 0  长度错 0  最大物理记录 32761
  player_server_91ca37ee-…  seq=263575621  len=50831  PlayerLevel=148   ← 分片写入
起服停服后：4928118.ldb 里同一条 key  seq=263575621  len=50831  PlayerLevel=148
App 重新读取：经验等级 148（进度 0.20）
最后还原 147 → 4928057.log 新增 seq=263576271 len=50831 PlayerLevel=147
最终安装包再写一次（同值）→ seq=263576272 len=50831（分片、CRC 0 错）
```

附带收益：缓存从「每个用途各存一份整库」改为共用 `dbcache` + 轻量 `dbsmall`，
设备上该世界的缓存占用由 8,368 MB 降到 2,092 MB；老版本遗留的
`worlddb / editread / editwrite / editverify` 目录会在首次同步时自动清掉。

## 3. 排除清单（试过什么、为什么不行）

| 假设 | 结论 | 依据 |
|---|---|---|
| WAL 记录 CRC 算法不对 | ❌ 排除 | 设备日志 57 条物理记录、实验室日志、MANIFEST 全部用标准 masked CRC32C 校验通过 |
| MANIFEST 的 `log_number` 门限把日志挡掉 | ❌ 排除 | 大存档 `log_number=4927966`，现存唯一 `.log` 正是 4927966；实验室也一致 |
| sequence 太小被旧记录压住 | ❌ 不是主因（但确实是**第二个 bug**） | 真实最大 seq=263575620，旧代码算出 263574049（漏算分片记录），会撞号；修好序号后未分片的记录依旧被丢 |
| 换更大编号的日志 / 追加到服务端最新日志 | ❌ 无效 | 与日志编号无关，帧格式本身不合法 |
| 写回的是旧缓存文件 | ❌ 已加固 | 旧 `copyDbAt` 只比大小；改为「大小 + 时间戳 + CURRENT 归属」 |
| 只拷回一个 `.log`、`cat >` 截断 | ⚠️ 隐患已修 | 改为写前核对远端长度 + `cat >>` 只追加 + `sync` |

## 4. 修复内容

| 文件 | 改动 |
|---|---|
| `LevelDbWriter` | 按 `log::Writer` 语义分片（FULL/FIRST/MIDDLE/LAST，块尾不足 7 字节补零），每片独立 masked CRC，末尾 `fsync`；新增 `checkedTail()` 写前自检、`contains()/containsBatch()/containsDeletes()` 写后回验、`ensureLog()` 按 `log_number`/`next_file_number` 选或建日志 |
| `LevelDbReader` | 新增 `Manifest`（解析 `log_number`/`prev_log_number`/`next_file_number`/`last_sequence`）；日志解析统一为 `eachRecord()`（拼接分片）；`lastSequence()` 改为 max(MANIFEST.last_sequence, 所有 `.log` 的 `seq+count-1`)，不再全量扫 2 GB `.ldb`，也不会漏分片 |
| `OfflinePlayerData` | 写回/删除/备份导入改走「只同步 CURRENT+MANIFEST+*.log」的轻量缓存；写前 gate；写后「远端长度 + 分片 CRC + 字节相等 + 读回相等」四重校验；`currentWorldStrict()` 禁止兜底到别的世界；缓存按 `世界+CURRENT` 归属，删掉重复的 `editread/editwrite/editverify/worlddb` 多份 2 GB 副本；删除玩家前真的做一次整世界备份 |
| `ShizukuShell` | 新增 `remoteSize()` / `appendTail()`：核对远端长度后用 `cat >>` 追加新增字节并 `sync`，不再 `cat >` 截断整个 WAL |
| `PlayerDataEditor` | 去掉 65000 字节闸门（分片后无此限制），只保留 8 MB 防 OOM 上限；校验改为对写入字节做等值比较 |

## 5. 标注为「假设」的部分

- **BDS 各版本共用同一支 LevelDB 日志格式**：已验证 1.26.62.1（设备 arm64/box64）与
  Windows 版 `bedrock_server.exe`（`.tooling\bds-win`，不同构建）都接受同一份字节；
  未来版本若换压缩/换存储，写回会先被 `checkedTail()` / `contains()` 拦下并报错，不会静默写坏。
- **`MANIFEST.last_sequence` ≥ 已在 `.ldb` 里的任何 sequence**：依据是 LevelDB 每次
  `LogAndApply` 都写入当时的 `LastSequence()`。若某存档被第三方工具改写导致不成立，
  新记录的 sequence 会偏低，但 memtable 命中优先于 SST，仍能生效。
- **`log >> 1` 之外的分片边界**：按 LevelDB 的 `leftover < kHeaderSize` 规则补零，
  未在 BDS 侧反证（实验室 T3/T4/T5 覆盖了跨块与不跨块两种情形）。
