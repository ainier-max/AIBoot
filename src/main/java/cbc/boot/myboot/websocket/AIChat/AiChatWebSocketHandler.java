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

        // 定义 Function Tool: query_layer_count
        List<ChatTool> tools = new ArrayList<>();
        tools.add(buildQueryLayerCountTool());

        // 第一次请求（非流式），检测是否需要 Function Calling
        ChatCompletionCreateParams firstRequest = ChatCompletionCreateParams.builder()
                .model("glm-4-flash")
                .messages(messages)
                .tools(tools)
                .toolChoice("auto")
                .stream(false)
                .build();

        ChatCompletionResponse firstResponse = client.chat().createChatCompletion(firstRequest);

        if (!firstResponse.isSuccess() || firstResponse.getData() == null) {
            sendMsg(session, "Error: " + firstResponse.getMsg());
            sendMsg(session, "[DONE]");
            return;
        }

        ModelData firstData = firstResponse.getData();
        Choice firstChoice = firstData.getChoices().get(0);
        ChatMessage assistantMsg = firstChoice.getMessage();

        // 判断是否触发了 Function Calling
        if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
            ToolCalls toolCall = (ToolCalls) assistantMsg.getToolCalls().get(0);
            String toolName = toolCall.getFunction().getName();
            String toolArgs = toolCall.getFunction().getArguments();
            String toolCallId = toolCall.getId();

            System.out.println("Function Calling 触发: " + toolName + ", 参数: " + toolArgs);

            // 执行本地函数
            String toolResult = executeTool(toolName, toolArgs);
            System.out.println("Function Calling 结果: " + toolResult);

            // 追加 assistant 消息和 tool 结果
            messages.add(assistantMsg);
            messages.add(ChatMessage.builder()
                    .role("tool")
                    .content(toolResult)
                    .toolCallId(toolCallId)
                    .build());

            // 第二次请求（流式），让模型根据工具结果组织回复
            ChatCompletionCreateParams secondRequest = ChatCompletionCreateParams.builder()
                    .model("glm-4-flash")
                    .messages(messages)
                    .stream(true)
                    .build();

            ChatCompletionResponse secondResponse = client.chat().createChatCompletion(secondRequest);
            streamResponse(session, secondResponse);

        } else {
            // 没有触发 Function Calling，直接流式输出
            // 重新发起流式请求
            ChatCompletionCreateParams streamRequest = ChatCompletionCreateParams.builder()
                    .model("glm-4-flash")
                    .messages(messages)
                    .stream(true)
                    .build();

            ChatCompletionResponse streamResponse = client.chat().createChatCompletion(streamRequest);
            streamResponse(session, streamResponse);
        }
    }

    /**
     * 构建 query_layer_count 工具定义
     */
    private ChatTool buildQueryLayerCountTool() {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> layerNameProp = new HashMap<>();
        layerNameProp.put("type", "string");
        layerNameProp.put("description", "图层名称，例如：网吧、加油站、学校");
        properties.put("layerName", layerNameProp);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("layerName"));

        return ChatTool.builder()
                .type("function")
                .function(ChatFunction.builder()
                        .name("query_layer_count")
                        .description("查询指定图层（如网吧、加油站）的数据总条数")
                        .parameters(parameters)
                        .build())
                .build();
    }

    /**
     * 根据工具名称执行对应本地函数
     */
    private String executeTool(String toolName, String toolArgs) {
        if ("query_layer_count".equals(toolName)) {
            try {
                JSONObject args = JSON.parseObject(toolArgs);
                String layerName = args.getString("layerName");

                Map<String, Object> param = new HashMap<>();
                param.put("layerName", layerName);
                List<String> sqls = new ArrayList<>();
                sqls.add("ai_chat.getTableNameByLayerName");
                sqls.add("ai_chat.getTableCount");
                param.put("sqls", sqls);

                Object result = combineSqlUtil.executeCombineSql(param);
                return "查询结果：" + JSON.toJSONString(result);
            } catch (Exception e) {
                return "查询失败：" + e.getMessage();
            }
        }
        return "未知工具：" + toolName;
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
