package com.xufg.service;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "glmAiChatModel")
public interface GLMChatService {
    String sendMessage(String message);
}
