package com.skjslcy.bedrockforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 非流式 AI 请求：OpenAI 兼容、Gemini、Anthropic。 */
final class AiHttp {

    static final class ToolCall {
        String id = "";
        String name = "";
        String args = "{}";
    }

    static final class Msg {
        String role;          // user / assistant / tool
        String text = "";
        List<String> images = new ArrayList<>();   // base64
        String toolCallId = "";
        List<ToolCall> calls = new ArrayList<>();
    }

    static final class ToolDef {
        final String name;
        final String desc;
        final String schema; // JSON schema object string

        ToolDef(String name, String desc, String schema) {
            this.name = name;
            this.desc = desc;
            this.schema = schema;
        }
    }

    static final class Result {
        boolean ok;
        String error = "";
        String text = "";
        List<ToolCall> calls = new ArrayList<>();
    }

    private AiHttp() {
    }

    static Result chat(Context ctx, String system, List<Msg> hist, List<ToolDef> tools,
                       String reasoning) {
        try {
            String pid = AiConfig.providerId(ctx);
            if (pid.isEmpty()) return err(I18n.t("未配置服务商"));
            AiConfig.Provider p = AiConfig.provider(pid);
            String key = AiConfig.key(ctx, pid);
            if (key.isEmpty()) return err(I18n.t("未填写 API Key"));
            String base = AiConfig.baseUrl(ctx, pid);
            if (base.isEmpty()) return err(I18n.t("未填写 Base URL"));
            String model = AiConfig.model(ctx);
            if (model.isEmpty()) return err(I18n.t("未选择模型"));
            if ("gemini".equals(p.style)) return gemini(base, key, model, system, hist, tools, reasoning);
            if ("anthropic".equals(p.style)) return anthropic(base, key, model, system, hist, tools, reasoning);
            return openai(ctx, pid, base, key, model, system, hist, tools, reasoning);
        } catch (Throwable t) {
            return err(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static Result err(String s) {
        Result r = new Result();
        r.error = s;
        return r;
    }

    static final class ModelList {
        boolean ok;
        String error = "";
        List<String> models = new ArrayList<>();
    }

    static ModelList listModels(Context ctx, String pid) {
        ModelList out = new ModelList();
        try {
            if (pid == null || pid.isEmpty()) {
                out.error = I18n.t("未配置服务商");
                return out;
            }
            AiConfig.Provider p = AiConfig.provider(pid);
            String key = AiConfig.key(ctx, pid);
            if (key.isEmpty()) {
                out.error = I18n.t("未填写 API Key");
                return out;
            }
            String base = AiConfig.baseUrl(ctx, pid);
            if (base.isEmpty()) {
                out.error = I18n.t("未填写 Base URL");
                return out;
            }
            JSONObject resp;
            if ("gemini".equals(p.style)) {
                resp = get(base + "/models?key=" + key, null, null, null);
                JSONArray arr = resp.optJSONArray("models");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        String name = m.optString("name", "");
                        if (name.startsWith("models/")) name = name.substring(7);
                        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                        boolean ok = methods == null;
                        if (methods != null) {
                            for (int j = 0; j < methods.length(); j++) {
                                if ("generateContent".equals(methods.optString(j))) ok = true;
                            }
                        }
                        if (ok && !name.isEmpty()) out.models.add(name);
                    }
                }
            } else if ("anthropic".equals(p.style)) {
                resp = get(base + "/models", key, "x-api-key", key,
                        new String[]{"anthropic-version", "2023-06-01"});
                JSONArray arr = resp.optJSONArray("data");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.getJSONObject(i).optString("id", "");
                        if (!id.isEmpty()) out.models.add(id);
                    }
                }
            } else {
                resp = get(base + "/models", key, "Authorization", "Bearer " + key, null);
                JSONArray arr = resp.optJSONArray("data");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.getJSONObject(i).optString("id", "");
                        if (!id.isEmpty()) out.models.add(id);
                    }
                }
            }
            if (out.models.isEmpty()) out.error = I18n.t("接口未返回可用模型");
            else out.ok = true;
        } catch (Throwable t) {
            out.error = t.getMessage() == null ? t.toString() : t.getMessage();
        }
        return out;
    }

    private static JSONObject get(String url, String header, String headerValue,
                                  String[] extraHeader) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        if (header != null && headerValue != null) c.setRequestProperty(header, headerValue);
        if (extraHeader != null && extraHeader.length >= 2) {
            c.setRequestProperty(extraHeader[0], extraHeader[1]);
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);
        if (code >= 400) throw new Exception("HTTP " + code + "：" + trim(text));
        return new JSONObject(text);
    }

    private static JSONObject get(String url, String key, String header, String headerValue,
                                  String[] extraHeader) throws Exception {
        return get(url, header, headerValue, extraHeader);
    }

    // ------------------------------------------------------------ openai

    private static Result openai(Context ctx, String pid, String base, String key, String model,
                                 String system, List<Msg> hist, List<ToolDef> tools,
                                 String reasoning) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        JSONArray msgs = new JSONArray();
        if (system != null && !system.isEmpty()) {
            msgs.put(new JSONObject().put("role", "system").put("content", system));
        }
        for (Msg m : hist) {
            JSONObject o = new JSONObject();
            if ("tool".equals(m.role)) {
                o.put("role", "tool").put("tool_call_id", m.toolCallId).put("content", m.text);
            } else if (!m.calls.isEmpty()) {
                o.put("role", "assistant");
                JSONArray arr = new JSONArray();
                for (ToolCall c : m.calls) {
                    arr.put(new JSONObject().put("id", c.id).put("type", "function")
                            .put("function", new JSONObject().put("name", c.name)
                                    .put("arguments", c.args)));
                }
                o.put("tool_calls", arr);
                o.put("content", m.text.isEmpty() ? JSONObject.NULL : m.text);
            } else if (!m.images.isEmpty()) {
                o.put("role", "user");
                JSONArray parts = new JSONArray();
                if (!m.text.isEmpty()) {
                    parts.put(new JSONObject().put("type", "text").put("text", m.text));
                }
                for (String b64 : m.images) {
                    parts.put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:image/png;base64," + b64)));
                }
                o.put("content", parts);
            } else {
                o.put("role", "user".equals(m.role) ? "user" : "assistant");
                o.put("content", m.text);
            }
            msgs.put(o);
        }
        body.put("messages", msgs);
        if (tools != null && !tools.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (ToolDef t : tools) {
                arr.put(new JSONObject().put("type", "function").put("function",
                        new JSONObject().put("name", t.name).put("description", t.desc)
                                .put("parameters", new JSONObject(t.schema))));
            }
            body.put("tools", arr);
        }
        applyReasoning(body, pid, reasoning);
        JSONObject resp = post(base + "/chat/completions", key, "Authorization",
                "Bearer " + key, body, null);
        Result r = new Result();
        JSONObject msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        r.text = msg.optString("content", "");
        JSONArray calls = msg.optJSONArray("tool_calls");
        if (calls != null) {
            for (int i = 0; i < calls.length(); i++) {
                JSONObject c = calls.getJSONObject(i);
                ToolCall tc = new ToolCall();
                tc.id = c.optString("id");
                tc.name = c.getJSONObject("function").optString("name");
                tc.args = c.getJSONObject("function").optString("arguments", "{}");
                r.calls.add(tc);
            }
        }
        r.ok = true;
        return r;
    }

    private static void applyReasoning(JSONObject body, String pid, String reasoning)
            throws Exception {
        if ("off".equals(reasoning)) return;
        String effort = "medium".equals(reasoning) ? "medium" : ("low".equals(reasoning) ? "low" : "high");
        int budget = "low".equals(reasoning) ? 1024 : ("medium".equals(reasoning) ? 4096 : 8192);
        if ("openai".equals(pid)) {
            body.put("reasoning_effort", effort);
        } else if ("openrouter".equals(pid)) {
            body.put("reasoning", new JSONObject().put("effort", effort));
        } else if ("dashscope".equals(pid)) {
            body.put("enable_thinking", true);
            body.put("thinking_budget", budget);
        } else if ("zhipu".equals(pid)) {
            body.put("thinking", new JSONObject().put("type", "enabled"));
        } else if ("siliconflow".equals(pid)) {
            body.put("enable_thinking", true);
        }
    }

    // ------------------------------------------------------------ gemini

    private static Result gemini(String base, String key, String model, String system,
                                 List<Msg> hist, List<ToolDef> tools, String reasoning)
            throws Exception {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        for (Msg m : hist) {
            JSONObject c = new JSONObject();
            c.put("role", "assistant".equals(m.role) ? "model" : "user");
            JSONArray parts = new JSONArray();
            if (!m.text.isEmpty()) parts.put(new JSONObject().put("text", m.text));
            for (String b64 : m.images) {
                parts.put(new JSONObject().put("inline_data", new JSONObject()
                        .put("mime_type", "image/png").put("data", b64)));
            }
            if ("tool".equals(m.role)) {
                c.put("role", "user");
                parts = new JSONArray();
                parts.put(new JSONObject().put("functionResponse", new JSONObject()
                        .put("name", m.toolCallId).put("response",
                                new JSONObject().put("result", m.text))));
            }
            if (!m.calls.isEmpty()) {
                parts = new JSONArray();
                for (ToolCall tc : m.calls) {
                    parts.put(new JSONObject().put("functionCall", new JSONObject()
                            .put("name", tc.name).put("args", new JSONObject(tc.args))));
                }
            }
            c.put("parts", parts);
            contents.put(c);
        }
        body.put("contents", contents);
        if (system != null && !system.isEmpty()) {
            body.put("systemInstruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", system))));
        }
        if (tools != null && !tools.isEmpty()) {
            JSONArray decls = new JSONArray();
            for (ToolDef t : tools) {
                decls.put(new JSONObject().put("name", t.name).put("description", t.desc)
                        .put("parameters", new JSONObject(t.schema)));
            }
            body.put("tools", new JSONArray().put(new JSONObject()
                    .put("functionDeclarations", decls)));
        }
        JSONObject gen = new JSONObject();
        if (!"off".equals(reasoning)) {
            int budget = "low".equals(reasoning) ? 1024 : ("medium".equals(reasoning) ? 4096 : 8192);
            gen.put("thinkingConfig", new JSONObject().put("thinkingBudget", budget));
        }
        body.put("generationConfig", gen);
        String url = base + "/models/" + model + ":generateContent?key=" + key;
        JSONObject resp = post(url, null, null, null, body, null);
        Result r = new Result();
        JSONArray cands = resp.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) return err(I18n.t("Gemini 返回空结果"));
        JSONArray parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("text")) text.append(part.optString("text"));
            if (part.has("functionCall")) {
                JSONObject fc = part.getJSONObject("functionCall");
                ToolCall tc = new ToolCall();
                tc.id = fc.optString("name");
                tc.name = fc.optString("name");
                tc.args = fc.optJSONObject("args") == null ? "{}" : fc.optJSONObject("args").toString();
                r.calls.add(tc);
            }
        }
        r.text = text.toString();
        r.ok = true;
        return r;
    }

    // --------------------------------------------------------- anthropic

    private static Result anthropic(String base, String key, String model, String system,
                                    List<Msg> hist, List<ToolDef> tools, String reasoning)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        int maxTokens = "off".equals(reasoning) ? 4096 : 8192;
        body.put("max_tokens", maxTokens);
        if (system != null && !system.isEmpty()) body.put("system", system);
        JSONArray msgs = new JSONArray();
        for (Msg m : hist) {
            JSONObject o = new JSONObject();
            if ("tool".equals(m.role)) {
                o.put("role", "user");
                o.put("content", new JSONArray().put(new JSONObject().put("type", "tool_result")
                        .put("tool_use_id", m.toolCallId).put("content", m.text)));
            } else if (!m.calls.isEmpty()) {
                o.put("role", "assistant");
                JSONArray content = new JSONArray();
                if (!m.text.isEmpty()) {
                    content.put(new JSONObject().put("type", "text").put("text", m.text));
                }
                for (ToolCall c : m.calls) {
                    content.put(new JSONObject().put("type", "tool_use").put("id", c.id)
                            .put("name", c.name).put("input", new JSONObject(c.args)));
                }
                o.put("content", content);
            } else {
                o.put("role", "user".equals(m.role) ? "user" : "assistant");
                JSONArray content = new JSONArray();
                for (String b64 : m.images) {
                    content.put(new JSONObject().put("type", "image").put("source",
                            new JSONObject().put("type", "base64").put("media_type", "image/png")
                                    .put("data", b64)));
                }
                if (!m.text.isEmpty()) {
                    content.put(new JSONObject().put("type", "text").put("text", m.text));
                }
                o.put("content", content);
            }
            msgs.put(o);
        }
        body.put("messages", msgs);
        if (tools != null && !tools.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (ToolDef t : tools) {
                arr.put(new JSONObject().put("name", t.name).put("description", t.desc)
                        .put("input_schema", new JSONObject(t.schema)));
            }
            body.put("tools", arr);
        }
        if (!"off".equals(reasoning)) {
            int budget = "low".equals(reasoning) ? 1024 : ("medium".equals(reasoning) ? 4096 : 8192);
            if (maxTokens <= budget) body.put("max_tokens", budget + 1024);
            body.put("thinking", new JSONObject().put("type", "enabled")
                    .put("budget_tokens", budget));
        }
        JSONObject resp = post(base + "/messages", key, "x-api-key", key, body,
                new String[]{"anthropic-version", "2023-06-01"});
        Result r = new Result();
        JSONArray content = resp.optJSONArray("content");
        StringBuilder text = new StringBuilder();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.getJSONObject(i);
                if ("text".equals(part.optString("type"))) text.append(part.optString("text"));
                if ("tool_use".equals(part.optString("type"))) {
                    ToolCall tc = new ToolCall();
                    tc.id = part.optString("id");
                    tc.name = part.optString("name");
                    tc.args = part.optJSONObject("input") == null
                            ? "{}" : part.optJSONObject("input").toString();
                    r.calls.add(tc);
                }
            }
        }
        r.text = text.toString();
        r.ok = true;
        return r;
    }

    // ------------------------------------------------------------- http

    private static JSONObject post(String url, String key, String header, String headerValue,
                                   JSONObject body, String[] extraHeader) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        if (header != null && headerValue != null) c.setRequestProperty(header, headerValue);
        if (extraHeader != null && extraHeader.length >= 2) {
            c.setRequestProperty(extraHeader[0], extraHeader[1]);
        }
        OutputStream out = c.getOutputStream();
        out.write(body.toString().getBytes("UTF-8"));
        out.close();
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(in);
        if (code >= 400) throw new Exception("HTTP " + code + "：" + trim(text));
        return new JSONObject(text);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private static String trim(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) : s);
    }
}
