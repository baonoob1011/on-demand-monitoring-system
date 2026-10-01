package com.ondemandmonitoring.support.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "support_faq_feedbacks")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupportFaqFeedback extends BaseEntity {

    @Column(name = "article_id", nullable = false, length = 100)
    String articleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", referencedColumnName = "article_id", insertable = false, updatable = false)
    SupportFaqArticle article;

    @Column(name = "article_question", length = 255)
    String articleQuestion;

    @Column(name = "article_category", length = 100)
    String articleCategory;

    @Column(name = "customer_id", length = 100)
    String customerId;

    @Column(name = "is_helpful", nullable = false)
    Boolean isHelpful = true;

    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
