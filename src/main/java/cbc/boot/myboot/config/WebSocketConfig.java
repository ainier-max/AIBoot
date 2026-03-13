package cbc.boot.myboot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import cbc.boot.myboot.websocket.AIChat.AiChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AiChatWebSocketHandler aiChatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 将 /api/ai/chat 映射到 AiChatWebSocketHandler
        // 允许跨域（可根据实际需求调整）
        registry.addHandler(aiChatWebSocketHandler, "/api/ai/chat")
                .setAllowedOrigins("*");
    }
}
