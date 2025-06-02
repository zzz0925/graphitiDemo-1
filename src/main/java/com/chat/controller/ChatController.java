// graphitiDemo/src/main/java/com/chat/controller/ChatController.java
package com.chat.controller;

import com.chat.dto.ChatRequest;
import com.chat.dto.ChatResponse;
import com.chat.entity.ChatMessage;
import com.chat.entity.ChatSession; // 导入 ChatSession
import com.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@Validated @RequestBody ChatRequest request) {
        log.info("Received message: {}", request.getMessage());
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String sessionId) {
        List<ChatMessage> history = chatService.getChatHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getAllChatSessions() {
        List<ChatSession> sessions = chatService.getAllChatSessions();
        return ResponseEntity.ok(sessions);
    }
}