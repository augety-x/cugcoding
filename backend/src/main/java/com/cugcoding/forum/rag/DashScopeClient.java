package com.cugcoding.forum.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class DashScopeClient {
    private static final Logger log = LoggerFactory.getLogger(DashScopeClient.class);
    private static final String API_KEY = "sk-73da8169200e48d2977e8ef4156d3c1f";
    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    private static final String CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashScopeClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /** Rerank documents against a query. Returns indices sorted by relevance (best first). */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "qwen3-vl-rerank");
            ObjectNode input = body.putObject("input");
            input.put("query", query);
            ArrayNode docs = input.putArray("documents");
            for (String doc : documents) docs.add(doc);
            ObjectNode params = body.putObject("parameters");
            params.put("return_documents", false);
            params.put("top_n", Math.min(topN, documents.size()));

            Request request = new Request.Builder()
                    .url(RERANK_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Rerank failed: {} {}", response.code(), response.body() != null ? response.body().string() : "");
                    return defaultRank(documents.size(), topN);
                }
                JsonNode json = mapper.readTree(response.body().string());
                JsonNode results = json.get("output").get("results");
                List<Integer> indices = new ArrayList<>();
                for (JsonNode r : results) {
                    indices.add(r.get("index").asInt());
                    if (indices.size() >= topN) break;
                }
                return indices;
            }
        } catch (Exception e) {
            log.error("Rerank error: {}", e.getMessage());
            return defaultRank(documents.size(), topN);
        }
    }

    /** Stream chat completion via SSE, calling onChunk for each text delta. */
    public String chatStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "qwen-plus");
            body.put("stream", true);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userMessage);

            Request request = new Request.Builder()
                    .url(CHAT_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Chat failed: {} {}", response.code(), err);
                    onChunk.accept("AI服务暂时不可用: " + response.code());
                    return "";
                }
                StringBuilder full = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) break;
                        try {
                            JsonNode node = mapper.readTree(data);
                            JsonNode choices = node.get("choices");
                            if (choices != null && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null && delta.has("content")) {
                                    String content = delta.get("content").asText();
                                    full.append(content);
                                    onChunk.accept(content);
                                }
                            }
                        } catch (Exception ignored) { }
                    }
                }
                return full.toString();
            }
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage());
            onChunk.accept("系统错误: " + e.getMessage());
            return "";
        }
    }

    private List<Integer> defaultRank(int total, int topN) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < Math.min(total, topN); i++) indices.add(i);
        return indices;
    }
}
