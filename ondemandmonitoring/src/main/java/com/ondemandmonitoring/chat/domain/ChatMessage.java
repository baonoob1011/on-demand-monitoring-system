package com.ondemandmonitoring.chat.domain;

import com.ondemandmonitoring.chat.enums.ChatMessageType;
import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(
                        name = "idx_chat_message_room_sent_at",
                        columnList = "chat_room_id, sent_at"
                ),
                @Index(
                        name = "idx_chat_message_sender",
                        columnList = "sender_id"
                )
        }
)
public class ChatMessage extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY)
    private List<ChatAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}