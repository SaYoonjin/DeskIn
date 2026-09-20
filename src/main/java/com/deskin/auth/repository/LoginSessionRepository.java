package com.deskin.auth.repository;

import com.deskin.auth.entity.LoginSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {
    @Override
    @EntityGraph(attributePaths = "user")
    Optional<LoginSession> findById(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LoginSession session where session.sessionId = :sessionId")
    Optional<LoginSession> findLockedById(@Param("sessionId") UUID sessionId);
}
