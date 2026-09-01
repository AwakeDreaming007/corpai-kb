package com.xufg.config;

import com.xufg.story.RedisChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Configuration
public class ChainConfig {

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    // 智谱 BigModel 连接配置（外部化到 application.yaml 的 langchain4j.zhipu 段，可用环境变量 ZHIPU_API_KEY 覆盖）
    @Value("${langchain4j.zhipu.base-url}")
    private String zhipuBaseUrl;
    @Value("${langchain4j.zhipu.api-key}")
    private String zhipuApiKey;
    @Value("${langchain4j.zhipu.model-name}")
    private String zhipuModelName;

    // DeepSeek 连接配置（外部化到 application.yaml 的 langchain4j.deepseek 段，可用环境变量 DEEPSEEK_API_KEY 覆盖）
    @Value("${langchain4j.deepseek.base-url}")
    private String deepseekBaseUrl;
    @Value("${langchain4j.deepseek.api-key}")
    private String deepseekApiKey;
    @Value("${langchain4j.deepseek.model-name}")
    private String deepseekModelName;

    /**
     * 智谱 GLM 对话模型（OpenAI 兼容协议，仅换 baseUrl）
     * @return 同步对话模型 Bean
     */
    @Bean
    public OpenAiChatModel glmAiChatModel() {
        return OpenAiChatModel.builder().baseUrl(zhipuBaseUrl)
                .apiKey(zhipuApiKey)
                .logRequests(true)
                .logResponses(true)
                .modelName(zhipuModelName).build();
    }

    /**
     * DeepSeek 同步对话模型
     * @return 同步对话模型 Bean
     */
    @Bean
    public OpenAiChatModel dpAiChatModel() {
        return OpenAiChatModel.builder().baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .logRequests(true)
                .logResponses(true)
                .modelName(deepseekModelName).build();
    }

    /**
     *
     * 流式接口
     * @return
     */
    @Bean
    public OpenAiStreamingChatModel dpAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder().baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .logRequests(true)
                .logResponses(true)
                .modelName(deepseekModelName).build();
    }


    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

    /**
     * 记忆缓存
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        ConcurrentHashMap<Long, ChatMemory> map = new ConcurrentHashMap<>();

        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object id) {
                Long userId = Long.valueOf(id.toString());
                return map.computeIfAbsent(userId, new Function<Long, ChatMemory>() {
                    @Override
                    public ChatMemory apply(Long aLong) {
                        return MessageWindowChatMemory.withMaxMessages(100);
                    }
                });
            }
        };
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider_01() {
//        return memoryId -> MessageWindowChatMemory.builder().id(memoryId.toString())
//                .maxMessages(20).chatMemoryStore(redisChatMemoryStore).build();
        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId.toString())
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
            }
        };
    }

//    @Bean
//    public ChatModel dpAiChatModel() {
//        return OpenAiChatModel.builder().baseUrl()
//    }

//    @Bean
//    public ChatService chatService() {
//        return AiServices.builder(ChatService.class).chatModel(chatModel()).build();
//    }
}
