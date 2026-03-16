package cbc.boot.myboot.websocket.AIChat;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.*;
import cbc.boot.myboot.controller.db.util.CombineSqlUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * AI 聊天 WebSocket 处理器，支持流式返回 + Function Calling
 * 使用新版 SDK: ai.z.openapi:zai-sdk:0.3.3
 */
@Component
public class AiChatWebSocketHandler extends TextWebSocketHandler {

    @Value("${zhipu.api-key:your_api_key_here}")
    private String apiKey;

    @Autowired
    private CombineSqlUtil combineSqlUtil;

    private ZhipuAiClient client;

    @PostConstruct
    public void init() {
        this.client = ZhipuAiClient.builder().ofZHIPU()
                .apiKey(apiKey)
                .build();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("handleTextMessage -- AI Chat WebSocket 收到消息");
        String userContent = message.getPayload();

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(userContent)
                .build());

        // 直接流式请求
        ChatCompletionCreateParams streamRequest = ChatCompletionCreateParams.builder()
                .model("glm-4-flash")
                .messages(messages)
                .stream(true)
                .build();

        ChatCompletionResponse streamResponse = client.chat().createChatCompletion(streamRequest);
        streamResponse(session, streamResponse);
    }

    /**
     * 流式推送响应给前端
     */
    private void streamResponse(WebSocketSession session, ChatCompletionResponse response) {
        if (response != null && response.isSuccess() && response.getFlowable() != null) {
            response.getFlowable().subscribe(
                    data -> {
                        if (data.getChoices() != null && !data.getChoices().isEmpty()) {
                            Delta delta = data.getChoices().get(0).getDelta();
                            if (delta != null && delta.getContent() != null) {
                                sendMsg(session, delta.getContent().toString());
                            }
                        }
                    },
                    error -> {
                        sendMsg(session, "Error: " + error.getMessage());
                        sendMsg(session, "[DONE]");
                    },
                    () -> sendMsg(session, "[DONE]")
            );
        } else {
            String errMsg = response != null ? response.getMsg() : "未获取到有效的流式响应";
            sendMsg(session, "Error: " + errMsg);
            sendMsg(session, "[DONE]");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
    }

    private void sendMsg(WebSocketSession session, String text) {
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(text));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
