package com.ondemandmonitoring.chat.domain;

import com.ondemandmonitoring.chat.enums.ChatAttachmentType;
import com.ondemandmonitoring.common.entity.BaseEntity;
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
        name = "chat_attachments",
        indexes = {
                @Index(
                        name = "idx_chat_attachment_message",
                        columnList = "message_id"
                )
        }
)
public class ChatAttachment extends BaseEntity  {



    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 20)
    private ChatAttachmentType attachmentType;


}