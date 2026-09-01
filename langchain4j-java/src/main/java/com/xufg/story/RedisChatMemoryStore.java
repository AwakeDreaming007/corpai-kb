package com.xufg.story;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String PRE_NAME = "langchain4j:";

    /** 聊天记忆 TTL（小时）。 */
    @Value("${kb.memory-ttl-hours:24}")
    private int memoryTtlHours;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String result = stringRedisTemplate.opsForValue().get(PRE_NAME + memoryId.toString());
        if (!StringUtils.hasText(result)) {
            return new ArrayList<>();
        }
        List<ChatMessage> chatMessages = ChatMessageDeserializer.messagesFromJson(result);
        return chatMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String str = ChatMessageSerializer.messagesToJson(list);
        stringRedisTemplate.opsForValue().set(PRE_NAME + memoryId.toString(), str,
                Duration.ofHours(memoryTtlHours));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(PRE_NAME + memoryId.toString());
    }
}
