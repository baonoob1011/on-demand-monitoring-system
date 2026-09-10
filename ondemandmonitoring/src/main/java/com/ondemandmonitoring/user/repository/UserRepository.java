package com.ondemandmonitoring.user.repository;

import com.ondemandmonitoring.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the User aggregate. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByCognitoSub(String cognitoSub);
}
