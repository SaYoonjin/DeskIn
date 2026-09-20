package com.deskin.seller.entity;

import com.deskin.auth.entity.User;
import com.deskin.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sellers", uniqueConstraints = @UniqueConstraint(name = "uk_sellers_user_id", columnNames = "user_id"))
public class Seller extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sellerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String storeName;

    public Seller(User user, String storeName) {
        this.user = user;
        this.storeName = storeName;
    }
}
