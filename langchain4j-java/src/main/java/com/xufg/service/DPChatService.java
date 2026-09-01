
package com.xufg.service;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "dpAiChatModel")
public interface DPChatService {
    String sendMessage(String message);
}
