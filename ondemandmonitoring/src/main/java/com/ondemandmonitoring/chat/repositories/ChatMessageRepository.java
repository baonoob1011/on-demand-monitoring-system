package com.ondemandmonitoring.chat.repositories;

import com.ondemandmonitoring.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, String> {

    Page<ChatMessage> findByChatRoomIdOrderBySentAtDesc(
            String chatRoomId,
            Pageable pageable
    );

    Optional<ChatMessage> findTopByChatRoomIdOrderBySentAtDesc(
            String chatRoomId
    );
}