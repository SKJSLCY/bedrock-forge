package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AI 服务商、模型、密钥存储（Key 用 Android Keystore 加密）。 */
final class AiConfig {

    static final class Provider {
        final String id;
        final String name;
        final String baseUrl;
        final String style; // openai / gemini / anthropic
        final String[] models;

        Provider(String id, String name, String baseUrl, String style, String... models) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.style = style;
            this.models = models;
        }
    }

    private static final Provider[] PROVIDERS = {
            new Provider("openai", "OpenAI", "https://api.openai.com/v1", "openai",
                    "gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o"),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "openai",
                    "deepseek-chat", "deepseek-reasoner"),
            new Provider("dashscope", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "openai", "qwen-max", "qwen-plus", "qwen-vl-max"),
            new Provider("moonshot", "Kimi", "https://api.moonshot.cn/v1", "openai",
                    "kimi-k2-0711-preview", "moonshot-v1-128k"),
            new Provider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "openai",
                    "glm-4.6", "glm-4.5", "glm-4v-plus"),
            new Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "openai",
                    "deepseek-ai/DeepSeek-V3.2-Exp", "Qwen/Qwen3-Max"),
            new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai",
                    "openai/gpt-5", "anthropic/claude-sonnet-4.5", "google/gemini-2.5-pro"),
            new Provider("gemini", "Google Gemini",
                    "https://generativelanguage.googleapis.com/v1beta", "gemini",
                    "gemini-2.5-pro", "gemini-2.5-flash"),
            new Provider("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1",
                    "anthropic", "claude-sonnet-4-5", "claude-opus-4-1"),
            new Provider("custom", "自定义", "", "openai", "")
    };

    private AiConfig() {
    }

    static Provider[] providers() {
        return PROVIDERS;
    }

    static Provider provider(String id) {
        for (Provider p : PROVIDERS) if (p.id.equals(id)) return p;
        return PROVIDERS[0];
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ai_config", Context.MODE_PRIVATE);
    }

    static String providerId(Context c) {
        String id = prefs(c).getString("provider", "");
        return id == null ? "" : id;
    }

    static java.util.List<String> added(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("added", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i);
                if (id != null && provider(id) != null) out.add(id);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void saveAdded(Context c, java.util.List<String> ids) {
        JSONArray arr = new JSONArray();
        for (String id : ids) arr.put(id);
        prefs(c).edit().putString("added", arr.toString()).apply();
    }

    static void addProvider(Context c, String id) {
        java.util.List<String> ids = added(c);
        if (!ids.contains(id)) ids.add(id);
        saveAdded(c, ids);
    }

    static void removeProvider(Context c, String id) {
        java.util.List<String> ids = added(c);
        ids.remove(id);
        saveAdded(c, ids);
        if (id.equals(providerId(c))) {
            prefs(c).edit().putString("provider", ids.isEmpty() ? "" : ids.get(0)).apply();
        }
        clearKey(c, id);
        prefs(c).edit().remove("model_" + id).remove("models_" + id).remove("base_" + id).apply();
    }

    static void setProvider(Context c, String id) {
        prefs(c).edit().putString("provider", id).apply();
    }

    static String model(Context c) {
        String m = prefs(c).getString("model_" + providerId(c), "");
        return m == null ? "" : m;
    }

    static void setModel(Context c, String providerId, String model) {
        prefs(c).edit().putString("model_" + providerId, model).apply();
    }

    static String baseUrl(Context c, String providerId) {
        String v = prefs(c).getString("base_" + providerId, "");
        if (v != null && !v.trim().isEmpty()) return v.trim();
        return provider(providerId).baseUrl;
    }

    static void setBaseUrl(Context c, String providerId, String url) {
        prefs(c).edit().putString("base_" + providerId, url == null ? "" : url.trim()).apply();
    }

    static java.util.List<String> detectedModels(Context c, String providerId) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("models_" + providerId, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Throwable ignored) {
        }
        return out;
    }

    static void setDetectedModels(Context c, String providerId, java.util.List<String> models) {
        JSONArray arr = new JSONArray();
        for (String m : models) arr.put(m);
        prefs(c).edit().putString("models_" + providerId, arr.toString()).apply();
    }

    static String aiName(Context c) {
        String v = prefs(c).getString("ai_name", "");
        return v == null || v.trim().isEmpty() ? "AI 助手" : v;
    }

    static void setAiName(Context c, String v) {
        prefs(c).edit().putString("ai_name", v == null ? "" : v.trim()).apply();
    }

    static String userName(Context c) {
        String v = prefs(c).getString("user_name", "");
        return v == null || v.trim().isEmpty() ? "你" : v;
    }

    static void setUserName(Context c, String v) {
        prefs(c).edit().putString("user_name", v == null ? "" : v.trim()).apply();
    }

    static int contextLimit(Context c) {
        return prefs(c).getInt("context_limit", 20);
    }

    static void setContextLimit(Context c, int n) {
        prefs(c).edit().putInt("context_limit", n).apply();
    }

    static String reasoning(Context c) {
        return prefs(c).getString("reasoning", "medium");
    }

    static void setReasoning(Context c, String v) {
        prefs(c).edit().putString("reasoning", v).apply();
    }

    static final String[][] PROMPTS = {
            {"A", "通用管理员",
                    "你是这台 Minecraft 基岩版服务端的常驻管理员。\n\n"
                    + "工作方式：\n"
                    + "1. 先查再答。涉及运行状态、玩家、日志的问题，先调用工具获取真实数据，再基于结果回答；没查到就说没查到。\n"
                    + "2. 区分只读和操作。启动、停止、执行命令属于操作，必须先说明将要做什么、影响是什么，等用户确认后再调用工具。\n"
                    + "3. 不猜测。日志里没有的错误不要编造；不确定版本差异时明确说明。\n"
                    + "4. 回答简洁。先给结论，再给依据。\n"
                    + "5. 使用中文，术语保留英文原名（如 allow-list、TPS、LevelDB）。\n\n"
                    + "操作底线：\n"
                    + "· 不主动执行破坏性命令（kill、clear、deop 全体、删除世界等）。\n"
                    + "· 用户要求执行危险命令时，先指出风险，再请求确认。\n"
                    + "· 玩家数据修改必须提示备份。\n"
                    + "· 不透露 API Key 等隐私信息。"},
            {"B", "运维排障",
                    "你是 Minecraft 基岩版服务端运维工程师，擅长从日志定位问题。\n\n"
                    + "排障流程：\n"
                    + "1. 调用 read_logs 获取最近日志（必要时增加到 200 行）。\n"
                    + "2. 调用 server_status 确认版本、世界、在线人数和运行状态。\n"
                    + "3. 按优先级查找：ERROR/FATAL > WARN > 连接失败 > 认证失败 > 性能告警。\n"
                    + "4. 输出结构：现象 / 证据（引用日志原文，最多 3 条）/ 原因（1-2 个）/ 处理步骤。\n"
                    + "5. 日志不足时列出还需要哪些信息，不给泛泛建议。\n\n"
                    + "禁止：编造日志、建议不可逆删除、把重启当作唯一方案。语气冷静直接。"},
            {"C", "玩家与存档",
                    "你是服务端的玩家与存档管理员。\n\n"
                    + "职责：\n"
                    + "· 玩家：在线状态、白名单、权限、踢出、游戏模式、等级。\n"
                    + "· 存档：世界列表、备份、回滚、导入导出。\n"
                    + "· 玩家数据：查看、编辑、删除、备份、跨世界导入。\n\n"
                    + "规则：\n"
                    + "1. 涉及玩家数据时，先说明改哪个世界、哪个玩家、哪些字段。\n"
                    + "2. 写入操作前提醒：需要停服、会自动备份、失败会回滚。\n"
                    + "3. 删除玩家数据必须二次确认，并说明只影响当前世界。\n"
                    + "4. 跨版本导入先比较版本，备份版本高于目标世界版本时阻止。\n"
                    + "5. 不替用户做清理所有玩家、重置世界这类决定。\n\n"
                    + "回答顺序：操作路径 → 风险提示 → 验证方式。"},
            {"D", "新手向导",
                    "你是 Minecraft 基岩版开服的耐心向导，面向第一次接触服务端的用户。\n\n"
                    + "原则：\n"
                    + "1. 用生活化语言解释；必须使用术语时用一句话解释。\n"
                    + "2. 一次只给一个步骤，等用户完成后再继续。\n"
                    + "3. 每步说明：点哪里、会看到什么、失败了怎么办。\n"
                    + "4. 不假设用户知道端口、白名单、在线模式、LevelDB。\n"
                    + "5. 用户说“不懂”时换一种说法。\n\n"
                    + "安全：不建议关闭在线模式、不建议无白名单对公网开放；删除覆盖前提醒备份。\n"
                    + "语气友好简短。"},
            {"E", "极简命令",
                    "你是极简模式的服务器助手。\n\n"
                    + "规则：\n"
                    + "· 能用一句回答就不写第二句。\n"
                    + "· 只读问题直接给结果，不解释过程。\n"
                    + "· 操作请求回复“将执行：<操作>，影响：<一句话>”，等待确认后再调用工具。\n"
                    + "· 不主动扩展话题。\n"
                    + "· 日志只摘关键行。\n\n"
                    + "禁止：客套话、重复用户问题、长篇总结。"}
    };

    static String presetId(Context c) {
        return prefs(c).getString("system_preset", "A");
    }

    static void setPreset(Context c, String id) {
        prefs(c).edit().putString("system_preset", id == null ? "A" : id).apply();
    }

    static String customPrompt(Context c) {
        return prefs(c).getString("system", "");
    }

    static void setCustomPrompt(Context c, String v) {
        prefs(c).edit().putString("system", v == null ? "" : v).apply();
    }

    static String presetText(String id) {
        for (String[] row : PROMPTS) {
            if (row[0].equals(id)) return row[2];
        }
        return PROMPTS[0][2];
    }

    static String presetName(String id) {
        for (String[] row : PROMPTS) {
            if (row[0].equals(id)) return row[1];
        }
        return "自定义";
    }

    static String systemPrompt(Context c) {
        String id = presetId(c);
        if ("custom".equals(id)) return customPrompt(c);
        return presetText(id);
    }

    static void setSystemPrompt(Context c, String v) {
        setCustomPrompt(c, v);
    }

    // ---------------------------------------------------------------- keys

    static boolean hasKey(Context c, String providerId) {
        String v = prefs(c).getString("key_" + providerId, "");
        return v != null && !v.isEmpty();
    }

    static void saveKey(Context c, String providerId, String key) {
        if (key == null || key.trim().isEmpty()) {
            prefs(c).edit().remove("key_" + providerId).apply();
            return;
        }
        try {
            prefs(c).edit().putString("key_" + providerId, CryptoBox.encrypt(key.trim())).apply();
        } catch (Throwable t) {
            // 加密不可用时拒绝保存，避免明文落盘
        }
    }

    static String key(Context c, String providerId) {
        String enc = prefs(c).getString("key_" + providerId, "");
        if (enc == null || enc.isEmpty()) return "";
        try {
            return CryptoBox.decrypt(enc);
        } catch (Throwable t) {
            return "";
        }
    }

    static void clearKey(Context c, String providerId) {
        prefs(c).edit().remove("key_" + providerId).apply();
    }

    // ------------------------------------------------------------- sessions

    static JSONArray sessions(Context c) {
        try {
            return new JSONArray(prefs(c).getString("sessions", "[]"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    static void saveSessions(Context c, JSONArray arr) {
        prefs(c).edit().putString("sessions", arr.toString()).apply();
    }

    static final class CryptoBox {
        private static final String ALIAS = "mcbds_ai_key";
        private static final String TRANSFORM = "AES/GCM/NoPadding";

        private static SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                kg.generateKey();
            }
            return (SecretKey) ks.getKey(ALIAS, null);
        }

        static String encrypt(String plain) throws Exception {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        }

        static String decrypt(String enc) throws Exception {
            byte[] all = Base64.decode(enc, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] pt = c.doFinal(all, 12, all.length - 12);
            return new String(pt, "UTF-8");
        }
    }
}
