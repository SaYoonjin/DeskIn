package com.deskin.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_session_id", columnList = "session_id"))
public class RefreshToken {
    @Id
    @Column(length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LoginSession session;

    @Column(nullable = false)
    private Instant issuedAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;

    public RefreshToken(String tokenHash, LoginSession session, Instant issuedAt) {
        this.tokenHash = tokenHash;
        this.session = session;
        this.issuedAt = issuedAt;
        this.expiresAt = session.getExpiresAt();
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
