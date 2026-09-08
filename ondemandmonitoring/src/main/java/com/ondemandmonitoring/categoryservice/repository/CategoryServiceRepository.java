package com.ondemandmonitoring.categoryservice.repository;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryServiceRepository extends JpaRepository<CategoryService, Long> {
}
