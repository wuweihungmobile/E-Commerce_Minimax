package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.core.chat.ChatService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 聊天 REST API (Mock Implementation)
 */
@Slf4j
@RestController
@RequestMapping("/v2/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 建立對話
     */
    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatDto.ConversationResponse>> createConversation(
            @Valid @RequestBody ChatDto.CreateConversationRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Create conversation: userId={}, recipient={}", userId, request.getRecipientId());
        ChatDto.ConversationResponse response = chatService.createConversation(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Conversation created", response));
    }

    /**
     * 取得用戶對話列表
     */
    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatDto.ConversationListResponse>> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = TenantContext.getCurrentUser();
        ChatDto.ConversationListResponse response = chatService.getUserConversations(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除對話
     */
    @DeleteMapping("/conversations/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable UUID conversationId) {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Delete conversation: userId={}, conversationId={}", userId, conversationId);
        chatService.deleteConversation(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted", null));
    }

    /**
     * 發送消息
     */
    @PostMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatDto.MessageResponse>> sendMessage(
            @Valid @RequestBody ChatDto.SendMessageRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Send message: userId={}, conversationId={}", userId, request.getConversationId());
        ChatDto.MessageResponse response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Message sent", response));
    }

    /**
     * 取得消息歷史
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatDto.MessageListResponse>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = TenantContext.getCurrentUser();
        ChatDto.MessageListResponse response = chatService.getMessages(userId, conversationId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 標記已讀
     */
    @PutMapping("/conversations/{conversationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID conversationId) {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Mark as read: userId={}, conversationId={}", userId, conversationId);
        chatService.markAsRead(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }
}
