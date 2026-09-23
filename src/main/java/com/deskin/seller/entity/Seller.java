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

    @Column(length = 100)
    private String storeName;

    public Seller(User user) {
        this.user = user;
        // 가입 시에는 가게를 설정하지 않으므로 미설정 상태를 null로 보관한다.
    }
}
