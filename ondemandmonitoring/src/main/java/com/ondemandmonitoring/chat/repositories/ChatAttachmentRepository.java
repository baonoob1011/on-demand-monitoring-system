package com.ondemandmonitoring.chat.repositories;

import com.ondemandmonitoring.chat.domain.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatAttachmentRepository
        extends JpaRepository<ChatAttachment, String> {

    List<ChatAttachment> findByMessageId(String messageId);
}