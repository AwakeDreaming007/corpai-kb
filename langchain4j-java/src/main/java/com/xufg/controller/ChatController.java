package com.xufg.controller;

import com.xufg.service.DPChatService;
import com.xufg.service.DPStreamingChatService;
import com.xufg.service.GLMChatService;
import com.xufg.service.VectorRagService;
import dev.langchain4j.chain.ConversationalChain;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;


@RestController
public class ChatController {

    @Resource
    private GLMChatService glmChatService;

    @Resource
    private DPChatService dpChatService;

    @Resource
    private DPStreamingChatService dpStreamingChatService;

    @Resource
    private ChatMemoryProvider chatMemoryProvider;

    @Autowired
    @Qualifier("dpAiChatModel")
    private OpenAiChatModel dpAiChatModel;

    @Resource
    private VectorRagService vectorRagService;

    @GetMapping("/chat")
    public String model(@RequestParam(value = "type", defaultValue = "1") Integer type, @RequestParam(value = "message", defaultValue = "Hello") String message) {
        return type == 1 ? glmChatService.sendMessage(message) : dpChatService.sendMessage(message);
    }

    @GetMapping("/streaming/chat")
        public Flux<String> sendMessage(@RequestParam(value = "memoryId") String memoryId, @RequestParam(value = "message") String message) {
        return dpStreamingChatService.sendMessage(memoryId, message);
    }


    @GetMapping("/chatMemory")
    public String chatMemory(@RequestParam(value = "userId") Long userId, @RequestParam(value = "message") String message) {
        ChatMemory chatMemory = chatMemoryProvider.get(userId);
        ConversationalChain chain = ConversationalChain.builder()
                .chatMemory(chatMemory)
                .chatModel(dpAiChatModel)
                .build();
        return chain.execute(message);
    }

    @GetMapping("/storeTextPDF")
    public String storeText4PDF(MultipartFile file) throws IOException {
        return vectorRagService.storeText4PDF(file);
    }

    @GetMapping("/storeText")
    public String storeText(@RequestParam(value = "text", required = true) String text) {
        vectorRagService.storeText(text);
        return "Stored successfully!";
    }


    @GetMapping("/findSimilarTexts")
    public List<String> findSimilarTexts(@RequestParam(value = "query", required = true) String query,
                                  @RequestParam(value = "maxResults", required = false, defaultValue = "5") int maxResults) {
        return vectorRagService.findSimilarTexts(query, maxResults);
    }
}
