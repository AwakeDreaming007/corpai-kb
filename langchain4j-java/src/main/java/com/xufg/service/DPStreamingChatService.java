package com.xufg.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "dpAiStreamingChatModel",
//        chatMemory = "chatMemory"
        chatMemoryProvider = "chatMemoryProvider_01",
        contentRetriever = "contentRetriever")
public interface DPStreamingChatService {

//    @SystemMessage(fromResource = "system.txt")
    @SystemMessage("你是一个个人资料管理助手，叫灵宝")
    Flux<String> sendMessage(@MemoryId String memoryId, @UserMessage String message);

//    Flux<String> sendMessage(String message);
}
