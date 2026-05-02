package com.cugcoding.forum.rag;

import com.cugcoding.forum.auth.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RagWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RagWebSocketHandler.class);
    private final RagService ragService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public RagWebSocketHandler(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        Long userId = JwtUtil.getUserId(token);
        if (userId == null) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException e) { }
            return;
        }
        sessions.put(session.getId(), session);
        log.info("RAG WS connected: session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            String query = node.has("query") ? node.get("query").asText() : "";
            JsonNode idsNode = node.get("articleIds");
            List<Long> articleIds = new ArrayList<>();
            if (idsNode != null && idsNode.isArray()) {
                for (JsonNode id : idsNode) articleIds.add(id.asLong());
            }

            if (query.isEmpty()) {
                sendJson(session, "{\"error\":\"问题不能为空\"}");
                return;
            }

            ragService.chat(query, articleIds.isEmpty() ? null : articleIds, chunk -> {
                try {
                    sendJson(session, "{\"chunk\":\"" + escapeJson(chunk) + "\"}");
                } catch (IOException e) {
                    log.error("WS send error", e);
                }
            });
            sendJson(session, "{\"type\":\"completion\",\"status\":\"finished\"}");
        } catch (Exception e) {
            log.error("RAG WS error", e);
            sendJson(session, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    private void sendJson(WebSocketSession session, String json) throws IOException {
        if (session.isOpen()) session.sendMessage(new TextMessage(json));
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.startsWith("token=")) {
            return query.substring(6);
        }
        // Also try from cookies
        List<String> cookies = session.getHandshakeHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                for (String pair : cookie.split(";")) {
                    pair = pair.trim();
                    if (pair.startsWith("f-session=")) {
                        return pair.substring(10);
                    }
                }
            }
        }
        return null;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
