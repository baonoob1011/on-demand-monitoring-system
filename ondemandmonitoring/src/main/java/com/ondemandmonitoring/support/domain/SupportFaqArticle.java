package com.ondemandmonitoring.support.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Entity
@Table(name = "support_faq_articles")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupportFaqArticle extends BaseEntity {

    @Column(name = "article_id", nullable = false, unique = true, length = 100)
    String articleId;

    @Column(name = "category", nullable = false, length = 100)
    String category;

    @Column(name = "category_label", length = 150)
    String categoryLabel;

    @Column(name = "question", nullable = false, length = 500)
    String question;

    @Column(name = "answer", nullable = false, length = 3000)
    String answer;

    @Column(name = "keywords", length = 1000)
    String keywords;

    @Column(name = "is_published", nullable = false)
    Boolean isPublished = true;
}
