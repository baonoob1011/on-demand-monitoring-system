package com.ondemandmonitoring.chat.services.impl;

import com.ondemandmonitoring.chat.domain.*;
import com.ondemandmonitoring.chat.dto.request.CreateDirectChatRequest;
import com.ondemandmonitoring.chat.dto.request.MarkChatReadRequest;
import com.ondemandmonitoring.chat.dto.request.SendChatMessageRequest;
import com.ondemandmonitoring.chat.dto.response.*;
import com.ondemandmonitoring.chat.enums.ChatRoomType;
import com.ondemandmonitoring.chat.repositories.ChatMessageRepository;
import com.ondemandmonitoring.chat.repositories.ChatParticipantRepository;
import com.ondemandmonitoring.chat.repositories.ChatRoomRepository;
import com.ondemandmonitoring.chat.services.ChatService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IUserIdentityService;

import lombok.RequiredArgsConstructor;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final IUserIdentityService userIdentityService;

    // =========================
    // CREATE DIRECT CHAT
    // =========================

    @Override
    public ChatRoomResponse createDirectChat(
            String currentUserId,
            CreateDirectChatRequest request
    ) {

        User currentUser = resolveUser(currentUserId, "Current user not found");
        User otherUser = resolveUser(request.participantUserId(), "Participant user not found");
        String resolvedCurrentUserId = currentUser.getId();
        String otherUserId = otherUser.getId();

        if (resolvedCurrentUserId.equals(otherUserId)) {
            throw new RuntimeException("Cannot create chat room with yourself");
        }

        // Kiểm tra 2 user đã có DIRECT room chưa
        ChatRoom existingRoom = chatRoomRepository
                .findDirectRoomBetweenUsers(
                        ChatRoomType.DIRECT,
                        resolvedCurrentUserId,
                        otherUserId
                )
                .orElse(null);

        if (existingRoom != null) {
            return toRoomResponse(existingRoom, resolvedCurrentUserId);
        }

        // Tạo room
        ChatRoom room = new ChatRoom();
        room.setType(ChatRoomType.DIRECT);

        room = chatRoomRepository.save(room);

        // Participant 1
        ChatParticipant participant1 = new ChatParticipant();
        participant1.setChatRoom(room);
        participant1.setUser(currentUser);

        // Participant 2
        ChatParticipant participant2 = new ChatParticipant();
        participant2.setChatRoom(room);
        participant2.setUser(otherUser);

        chatParticipantRepository.save(participant1);
        chatParticipantRepository.save(participant2);

        return toRoomResponse(room, resolvedCurrentUserId);
    }

    // =========================
    // GET MY ROOMS
    // =========================

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(String currentUserId) {
        String resolvedCurrentUserId = resolveUserId(currentUserId, "Current user not found");

        List<ChatParticipant> participants =
                chatParticipantRepository.findByUserId(resolvedCurrentUserId);

        return participants.stream()
                .map(ChatParticipant::getChatRoom)
                .map(room -> toRoomResponse(room, resolvedCurrentUserId))
                .toList();
    }

    // =========================
    // GET ONE ROOM
    // =========================

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(
            String currentUserId,
            String chatRoomId
    ) {

        String resolvedCurrentUserId = resolveUserId(currentUserId, "Current user not found");

        validateParticipant(chatRoomId, resolvedCurrentUserId);

        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new RuntimeException("Chat room not found"));

        return toRoomResponse(room, resolvedCurrentUserId);
    }

    // =========================
    // GET MESSAGES
    // =========================

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getMessages(
            String currentUserId,
            String chatRoomId,
            Pageable pageable
    ) {

        String resolvedCurrentUserId = resolveUserId(currentUserId, "Current user not found");

        validateParticipant(chatRoomId, resolvedCurrentUserId);

        return chatMessageRepository
                .findByChatRoomIdOrderBySentAtDesc(
                        chatRoomId,
                        pageable
                )
                .map(this::toMessageResponse);
    }

    // =========================
    // SEND MESSAGE
    // =========================

    @Override
    public ChatMessageResponse sendMessage(
            String currentUserId,
            String chatRoomId,
            SendChatMessageRequest request
    ) {

        User sender = resolveUser(currentUserId, "User not found");
        String resolvedCurrentUserId = sender.getId();

        validateParticipant(chatRoomId, resolvedCurrentUserId);

        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new RuntimeException("Chat room not found"));

        ChatMessage message = new ChatMessage();

        message.setChatRoom(room);
        message.setSender(sender);
        message.setMessageType(request.messageType());
        message.setContent(request.content());

        // Reply message
        if (request.replyToMessageId() != null) {

            ChatMessage replyMessage =
                    chatMessageRepository
                            .findById(request.replyToMessageId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Reply message not found"
                                    ));

            // Không cho reply message của room khác
            if (!replyMessage.getChatRoom()
                    .getId()
                    .equals(chatRoomId)) {

                throw new RuntimeException(
                        "Reply message does not belong to this chat room"
                );
            }

            message.setReplyToMessage(replyMessage);
        }

        message = chatMessageRepository.save(message);

        // Update thời gian message cuối
        room.setLastMessageAt(Instant.now());

        chatRoomRepository.save(room);

        return toMessageResponse(message);
    }

    // =========================
    // MARK AS READ
    // =========================

    @Override
    public void markAsRead(
            String currentUserId,
            String chatRoomId,
            MarkChatReadRequest request
    ) {

        String resolvedCurrentUserId = resolveUserId(currentUserId, "Current user not found");

        ChatParticipant participant =
                chatParticipantRepository
                        .findByChatRoomIdAndUserId(
                                chatRoomId,
                                resolvedCurrentUserId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "You are not a participant of this room"
                                ));

        ChatMessage message = chatMessageRepository
                .findById(request.messageId())
                .orElseThrow(() ->
                        new RuntimeException("Message not found"));

        // message phải thuộc room hiện tại
        if (!message.getChatRoom()
                .getId()
                .equals(chatRoomId)) {

            throw new RuntimeException(
                    "Message does not belong to this chat room"
            );
        }

        participant.setLastReadMessage(message);
        participant.setLastReadAt(Instant.now());

        chatParticipantRepository.save(participant);
    }

    // =========================
    // VALIDATION
    // =========================

    private void validateParticipant(
            String chatRoomId,
            String userId
    ) {

        boolean exists =
                chatParticipantRepository
                        .existsByChatRoomIdAndUserId(
                                chatRoomId,
                                userId
                        );

        if (!exists) {
            throw new RuntimeException(
                    "You are not a participant of this chat room"
            );
        }
    }

    private String resolveUserId(String userIdentifier, String notFoundMessage) {
        return resolveUser(userIdentifier, notFoundMessage).getId();
    }

    private User resolveUser(String userIdentifier, String notFoundMessage) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            throw new RuntimeException(notFoundMessage);
        }

        String identifier = userIdentifier.trim();

        return userRepository.findById(identifier)
                .or(() -> findUserByCognitoSub(identifier))
                .or(() -> findUserByCognitoUsername(identifier))
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new RuntimeException(notFoundMessage));
    }

    private java.util.Optional<User> findUserByCognitoSub(String cognitoSub) {
        try {
            return java.util.Optional.of(userIdentityService.findUserByCognitoSub(cognitoSub));
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                return java.util.Optional.empty();
            }
            throw exception;
        }
    }

    private java.util.Optional<User> findUserByCognitoUsername(String cognitoUsername) {
        try {
            return java.util.Optional.of(userIdentityService.findUserByCognitoUsername(cognitoUsername));
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                return java.util.Optional.empty();
            }
            throw exception;
        }
    }

    // =========================
    // MESSAGE MAPPER
    // =========================

    private ChatMessageResponse toMessageResponse(
            ChatMessage message
    ) {

        ChatUserResponse sender =
                toUserResponse(message.getSender());

        ReplyMessageResponse reply = null;

        if (message.getReplyToMessage() != null) {

            ChatMessage replyMessage =
                    message.getReplyToMessage();

            reply = new ReplyMessageResponse(
                    replyMessage.getId(),
                    replyMessage.getSender().getId(),
                    getUserName(replyMessage.getSender()),
                    replyMessage.getContent()
            );
        }

        List<ChatAttachmentResponse> attachments =
                message.getAttachments()
                        .stream()
                        .map(this::toAttachmentResponse)
                        .toList();

        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                sender,
                message.getMessageType(),
                message.getContent(),
                reply,
                attachments,
                message.getSentAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }

    // =========================
    // ROOM MAPPER
    // =========================

    private ChatRoomResponse toRoomResponse(
            ChatRoom room,
            String currentUserId
    ) {

        List<ChatParticipant> participants =
                chatParticipantRepository
                        .findByChatRoomId(room.getId());

        List<ChatParticipantResponse> participantResponses =
                participants.stream()
                        .map(this::toParticipantResponse)
                        .toList();

        LastMessageResponse lastMessageResponse = null;

        var lastMessageOptional =
                chatMessageRepository
                        .findTopByChatRoomIdOrderBySentAtDesc(
                                room.getId()
                        );

        if (lastMessageOptional.isPresent()) {

            ChatMessage lastMessage =
                    lastMessageOptional.get();

            lastMessageResponse =
                    new LastMessageResponse(
                            lastMessage.getId(),
                            lastMessage.getSender().getId(),
                            lastMessage.getContent(),
                            lastMessage.getMessageType(),
                            lastMessage.getSentAt()
                    );
        }

        // Tạm thời để 0
        // Bước sau sẽ thêm query tính unread
        long unreadCount = 0;

        return new ChatRoomResponse(
                room.getId(),
                room.getType(),
                participantResponses,
                lastMessageResponse,
                unreadCount,
                room.getLastMessageAt(),
                room.getCreatedAt()
        );
    }

    // =========================
    // PARTICIPANT MAPPER
    // =========================

    private ChatParticipantResponse toParticipantResponse(
            ChatParticipant participant
    ) {

        String lastReadMessageId = null;

        if (participant.getLastReadMessage() != null) {
            lastReadMessageId =
                    participant.getLastReadMessage().getId();
        }

        return new ChatParticipantResponse(
                String.valueOf(participant.getId()),
                toUserResponse(participant.getUser()),
                lastReadMessageId,
                participant.getLastReadAt(),
                participant.getJoinedAt()
        );
    }

    // =========================
    // ATTACHMENT MAPPER
    // =========================

    private ChatAttachmentResponse toAttachmentResponse(
            ChatAttachment attachment
    ) {

        return new ChatAttachmentResponse(
                getAttachmentId(attachment),
                attachment.getFileUrl(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getAttachmentType()
        );
    }

    // =========================
    // USER MAPPER
    // =========================

    private ChatUserResponse toUserResponse(User user) {

        return new ChatUserResponse(
                user.getId(),
                getUserName(user),
                getAvatarUrl(user)
        );
    }

    private String getUserName(User user) {
        return user.getFullName();
    }

    private String getAvatarUrl(User user) {
        return user.getAvatarS3Key();
    }

    private String getAttachmentId(ChatAttachment attachment) {
        if (attachment.getStorageKey() != null && !attachment.getStorageKey().isBlank()) {
            return attachment.getStorageKey();
        }
        return attachment.getFileUrl();
    }
}
