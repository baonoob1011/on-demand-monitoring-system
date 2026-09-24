package com.ondemandmonitoring.chat.services;

import com.ondemandmonitoring.chat.dto.request.CreateDirectChatRequest;
import com.ondemandmonitoring.chat.dto.request.MarkChatReadRequest;
import com.ondemandmonitoring.chat.dto.request.SendChatMessageRequest;
import com.ondemandmonitoring.chat.dto.response.ChatMessageResponse;
import com.ondemandmonitoring.chat.dto.response.ChatRoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    ChatRoomResponse createDirectChat(
            String currentUserId,
            CreateDirectChatRequest request
    );

    List<ChatRoomResponse> getMyChatRooms(
            String currentUserId
    );

    ChatRoomResponse getChatRoom(
            String currentUserId,
            String chatRoomId
    );

    Page<ChatMessageResponse> getMessages(
            String currentUserId,
            String chatRoomId,
            Pageable pageable
    );

    ChatMessageResponse sendMessage(
            String currentUserId,
            String chatRoomId,
            SendChatMessageRequest request
    );

    void markAsRead(
            String currentUserId,
            String chatRoomId,
            MarkChatReadRequest request
    );
}