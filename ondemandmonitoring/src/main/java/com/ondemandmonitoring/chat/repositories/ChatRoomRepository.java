package com.ondemandmonitoring.chat.repositories;

import com.ondemandmonitoring.chat.domain.ChatRoom;

import com.ondemandmonitoring.chat.enums.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    @Query("""
        SELECT r
        FROM ChatRoom r
        WHERE r.type = :type
          AND (
              SELECT COUNT(p)
              FROM ChatParticipant p
              WHERE p.chatRoom = r
          ) = 2
          AND EXISTS (
              SELECT p1.id
              FROM ChatParticipant p1
              WHERE p1.chatRoom = r
                AND p1.user.id = :userId1
          )
          AND EXISTS (
              SELECT p2.id
              FROM ChatParticipant p2
              WHERE p2.chatRoom = r
                AND p2.user.id = :userId2
          )
        """)
    Optional<ChatRoom> findDirectRoomBetweenUsers(
            @Param("type") ChatRoomType type,
            @Param("userId1") String userId1,
            @Param("userId2") String userId2
    );
}