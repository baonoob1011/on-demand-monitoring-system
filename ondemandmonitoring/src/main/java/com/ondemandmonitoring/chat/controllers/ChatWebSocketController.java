package com.ondemandmonitoring.chat.controllers;

import com.ondemandmonitoring.chat.dto.request.ChatTypingRequest;
import com.ondemandmonitoring.chat.dto.request.MarkChatReadRequest;
import com.ondemandmonitoring.chat.dto.request.SendChatMessageRequest;
import com.ondemandmonitoring.chat.dto.response.ChatMessageResponse;
import com.ondemandmonitoring.chat.dto.response.ChatReadResponse;
import com.ondemandmonitoring.chat.dto.response.ChatTypingResponse;
import com.ondemandmonitoring.chat.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // =====================================================
    // SEND MESSAGE
    // SEND:      /app/chat/{roomId}/send
    // SUBSCRIBE: /topic/chat/{roomId}
    // =====================================================

    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(
            @DestinationVariable String roomId,
            SendChatMessageRequest request,
            Principal principal
    ) {

        String currentUserId = principal.getName();

        ChatMessageResponse response =
                chatService.sendMessage(
                        currentUserId,
                        roomId,
                        request
                );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomId,
                response
        );
    }

    // =====================================================
    // TYPING
    // SEND:      /app/chat/{roomId}/typing
    // SUBSCRIBE: /topic/chat/{roomId}/typing
    // =====================================================

    @MessageMapping("/chat/{roomId}/typing")
    public void typing(
            @DestinationVariable String roomId,
            ChatTypingRequest request,
            Principal principal
    ) {

        String currentUserId = principal.getName();

        // Check user thuộc room.
        // getChatRoom() của service đã validate participant.
        chatService.getChatRoom(
                currentUserId,
                roomId
        );

        ChatTypingResponse response =
                new ChatTypingResponse(
                        roomId,
                        currentUserId,
                        request.typing()
                );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomId + "/typing",
                response
        );
    }

    // =====================================================
    // READ / SEEN
    // SEND:      /app/chat/{roomId}/read
    // SUBSCRIBE: /topic/chat/{roomId}/read
    // =====================================================

    @MessageMapping("/chat/{roomId}/read")
    public void markAsRead(
            @DestinationVariable String roomId,
            MarkChatReadRequest request,
            Principal principal
    ) {

        String currentUserId = principal.getName();

        chatService.markAsRead(
                currentUserId,
                roomId,
                request
        );

        ChatReadResponse response =
                new ChatReadResponse(
                        roomId,
                        currentUserId,
                        request.messageId(),
                        Instant.now()
                );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomId + "/read",
                response
        );
    }
}