package com.deskin.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("postgresql")
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {
    // 컨텍스트 캐시와 컨테이너 수명을 맞춰 테스트 클래스 사이에 연결이 끊기지 않게 한다.
    private static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        database.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.datasource.driver-class-name", database::getDriverClassName);
    }
}
