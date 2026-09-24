package com.ondemandmonitoring.chat.domain;

import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "chat_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_participant_room_user",
                        columnNames = {"chat_room_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_participant_room",
                        columnList = "chat_room_id"
                ),
                @Index(
                        name = "idx_chat_participant_user",
                        columnList = "user_id"
                )
        }
)
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private ChatMessage lastReadMessage;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
}