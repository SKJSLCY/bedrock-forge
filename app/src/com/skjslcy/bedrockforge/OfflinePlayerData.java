package com.skjslcy.bedrockforge;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 服务端离线时直接读取世界 LevelDB 中的玩家数据。
 * 注意：基岩版存档只保存 MSA/ServerId，不保存玩家名，因此按“记录”列出。
 */
final class OfflinePlayerData {

    static final class Row {
        String label;
        String value;
        String sub;
        Map<String, Object> item;

        Row(String label, String value) {
            this(label, value, null);
        }

        Row(String label, String value, String sub) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.sub = sub;
        }
    }

    static final class Record {
        String msaId = "";
        String serverId = "";
        String boundName = "";
        Map<String, Object> nbt;
        List<Row> basic = new ArrayList<>();
        List<Row> inventory = new ArrayList<>();
        List<Row> ender = new ArrayList<>();
        List<Row> effects = new ArrayList<>();
        LinkedHashMap<String, List<Row>> groups = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        List<String> raw = new ArrayList<>();

        String label() {
            String id = msaId.isEmpty() ? serverId : msaId;
            if (id.length() > 8) id = id.substring(0, 8);
            if (boundName != null && !boundName.isEmpty()) return boundName + "（" + id + "）";
            return "账号 " + id;
        }
    }

    static final class Snapshot {
        String world = "";
        String error;
        List<Record> records = new ArrayList<>();
    }

    private static final Set<String> CONSUMED = new HashSet<>(Arrays.asList(
            "Pos", "DimensionId", "Health", "Air", "PlayerLevel", "PlayerLevelProgress",
            "PlayerGameMode", "Inventory", "Armor", "EnderChestInventory", "ActiveEffects"));

    private static final Set<String> STATE_FIELDS = new HashSet<>(Arrays.asList(
            "Sitting", "Sleeping", "Sneaking", "OnGround", "SleepTimer", "TimeSinceRest",
            "canPickupItems", "hasSetCanPickupItems", "expDropEnabled"));

    private static final Set<String> SPAWN_DEATH = new HashSet<>(Arrays.asList(
            "SpawnX", "SpawnY", "SpawnZ", "SpawnDimension",
            "SpawnBlockPositionX", "SpawnBlockPositionY", "SpawnBlockPositionZ",
            "DeathDimension", "DeathPositionX", "DeathPositionY", "DeathPositionZ"));

    private static final Set<String> UI_FIELDS = new HashSet<>(Arrays.asList(
            "InventoryLayout", "InventoryFiltering", "CraftingLayout",
            "FurnaceLayout", "FurnaceFiltering", "SmokerLayout", "SmokerFiltering",
            "BlastFurnaceLayout", "BlastFurnaceFiltering",
            "LeftInventoryTabSelection", "RightInventoryTabSelection",
            "LeftFurnaceTabSelection", "LeftSmokerTabSelection", "LeftBlastFurnaceTabSelection",
            "SelectedContainerId", "SelectedInventorySlot", "MapIndex", "PlayerUIItems",
            "SlotDropChances", "Mainhand"));

    private static final Set<String> BOOL_FIELDS = new HashSet<>(Arrays.asList(
            "HasSeenCredits", "HasDiedBefore", "canPickupItems", "hasSetCanPickupItems",
            "expDropEnabled", "Sleeping", "Sneaking", "Sitting", "OnGround"));

    private OfflinePlayerData() {
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static Snapshot load(Context ctx) {
        Snapshot out = new Snapshot();
        try {
            Translations.ensureLoaded(ctx);
            String world = currentWorld();
            out.world = world;
            String dbDir = DeployManager.SERVER_DIR + "/worlds/" + world + "/db";
            if (!"1".equals(ShizukuShell.sh("test -d " + q(dbDir) + " && echo 1 || echo 0").trim())) {
                throw new Exception("找不到世界数据库：" + world);
            }
            File local = new File(ctx.getCacheDir(), "worlddb");
            deleteTree(local);
            if (!local.mkdirs()) throw new Exception("无法创建临时目录");

            String list = ShizukuShell.sh("ls -1 " + q(dbDir) + " 2>/dev/null || true");
            int copied = 0;
            for (String line : list.split("\n")) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                if (!(name.equals("CURRENT") || name.startsWith("MANIFEST")
                        || name.endsWith(".ldb") || name.endsWith(".sst") || name.endsWith(".log"))) {
                    continue;
                }
                File target = new File(local, name);
                try {
                    ShizukuShell.copyOut(dbDir + "/" + name, target, null);
                    copied++;
                } catch (Throwable ignored) {
                }
            }
            if (copied == 0) throw new Exception("未能复制任何数据库文件");

            java.util.LinkedHashMap<String, byte[]> all = LevelDbReader.list(local, "player_");
            List<String> playerKeys = new ArrayList<>();
            for (String k : all.keySet()) {
                if (k.startsWith("player_server_")) continue;
                if (!k.startsWith("player_")) continue;
                playerKeys.add(k);
            }
            for (String k : playerKeys) {
                Record r = new Record();
                r.msaId = k.substring("player_".length());
                byte[] stub = all.get(k);
                if (stub != null) {
                    try {
                        Map<String, Object> m = BedrockNbt.parse(stub);
                        String sid = BedrockNbt.str(m.get("ServerId"));
                        if (sid.startsWith("player_server_")) {
                            r.serverId = sid.substring("player_server_".length());
                        }
                    } catch (Throwable ignored) {
                    }
                }
                byte[] data = null;
                if (!r.serverId.isEmpty()) data = all.get("player_server_" + r.serverId);
                if (data == null) data = all.get(k);
                if (data == null || data.length == 0) continue;
                try {
                    r.nbt = BedrockNbt.parse(data);
                } catch (Throwable t) {
                    continue;
                }
                fill(r);
                r.boundName = PlayerMapping.nameForMsa(ctx, r.msaId);
                out.records.add(r);
            }
            if (out.records.isEmpty()) {
                throw new Exception("存档里没有找到玩家记录（可能该世界还没有玩家进入过）");
            }
            java.util.Collections.sort(out.records, (a, b) -> a.label().compareTo(b.label()));
        } catch (Throwable t) {
            out.error = t.getMessage() == null ? t.toString() : t.getMessage();
        }
        return out;
    }

    static String currentWorldForEdit(Context ctx) {
        try {
            return currentWorld();
        } catch (Throwable t) {
            return "Bedrock level";
        }
    }

    /** 读取指定玩家服务器的原始 NBT，用于编辑。 */
    static byte[] readServerData(Context ctx, String serverId) throws Exception {
        File local = copyDb(ctx, "editread");
        return LevelDbReader.value(local, "player_server_" + serverId);
    }

    /** 追加 WAL 并写回设备，返回使用的 sequence。 */
    static long writeBack(Context ctx, String key, byte[] value) throws Exception {
        File local = copyDb(ctx, "editwrite");
        long seq = LevelDbReader.lastSequence(local) + 1;
        LevelDbWriter.append(local, seq, key.getBytes("UTF-8"), value);
        File log = LevelDbWriter.newestLog(local);
        if (log == null) throw new Exception("找不到可写回的日志文件");
        String remote = DeployManager.SERVER_DIR + "/worlds/" + currentWorld() + "/db/" + log.getName();
        ShizukuShell.copyIn(log, remote, null);
        return seq;
    }

    /** 写回后重新读取校验。 */
    static String verifyWritten(Context ctx, String key) throws Exception {
        File local = copyDb(ctx, "editverify");
        byte[] v = LevelDbReader.value(local, key);
        if (v == null) throw new Exception("写入后读不到玩家数据");
        NbtCodec.parse(v);
        return "ok";
    }

    static long deletePlayerData(Context ctx, String msaId, String serverId) throws Exception {
        File local = copyDb(ctx, "editdel");
        long seq = LevelDbReader.lastSequence(local) + 1;
        LevelDbWriter.appendDeletes(local, seq, "player_" + msaId, "player_server_" + serverId);
        pushLog(local);
        return seq;
    }

    static String verifyDeleted(Context ctx, String msaId, String serverId) throws Exception {
        File local = copyDb(ctx, "editdelv");
        if (LevelDbReader.value(local, "player_" + msaId) != null) throw new Exception("账号桩仍存在");
        if (LevelDbReader.value(local, "player_server_" + serverId) != null) throw new Exception("玩家数据仍存在");
        return "ok";
    }

    static String backupPlayer(Context ctx, String name, String msaId, String serverId,
                               String version) throws Exception {
        File local = copyDb(ctx, "editbak");
        byte[] stub = LevelDbReader.value(local, "player_" + msaId);
        byte[] data = LevelDbReader.value(local, "player_server_" + serverId);
        if (data == null) throw new Exception("找不到玩家数据");
        File dir = new File(ctx.getCacheDir(), "pbak");
        deleteTree(dir);
        if (!dir.mkdirs()) throw new Exception("无法创建备份临时目录");
        writeFile(new File(dir, "data.nbt"), data);
        if (stub != null) writeFile(new File(dir, "stub.bin"), stub);
        String meta = "{\"kind\":\"player\",\"name\":\"" + safe(name)
                + "\",\"msaId\":\"" + msaId + "\",\"serverId\":\"" + serverId
                + "\",\"world\":\"" + safe(currentWorld()) + "\",\"version\":\"" + version + "\"}";
        writeFile(new File(dir, "meta.json"), meta.getBytes("UTF-8"));
        ShizukuShell.sh("mkdir -p /data/local/tmp/mcbds/tmp/pbak; rm -rf /data/local/tmp/mcbds/tmp/pbak/*");
        for (String fn : new String[]{"data.nbt", "stub.bin", "meta.json"}) {
            File f = new File(dir, fn);
            if (f.exists()) ShizukuShell.copyIn(f, "/data/local/tmp/mcbds/tmp/pbak/" + fn, null);
        }
        String tag = msaId.length() > 8 ? msaId.substring(0, 8) : msaId;
        String out = DeployManager.BACKUPS_DIR + "/players-" + version + "-" + safe(name)
                + "-" + tag + ".tar.gz";
        ShizukuShell.sh("mkdir -p " + q(DeployManager.BACKUPS_DIR)
                + "; tar -czf " + q(out) + " -C /data/local/tmp/mcbds/tmp/pbak .");
        if (!"1".equals(ShizukuShell.sh("test -f " + q(out) + " && echo 1 || echo 0").trim())) {
            throw new Exception("备份创建失败");
        }
        return out;
    }

    private static void pushLog(File local) throws Exception {
        File log = LevelDbWriter.newestLog(local);
        if (log == null) throw new Exception("找不到可写回的日志文件");
        String remote = DeployManager.SERVER_DIR + "/worlds/" + currentWorld() + "/db/" + log.getName();
        ShizukuShell.copyIn(log, remote, null);
    }

    private static void writeFile(File f, byte[] b) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(b);
        out.close();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    static final String SERVER_BASE = "/data/local/tmp/mcbds/servers";

    static java.util.List<String[]> instances() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            String raw = ShizukuShell.sh("for d in " + SERVER_BASE
                    + "/*/; do [ -f \"$d.mcbds_version\" ] && echo \"$(basename $d)|$(cat $d.mcbds_version)\"; done");
            for (String line : raw.split("\n")) {
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                out.add(new String[]{line.substring(0, bar), line.substring(bar + 1).trim()});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static java.util.List<String> worldsOf(String instanceDir) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            String raw = ShizukuShell.sh("ls -1 " + q(instanceDir + "/worlds") + " 2>/dev/null");
            for (String line : raw.split("\n")) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static String extractBackup(String backup) throws Exception {
        String tmp = "/data/local/tmp/mcbds/tmp/pbak_ext";
        ShizukuShell.sh("rm -rf " + q(tmp) + "; mkdir -p " + q(tmp)
                + "; tar -xzf " + q(DeployManager.BACKUPS_DIR + "/" + backup) + " -C " + q(tmp)
                + " 2>/dev/null || true");
        return tmp;
    }

    static String backupMeta(String backup) throws Exception {
        String tmp = extractBackup(backup);
        return ShizukuShell.read(tmp + "/meta.json");
    }

    static byte[] backupMember(Context ctx, String backup, String member) throws Exception {
        String tmp = extractBackup(backup);
        File f = new File(ctx.getCacheDir(), "pbak_member");
        if (f.exists()) f.delete();
        ShizukuShell.copyOut(tmp + "/" + member, f, null);
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n <= 0) break;
            off += n;
        }
        in.close();
        return b;
    }

    static boolean hasPlayer(Context ctx, String instanceDir, String world, String msaId)
            throws Exception {
        File local = copyDbAt(ctx, "phas", instanceDir + "/worlds/" + world + "/db");
        return LevelDbReader.value(local, "player_" + msaId) != null;
    }

    static byte[] filtered(byte[] full, boolean withPosition) throws Exception {
        NbtCodec.Tag root = NbtCodec.parse(full);
        Map<String, NbtCodec.Tag> src = NbtCodec.map(root);
        if (src == null) throw new Exception("NBT 格式异常");
        String[] core = {"Inventory", "Armor", "Offhand", "EnderChestInventory",
                "PlayerLevel", "PlayerLevelProgress", "PlayerGameMode",
                "Health", "Air", "ActiveEffects", "Attributes", "Tags"};
        String[] pos = {"Pos", "Rotation", "DimensionId",
                "SpawnX", "SpawnY", "SpawnZ", "SpawnDimension",
                "SpawnBlockPositionX", "SpawnBlockPositionY", "SpawnBlockPositionZ"};
        java.util.LinkedHashMap<String, NbtCodec.Tag> keep = new java.util.LinkedHashMap<>();
        for (String k : core) if (src.get(k) != null) keep.put(k, src.get(k));
        if (withPosition) {
            for (String k : pos) if (src.get(k) != null) keep.put(k, src.get(k));
        }
        return NbtCodec.write(new NbtCodec.Tag(NbtCodec.COMPOUND, keep));
    }

    static String applyPlayer(Context ctx, String instanceDir, String world,
                              String msaId, String serverId, byte[] stub, byte[] data)
            throws Exception {
        String dbDir = instanceDir + "/worlds/" + world + "/db";
        String bak = DeployManager.BACKUPS_DIR + "/playerapply-" + System.currentTimeMillis()
                + ".tar.gz";
        ShizukuShell.sh("mkdir -p " + q(DeployManager.BACKUPS_DIR)
                + "; tar -czf " + q(bak) + " -C " + q(instanceDir + "/worlds")
                + " " + q(world) + " 2>/dev/null || true");
        File local = copyDbAt(ctx, "papply", dbDir);
        String oldSid = "";
        byte[] oldStub = LevelDbReader.value(local, "player_" + msaId);
        if (oldStub != null) {
            try {
                Map<String, Object> sm = BedrockNbt.parse(oldStub);
                String sid = BedrockNbt.str(sm.get("ServerId"));
                if (sid.startsWith("player_server_")) {
                    oldSid = sid.substring("player_server_".length());
                }
            } catch (Throwable ignored) {
            }
        }
        long seq = LevelDbReader.lastSequence(local) + 1;
        String delKey = (!oldSid.isEmpty() && !oldSid.equals(serverId))
                ? "player_server_" + oldSid : null;
        LevelDbWriter.appendPlayerOps(local, seq, "player_" + msaId, stub,
                "player_server_" + serverId, data, delKey);
        File log = LevelDbWriter.newestLog(local);
        if (log == null) throw new Exception("找不到可写回的日志文件");
        ShizukuShell.copyIn(log, dbDir + "/" + log.getName(), null);
        File verify = copyDbAt(ctx, "pverify", dbDir);
        if (LevelDbReader.value(verify, "player_" + msaId) == null) {
            throw new Exception("写入后账号桩不存在");
        }
        byte[] back = LevelDbReader.value(verify, "player_server_" + serverId);
        if (back == null) throw new Exception("写入后玩家数据不存在");
        NbtCodec.parse(back);
        return "ok";
    }

    static int compareVersion(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int yi = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (xi != yi) return xi > yi ? 1 : -1;
        }
        return 0;
    }

    private static File copyDbAt(Context ctx, String name, String dbDir) throws Exception {
        File local = new File(ctx.getCacheDir(), name);
        deleteTree(local);
        if (!local.mkdirs()) throw new Exception("无法创建临时目录");
        String list = ShizukuShell.sh("ls -1 " + q(dbDir) + " 2>/dev/null || true");
        int copied = 0;
        for (String line : list.split("\n")) {
            String n = line.trim();
            if (n.isEmpty()) continue;
            if (!(n.equals("CURRENT") || n.startsWith("MANIFEST")
                    || n.endsWith(".ldb") || n.endsWith(".sst") || n.endsWith(".log"))) continue;
            try {
                ShizukuShell.copyOut(dbDir + "/" + n, new File(local, n), null);
                copied++;
            } catch (Throwable ignored) {
            }
        }
        if (copied == 0) throw new Exception("未能复制数据库文件");
        return local;
    }

    private static File copyDb(Context ctx, String name) throws Exception {
        String world = currentWorld();
        String dbDir = DeployManager.SERVER_DIR + "/worlds/" + world + "/db";
        File local = new File(ctx.getCacheDir(), name);
        deleteTree(local);
        if (!local.mkdirs()) throw new Exception("无法创建临时目录");
        String list = ShizukuShell.sh("ls -1 " + q(dbDir) + " 2>/dev/null || true");
        int copied = 0;
        for (String line : list.split("\n")) {
            String n = line.trim();
            if (n.isEmpty()) continue;
            if (!(n.equals("CURRENT") || n.startsWith("MANIFEST")
                    || n.endsWith(".ldb") || n.endsWith(".sst") || n.endsWith(".log"))) continue;
            try {
                ShizukuShell.copyOut(dbDir + "/" + n, new File(local, n), null);
                copied++;
            } catch (Throwable ignored) {
            }
        }
        if (copied == 0) throw new Exception("未能复制数据库文件");
        return local;
    }

    private static String currentWorld() throws Exception {
        String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
        for (String line : props.split("\n")) {
            String t = line.trim();
            if (t.startsWith("level-name=")) {
                String v = t.substring("level-name=".length()).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "Bedrock level";
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private static void fill(Record s) {
        Map<String, Object> m = s.nbt;

        List<Object> pos = BedrockNbt.asList(m.get("Pos"));
        String posText = "未知";
        if (pos != null && pos.size() >= 3) {
            posText = String.format(Locale.US, "%.1f, %.1f, %.1f",
                    d(pos.get(0)), d(pos.get(1)), d(pos.get(2)));
        }
        s.basic.add(new Row("位置", posText));

        Object dim = first(m, "DimensionId", "Dimension");
        if (dim != null) s.basic.add(new Row("维度", Translations.dim(dim)));

        Object hp = m.get("Health");
        if (hp != null) s.basic.add(new Row("生命", Translations.num(hp)));

        Object air = m.get("Air");
        if (air != null) s.basic.add(new Row("氧气", Translations.num(air)));

        Object xp = m.get("PlayerLevel");
        Object xpP = m.get("PlayerLevelProgress");
        if (xp != null) {
            String v = Translations.num(xp);
            if (xpP != null) v += "（进度 " + Translations.num(xpP) + "）";
            s.basic.add(new Row("经验等级", v));
        }

        Object gm = first(m, "PlayerGameMode", "GameMode");
        if (gm != null) s.basic.add(new Row("游戏模式", Translations.gamemode(gm)));

        List<Object> inv = BedrockNbt.asList(m.get("Inventory"));
        if (inv != null) {
            for (Object item : inv) {
                Row row = itemRow(BedrockNbt.asMap(item), null);
                if (row != null) s.inventory.add(row);
            }
        }
        List<Object> armorList = BedrockNbt.asList(m.get("Armor"));
        if (armorList != null) {
            int idx = 0;
            for (Object item : armorList) {
                Map<String, Object> im = BedrockNbt.asMap(item);
                if (im == null) continue;
                Row row = itemRow(im, ItemNames.armorSlotByIndex(idx));
                if (row != null) {
                    s.inventory.add(row);
                    idx++;
                }
            }
        }

        List<Object> offhand = BedrockNbt.asList(m.get("Offhand"));
        if (offhand != null) {
            for (Object item : offhand) {
                Row row = itemRow(BedrockNbt.asMap(item), "副手");
                if (row != null) s.inventory.add(row);
            }
        }

        fillContainer(s.ender, BedrockNbt.asList(m.get("EnderChestInventory")));

        fillEffects(s.effects, BedrockNbt.asList(first(m, "ActiveEffects", "Effects", "MobEffects")));

        fillGroups(s, m);
    }

    private static void fillContainer(List<Row> out, List<Object> items) {
        if (items == null) return;
        for (Object item : items) {
            Row row = itemRow(BedrockNbt.asMap(item), null);
            if (row != null) out.add(row);
        }
    }

    private static Row itemRow(Map<String, Object> im, String slotLabel) {
        if (im == null) return null;
        String name = ItemNames.display(BedrockNbt.str(im.get("Name")));
        if (name.isEmpty()) return null;
        String label = slotLabel != null ? slotLabel : "槽位 " + BedrockNbt.str(im.get("Slot"));
        Object count = im.get("Count");
        String value = name + (count == null ? "" : " ×" + Translations.num(count));
        String detail = itemDetail(im);
        Row row = new Row(label, value, detail);
        row.item = im;
        return row;
    }

    private static String itemDetail(Map<String, Object> im) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> tag = BedrockNbt.asMap(im.get("tag"));
        if (tag != null) {
            List<Object> ench = BedrockNbt.asList(tag.get("ench"));
            if (ench != null && !ench.isEmpty()) {
                for (Object e : ench) {
                    Map<String, Object> em = BedrockNbt.asMap(e);
                    if (em == null) continue;
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(Translations.enchant(em.get("id")))
                            .append(" ").append(Translations.num(em.get("lvl"))).append(" 级");
                }
            }
            Map<String, Object> display = BedrockNbt.asMap(tag.get("display"));
            if (display != null) {
                String custom = BedrockNbt.str(display.get("Name"));
                if (!custom.isEmpty()) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append("名称：").append(custom);
                }
                List<Object> lore = BedrockNbt.asList(display.get("Lore"));
                if (lore != null && !lore.isEmpty()) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append("描述：").append(join(lore));
                }
            }
        }
        Object damage = im.get("Damage");
        if (damage != null && d(damage) > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("耐久损耗 ").append(Translations.num(damage));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void fillEffects(List<Row> out, List<Object> effects) {
        if (effects == null) return;
        for (Object e : effects) {
            Map<String, Object> em = BedrockNbt.asMap(e);
            if (em == null) continue;
            Object id = first(em, "Id", "EffectId", "id");
            Object amp = first(em, "Amplifier", "amplifier", "Level");
            Object dur = first(em, "Duration", "DurationTicks", "duration");
            StringBuilder sb = new StringBuilder();
            if (amp != null) sb.append("等级 ").append(Translations.num(amp));
            if (dur != null) {
                double ticks = d(dur);
                if (sb.length() > 0) sb.append(" · ");
                sb.append("剩余 ").append(Translations.num(dur)).append(" 刻");
                if (ticks > 0) sb.append("（约 ").append(Math.round(ticks / 20.0)).append(" 秒）");
            }
            out.add(new Row(Translations.effect(id), sb.toString()));
        }
    }

    private static void fillGroups(Record s, Map<String, Object> m) {
        for (String key : m.keySet()) {
            Object v = m.get(key);
            s.raw.add(key + " = " + rawValue(v));
            if (CONSUMED.contains(key)) continue;
            String zh = Translations.field(key);
            if (zh == null) {
                s.unknown.add(key + " = " + rawValue(v));
                continue;
            }
            if ("Attributes".equals(key)) {
                addAttributes(s, BedrockNbt.asList(v));
                continue;
            }
            if ("abilities".equals(key)) {
                addAbilities(s, BedrockNbt.asMap(v));
                continue;
            }
            String group = groupOf(key);
            List<Row> rows = s.groups.get(group);
            if (rows == null) {
                rows = new ArrayList<>();
                s.groups.put(group, rows);
            }
            rows.add(new Row(zh, fieldValue(key, v)));
        }
    }

    private static String groupOf(String key) {
        if (STATE_FIELDS.contains(key)) return "状态";
        if (SPAWN_DEATH.contains(key)) return "出生与死亡";
        if (UI_FIELDS.contains(key)) return "界面状态";
        return "其他";
    }

    private static void addAttributes(Record s, List<Object> list) {
        if (list == null) return;
        List<Row> rows = s.groups.get("属性");
        if (rows == null) {
            rows = new ArrayList<>();
            s.groups.put("属性", rows);
        }
        for (Object o : list) {
            Map<String, Object> am = BedrockNbt.asMap(o);
            if (am == null) continue;
            String name = Translations.attr(BedrockNbt.str(am.get("Name")));
            Object base = first(am, "Current", "Base", "Value");
            Object min = first(am, "Min", "DefaultMin");
            Object max = first(am, "Max", "DefaultMax");
            Object def = am.get("Default");
            String value = Translations.num(base == null ? "" : base);
            String sub = null;
            if (min != null && max != null) {
                sub = "范围 " + rangeText(min) + " ~ " + rangeText(max);
            } else if (def != null) {
                sub = "默认 " + Translations.num(def);
            }
            Object mods = am.get("Modifiers");
            List<Object> modList = BedrockNbt.asList(mods);
            if (modList != null && !modList.isEmpty()) {
                sub = (sub == null ? "" : sub + " · ") + "修饰 " + modList.size() + " 项";
            }
            rows.add(new Row(name, value, sub));
        }
    }

    private static void addAbilities(Record s, Map<String, Object> map) {
        if (map == null) return;
        List<Row> rows = s.groups.get("能力");
        if (rows == null) {
            rows = new ArrayList<>();
            s.groups.put("能力", rows);
        }
        for (String k : map.keySet()) {
            String zh = Translations.field("abilities." + k);
            if (zh == null) continue;
            Object v = map.get(k);
            String value;
            if (v instanceof Number && k.endsWith("Speed")) {
                value = Translations.num(v);
            } else {
                value = Translations.yesNo(v);
            }
            rows.add(new Row(zh, value));
        }
    }

    private static String rangeText(Object v) {
        double d = OfflinePlayerData.d(v);
        if (d > 1e30) return "∞";
        if (d < -1e30) return "-∞";
        return Translations.num(v);
    }

    private static String fieldValue(String key, Object v) {
        if (BOOL_FIELDS.contains(key)) return Translations.yesNo(v);
        if (key.endsWith("Dimension")) return Translations.dim(v);
        if ("permissionsLevel".equals(key) || "playerPermissionsLevel".equals(key)) {
            return Translations.permission(v);
        }
        if ("identifier".equals(key)) {
            String id = BedrockNbt.str(v);
            return "minecraft:player".equals(id) ? "玩家" : id;
        }
        if (v instanceof List) return listSummary(key, BedrockNbt.asList(v));
        if (v instanceof Map) return mapSummary(key, BedrockNbt.asMap(v));
        return Translations.num(v);
    }

    private static String listSummary(String key, List<Object> list) {
        if (list == null) return "";
        if (list.isEmpty()) return "无";
        switch (key) {
            case "Tags":
            case "SlotDropChances":
            case "Pos":
            case "Rotation":
                return join(list);
            case "definitions":
            case "fogCommandStack":
                return list.size() + " 项";
            default:
                return list.size() + " 项";
        }
    }

    private static String mapSummary(String key, Map<String, Object> map) {
        if (map == null) return "空";
        if ("recipe_unlocking".equals(key)) {
            List<Object> unlocked = BedrockNbt.asList(map.get("unlocked_recipes"));
            return (unlocked == null ? 0 : unlocked.size()) + " 条已解锁";
        }
        if ("internalComponents".equals(key)) return map.size() + " 项";
        return map.size() + " 项";
    }

    private static String rawValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Map) return "复合数据（" + ((Map<?, ?>) v).size() + " 项）";
        if (v instanceof List) return "列表（" + ((List<?>) v).size() + " 项）";
        return String.valueOf(v);
    }

    private static String join(List<Object> list) {
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (sb.length() > 0) sb.append("，");
            sb.append(o);
        }
        return sb.toString();
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private static double d(Object o) {
        Double v = BedrockNbt.num(o);
        return v == null ? 0 : v;
    }
}
