package cbc.boot.myboot.websocket;

import com.alibaba.fastjson.JSON;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天 WebSocket 处理器，实现流式返回数据
 */
@Component
public class AiChatWebSocketHandler extends TextWebSocketHandler {

    @Value("${zhipu.api-key:your_api_key_here}")
    private String apiKey;

    private ClientV4 client;

    @PostConstruct
    public void init() {
        this.client = new ClientV4.Builder(apiKey).build();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("handleTextMessage--大模型开发交流消息");
        String userContent = message.getPayload();

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userContent));

        // 构建请求，设置 stream 为 true 开启流式输出
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("glm-4")
                .stream(Boolean.TRUE)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();

        // 调用流式接口，获取 ModelApiResponse
        ModelApiResponse response = client.invokeModelApi(request);

        // response.getFlowable() 返回 Flowable<ModelData>，逐块推送给前端
        if (response != null && response.getFlowable() != null) {
            response.getFlowable()
                    .doOnNext(modelData -> {
                        // 每次收到一个数据块时，从 choices -> delta -> content 中取文本推送
                        if (modelData.getChoices() != null && !modelData.getChoices().isEmpty()) {
                            Choice choice = modelData.getChoices().get(0);
                            if (choice.getDelta() != null) {
                                Object content = choice.getDelta().getContent();
                                if (content != null) {
                                    String text = content.toString();
                                    sendMessageToClient(session, text);
                                }
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 流结束，发送结束信号给前端
                        sendMessageToClient(session, "[DONE]");
                    })
                    .doOnError(e -> {
                        sendMessageToClient(session, "Error: " + e.getMessage());
                    })
                    .blockingSubscribe(); // 阻塞直到全部推送完毕（在当前线程执行）
        } else {
            sendMessageToClient(session, "Error: 未获取到有效的流式响应");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
    }

    private void sendMessageToClient(WebSocketSession session, String text) {
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
