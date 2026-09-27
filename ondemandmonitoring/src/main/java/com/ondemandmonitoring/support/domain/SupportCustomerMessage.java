package com.ondemandmonitoring.support.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "support_customer_messages")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupportCustomerMessage extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    String ticketId;

    @Column(name = "sender_id", nullable = false)
    String senderId;

    @Column(name = "sender_name", nullable = false, length = 150)
    String senderName;

    @Column(name = "sender_role", nullable = false, length = 50)
    String senderRole; // CUSTOMER, STAFF, MANAGER, SYSTEM

    @Column(name = "content", nullable = false, length = 4000)
    String content;

    @Column(name = "attachment_url", length = 500)
    String attachmentUrl;

    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
