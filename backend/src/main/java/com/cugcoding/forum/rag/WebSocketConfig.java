package com.cugcoding.forum.rag;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RagWebSocketHandler ragHandler;

    public WebSocketConfig(RagWebSocketHandler ragHandler) {
        this.ragHandler = ragHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ragHandler, "/ws/rag/chat").setAllowedOrigins("*");
    }
}
