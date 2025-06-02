// graphitiDemo/src/main/java/com/chat/service/ChatService.java
package com.chat.service;

import com.chat.dto.ChatRequest;
import com.chat.dto.ChatResponse;
import com.chat.dto.GraphitiRequest;
import com.chat.dto.GraphitiRequest.GraphitiMessage; // 导入 GraphitiMessage 内部类
import com.chat.entity.ChatMessage;
import com.chat.entity.ChatSession;
import com.chat.repository.ChatMessageRepository;
import com.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant; // 用于获取 UTC 时间戳
import java.time.LocalDateTime; // 原始代码使用 LocalDateTime
import java.time.ZoneOffset; // 用于 LocalDateTime 到毫秒时间戳的转换
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors; // 用于流操作

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ModelService modelService;
    private final GraphitiService graphitiService;

    @Transactional
    public ChatResponse processChat(ChatRequest request) {
        try {
            // 获取或创建会话
            ChatSession session = getOrCreateSession(request.getSessionId());

            // 保存用户消息
            ChatMessage userMessage = new ChatMessage();
            userMessage.setSession(session);
            userMessage.setRole("user");
            userMessage.setContent(request.getMessage());
            // 保存后，确保 userMessage 对象包含了数据库生成的 createdTime
            userMessage = messageRepository.save(userMessage);

            // 调用模型生成回复
            String modelResponse = modelService.generateResponse(request.getMessage());

            // 保存助手回复
            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setSession(session);
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(modelResponse);
            // 保存后，确保 assistantMessage 对象包含了数据库生成的 createdTime
            assistantMessage = messageRepository.save(assistantMessage);

            // 异步保存到Graphiti
            // 现在只传递会话ID，因为我们将从数据库中获取所有消息
            saveToGraphitiAsync(session.getSessionId());

            return ChatResponse.success(modelResponse, session.getSessionId());

        } catch (Exception e) {
            log.error("处理聊天请求时出错: ", e);
            return ChatResponse.error("处理请求时发生错误，请稍后重试");
        }
    }

    private ChatSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            // 创建新会话
            ChatSession newSession = new ChatSession();
            newSession.setSessionId(UUID.randomUUID().toString());
            newSession.setCreatedTime(LocalDateTime.now()); // 设置创建时间
            newSession.setUpdatedTime(LocalDateTime.now()); // 设置更新时间
            return sessionRepository.save(newSession);
        }

        return sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    ChatSession newSession = new ChatSession();
                    newSession.setSessionId(sessionId);
                    newSession.setCreatedTime(LocalDateTime.now()); // 设置创建时间
                    newSession.setUpdatedTime(LocalDateTime.now()); // 设置更新时间
                    return sessionRepository.save(newSession);
                });
    }

    // 更新后的 saveToGraphitiAsync 方法
    private void saveToGraphitiAsync(String sessionId) {
        new Thread(() -> {
            try {
                // 获取当前会话的所有消息
                List<ChatMessage> chatMessages = messageRepository.findBySessionIdOrderByCreatedTime(sessionId);

                // 将 ChatMessage 列表转换为 GraphitiRequest.GraphitiMessage 列表
                List<GraphitiMessage> graphitiMessages = chatMessages.stream()
                        .map(msg -> {
                            GraphitiMessage gm = new GraphitiMessage();
                            gm.setRole(msg.getRole());
                            gm.setContent(msg.getContent());
                            gm.setTimestamp(msg.getCreatedTime().toInstant(ZoneOffset.UTC).toEpochMilli());
                            return gm;
                        })
                        .collect(Collectors.toList());

                // 构建 GraphitiRequest
                GraphitiRequest graphitiRequest = new GraphitiRequest(sessionId, graphitiMessages);

                graphitiService.saveToGraphiti(graphitiRequest)
                        .subscribe(
                                success -> {
                                    if (success) {
                                        log.info("成功保存到Graphiti: sessionId={}", sessionId);
                                    } else {
                                        log.warn("Graphiti保存操作未成功，可能API返回非预期或Graphiti服务问题: sessionId={}", sessionId);
                                    }
                                },
                                error -> log.error("保存到Graphiti失败: ", error)
                        );
            } catch (Exception e) {
                log.error("异步保存到Graphiti时出错: ", e);
            }
        }).start();
    }

    public List<ChatMessage> getChatHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedTime(sessionId);
    }

    // 新增：获取所有会话
    public List<ChatSession> getAllChatSessions() {
        return sessionRepository.findAllByOrderByCreatedTimeDesc();
    }
}