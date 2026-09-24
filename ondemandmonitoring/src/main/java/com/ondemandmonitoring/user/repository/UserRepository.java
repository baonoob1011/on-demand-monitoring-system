package com.ondemandmonitoring.user.repository;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.role.domain.RoleCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByRole_CodeAndIsActiveTrueOrderByFullNameAsc(RoleCode roleCode);
}
