package com.ondemandmonitoring.chat.repositories;

import com.ondemandmonitoring.chat.domain.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository
        extends JpaRepository<ChatParticipant, String> {

    List<ChatParticipant> findByUserId(String userId);

    List<ChatParticipant> findByChatRoomId(String chatRoomId);

    Optional<ChatParticipant> findByChatRoomIdAndUserId(
            String chatRoomId,
            String userId
    );

    boolean existsByChatRoomIdAndUserId(
            String chatRoomId,
            String userId
    );

    long countByChatRoomId(String chatRoomId);
}