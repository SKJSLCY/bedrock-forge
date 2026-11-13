package com.skjslcy.bedrockforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** AI 可调用的只读工具与需确认的操作。 */
final class AiTools {

    private AiTools() {
    }

    static List<AiHttp.ToolDef> defs() {
        List<AiHttp.ToolDef> out = new ArrayList<>();
        out.add(new AiHttp.ToolDef("server_status",
                "读取服务端运行状态、版本、世界、在线玩家数",
                "{\"type\":\"object\",\"properties\":{}}"));
        out.add(new AiHttp.ToolDef("read_logs",
                "读取服务端最新日志",
                "{\"type\":\"object\",\"properties\":{\"lines\":{\"type\":\"integer\","
                        + "\"minimum\":1,\"maximum\":200}}}"));
        out.add(new AiHttp.ToolDef("list_players",
                "列出已记录的玩家及在线状态",
                "{\"type\":\"object\",\"properties\":{}}"));
        out.add(new AiHttp.ToolDef("start_server",
                "启动服务端（需要用户确认）",
                "{\"type\":\"object\",\"properties\":{}}"));
        out.add(new AiHttp.ToolDef("stop_server",
                "停止服务端（需要用户确认）",
                "{\"type\":\"object\",\"properties\":{}}"));
        out.add(new AiHttp.ToolDef("send_command",
                "向服务端控制台发送命令（需要用户确认）",
                "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},"
                        + "\"required\":[\"command\"]}"));
        return out;
    }

    static boolean needsConfirm(String name) {
        return "start_server".equals(name) || "stop_server".equals(name)
                || "send_command".equals(name);
    }

    static String describe(String name, String args) {
        if ("start_server".equals(name)) return "启动服务端";
        if ("stop_server".equals(name)) return "停止服务端";
        if ("send_command".equals(name)) {
            try {
                return "执行命令：" + new JSONObject(args).optString("command");
            } catch (Throwable ignored) {
                return "执行命令";
            }
        }
        return name;
    }

    /** 执行只读工具；start/stop 由界面层执行。 */
    static String execute(Context ctx, String name, String args) {
        try {
            if ("server_status".equals(name)) {
                StringBuilder sb = new StringBuilder();
                boolean running = ServerManager.get().isRunning();
                sb.append("运行状态：").append(running ? "运行中" : "已停止").append("\n");
                try {
                    sb.append("服务端版本：")
                            .append(DeployManager.detect(ctx, DeployManager.TYPE_SERVER).version)
                            .append("\n");
                } catch (Throwable ignored) {
                }
                sb.append("世界：").append(currentWorld()).append("\n");
                sb.append("端口：").append(ServerManager.get().getPort()).append("\n");
                PlayerTracker.ensureLoaded();
                int online = 0;
                List<PlayerTracker.Player> players = PlayerTracker.list();
                for (PlayerTracker.Player p : players) if (p.online) online++;
                sb.append("记录玩家：").append(players.size())
                        .append("，在线：").append(online);
                return sb.toString();
            }
            if ("read_logs".equals(name)) {
                int lines = 60;
                try {
                    lines = new JSONObject(args).optInt("lines", 60);
                } catch (Throwable ignored) {
                }
                lines = Math.max(1, Math.min(200, lines));
                String text = ShizukuShell.read(DeployManager.SERVER_DIR + "/Dedicated_Server.txt");
                String[] all = text.split("\n");
                StringBuilder sb = new StringBuilder();
                int start = Math.max(0, all.length - lines);
                for (int i = start; i < all.length; i++) {
                    sb.append(all[i]).append('\n');
                }
                return sb.length() == 0 ? "（日志为空）" : sb.toString();
            }
            if ("list_players".equals(name)) {
                PlayerTracker.ensureLoaded();
                List<PlayerTracker.Player> players = PlayerTracker.list();
                if (players.isEmpty()) return "还没有玩家记录。";
                JSONArray arr = new JSONArray();
                for (PlayerTracker.Player p : players) {
                    JSONObject o = new JSONObject();
                    o.put("name", p.name);
                    o.put("online", p.online);
                    o.put("xuid", p.xuid == null ? "" : p.xuid);
                    arr.put(o);
                }
                return arr.toString(2);
            }
            if ("send_command".equals(name)) {
                if (!ServerManager.get().isRunning()) return "服务端未运行，无法发送命令";
                String cmd = new JSONObject(args).optString("command");
                if (cmd.trim().isEmpty()) return "命令为空";
                boolean ok = ServerManager.get().send(cmd.trim());
                return ok ? "命令已发送：" + cmd : "命令发送失败";
            }
            return "未知工具：" + name;
        } catch (Throwable t) {
            return "工具执行失败：" + (t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static String currentWorld() {
        try {
            String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
            for (String line : props.split("\n")) {
                String t = line.trim();
                if (t.startsWith("level-name=")) {
                    String v = t.substring("level-name=".length()).trim();
                    if (!v.isEmpty()) return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return "未知";
    }
}
