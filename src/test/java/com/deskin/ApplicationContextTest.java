package com.deskin;

import com.deskin.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest extends PostgresIntegrationTest {
    @Test
    void startsApplication() {
    }
}
