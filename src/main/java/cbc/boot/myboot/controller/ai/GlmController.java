package cbc.boot.myboot.controller.ai;

import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ChatMessage;
import com.zhipu.oapi.service.v4.model.ChatMessageRole;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/model/ai")
public class GlmController {
    //测试地址：http://127.0.0.1:8087/model/ai/chat?message=%E4%BD%A0%E5%A5%BD
    // 从配置文件中读取API_KEY，如果没有配置则采用默认值 "your_api_key_here"
    @Value("${zhipu.api-key:your_api_key_here}")
    private String apiKey;

    private ClientV4 client;

    @PostConstruct
    public void init() {
        this.client = new ClientV4.Builder(apiKey).build();
    }

    /**
     * GLM 大模型对话测试接口
     *
     * @param message 用户输入的聊天内容
     * @return 模型的回复文本
     */
    @GetMapping("/chat")
    public String chatWithGlm(@RequestParam String message) {
        System.out.print("进入大模型对话聊天");
        if ("your_api_key_here".equals(apiKey)) {
            return "请在配置中设置 zhipu.api-key: [你的真实API-KEY]，并重启应用！";
        }

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), message);
        messages.add(chatMessage);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("glm-5") // 若智谱上线了 glm-5 的正式版别名，可以在此处替换成如 "glm-5"
                .stream(false)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();

        ModelApiResponse response = client.invokeModelApi(request);

        if (response != null && response.getData() != null && response.getData().getChoices() != null
                && !response.getData().getChoices().isEmpty()) {
            return response.getData().getChoices().get(0).getMessage().getContent().toString();
        } else {
            return "模型调用失败：" + (response != null && response.getMsg() != null ? response.getMsg() : "未知异常");
        }
    }
}
