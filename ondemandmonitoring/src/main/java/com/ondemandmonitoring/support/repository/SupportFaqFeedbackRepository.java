package com.ondemandmonitoring.support.repository;

import com.ondemandmonitoring.support.domain.SupportFaqFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportFaqFeedbackRepository extends JpaRepository<SupportFaqFeedback, String> {

    long countByIsHelpfulTrue();

    Optional<SupportFaqFeedback> findByArticleIdAndCustomerId(String articleId, String customerId);

    @Query("SELECT f.articleId as articleId, f.articleQuestion as articleQuestion, COUNT(f) as voteCount " +
            "FROM SupportFaqFeedback f WHERE f.isHelpful = true " +
            "GROUP BY f.articleId, f.articleQuestion " +
            "ORDER BY COUNT(f) DESC")
    List<TopFaqStatProjection> findTopHelpfulArticles();

    interface TopFaqStatProjection {
        String getArticleId();
        String getArticleQuestion();
        Long getVoteCount();
    }
}
