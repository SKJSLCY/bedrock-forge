# 玩家数据编辑器：交互修正与性能优化（第二轮）

## 1. 一句话

去掉每次保存都做的「整世界 tar 备份」，回滚改为截断本次追加的 WAL 尾部；保存结果改成必须点
「确定」的固定弹窗；进编辑器有真实进度条；保存后只重读该玩家并就地重绘；读存档改用
MANIFEST 的键范围跳过无关 SST——2 GB 存档的常用路径从分钟级降到秒级。

## 2. 逐条改动

| # | 需求 | 实现 |
|---|---|---|
| 1 | 保存不再备份 | `PlayerDataEditor.saveAsync` 删除 `backupDb()`（整世界 tar）；失败回滚改为 `WriteResult.logStart` + `LevelDbWriter.truncateLocal()` + `ShizukuShell.truncateRemote()`（追加式写入截断即回滚） |
| 2 | 提示改为固定弹窗 | 新增 `showResult(ok, msg)`：标题「保存完成 / 保存失败」，正文带 sequence，`setCancelable(false)`，必须点「确定」 |
| 3 | 进编辑器要有实时进度条 | `offlinePlayerData.readServerData(ctx, serverId, Progress)` 重载；`openEditor()` 先弹 `ProgressDialog`，按「读取数据库 N/M 文件 P%」推进，拷完切「正在解析玩家数据…」，成功/失败都会关闭 |
| 4 | 保存后玩家数据界面要更新 | `PlayerDataView` 给 `PlayerDataEditor.start` 传回调：`OfflinePlayerData.refreshRecord()` 只重读该玩家 NBT 并 `fill()` 重建卡片，然后就地重绘 |
| 5 | 读存档更快 | `LevelDbReader.list()` 用 MANIFEST 的 `new_file` 最小/最大键做范围过滤，跳过不可能命中的 SST；结果为空时自动全量重扫兜底 |
| 6 | 进编辑器更快 | `LevelDbReader.get()` 同样按范围过滤；`value()` 不再扫 1171 个文件 |
| 7 | 保存更快 | 去掉 2.1 GB tar（约占原耗时 99%）；写回只同步 `CURRENT/MANIFEST/*.log`（`dbsmall`）、只追加新增字节；`verifyWritten` 只读该 `.log` |
| 附 | 缓存不再重复 | 所有读用途共用 `dbcache`，老版本遗留的 `worlddb/editread/editwrite/editverify` 首次同步时自动清理 |

## 3. 实测（Redmi apollo / Android 17，2 GB 世界，1171 个 .ldb）

| 场景 | 改前 | 改后 |
|---|---|---|
| 首次打开玩家数据（冷缓存） | ≈ 200–240 s（全量拷贝 + 全量解析） | **68 s** |
| 再次打开玩家数据（热缓存） | ≈ 200 s（每次都重拷 + 重扫） | **3.6 s** |
| 点「编辑数据」到编辑器出现 | 无反馈，等待数分钟 | **3.5 s**（带进度条） |
| 保存（含校验） | ≈ 160 s（2.1 GB tar 备份） | **5.6 s** |
| 保存后玩家数据界面刷新 | 不刷新（仍是旧值） | **3.5 s**，显示新值 |
| 设备缓存占用 | 8,368 MB（多份整库副本） | 2,092 MB |

正确性抽查（离线，用真实 `LevelDbReader` 跑设备拉下来的 116 个 SST）：

```
过滤扫描: 键 1  扫 1 跳过 115
全量扫描: 键 1
键集合一致: true     值不一致的键数: 0
```

`get()` 与 `list()` 对同一玩家键返回同一份（最新）字节：`一致: true`。

## 4. 附带修复

- i18n：`verify.py` 由 `missing=38` 变为 `missing=0`（补了本轮新增文案与 0.3.0 遗留缺口，
  `final_sync.py` 同时清掉 11 个失效键，en 无残留汉字）。
- 设备上 18 个 `playeredit-*.tar.gz`（每次保存自动生成，约 39 GB）已清理；新版本不再产生。

## 5. 标注为「假设」的部分

- 「范围过滤不会漏键」依赖 MANIFEST 的 `new_file` 最小/最大键准确；已用 116 个真实 SST 对比
  全量扫描验证一致，且 `list()`/`get()` 在结果为空时会自动退回全量扫描。
- 保存回滚只还原「本次追加的字节」，不做整库回滚：因为写回只在日志尾部追加，截断即等价；
  若远端日志在写入期间被第三方改动，`appendTail` 的长度校验会直接拒绝写入。
