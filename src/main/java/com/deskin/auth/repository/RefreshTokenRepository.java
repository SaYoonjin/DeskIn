package com.deskin.auth.repository;

import com.deskin.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Query("select token.session.sessionId from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);
}
