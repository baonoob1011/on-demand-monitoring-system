package com.ondemandmonitoring.support.repository;

import com.ondemandmonitoring.support.domain.SupportFaqArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportFaqArticleRepository extends JpaRepository<SupportFaqArticle, String> {

    List<SupportFaqArticle> findByIsPublishedTrueOrderByArticleIdAsc();

    List<SupportFaqArticle> findByCategoryAndIsPublishedTrueOrderByArticleIdAsc(String category);
}
