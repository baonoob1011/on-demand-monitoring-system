package com.ondemandmonitoring.user.repository;

import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, IdentityProvider provider);

    Optional<UserIdentity> findByCognitoSub(String cognitoSub);

    boolean existsByCognitoSubAndProvider(String cognitoSub, IdentityProvider provider);
}
