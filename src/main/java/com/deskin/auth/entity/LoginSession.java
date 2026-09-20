package com.deskin.auth.entity;

import com.deskin.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "login_sessions", indexes = @Index(name = "idx_sessions_user_id", columnList = "user_id"))
public class LoginSession extends BaseEntity {
    @Id
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;

    public LoginSession(User user, Instant expiresAt) {
        this.sessionId = UUID.randomUUID();
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
