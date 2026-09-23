package com.deskin.auth;

import com.deskin.support.PostgresIntegrationTest;
import com.deskin.auth.entity.User;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.repository.UserRepository;
import com.deskin.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class SignupPostgresConcurrencyTest extends PostgresIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired GlobalExceptionHandler exceptionHandler;

    @Test
    void mapsConcurrentLoginIdConflictToExpectedError() throws Exception {
        verifyConcurrentConflict("DUPLICATE_LOGIN_ID", true);
    }

    @Test
    void mapsConcurrentEmailConflictToExpectedError() throws Exception {
        verifyConcurrentConflict("DUPLICATE_EMAIL", false);
    }

    private void verifyConcurrentConflict(String expectedCode, boolean sameLoginId) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(
                    () -> createConcurrentUser(barrier, sameLoginId, 1),
                    () -> createConcurrentUser(barrier, sameLoginId, 2)), 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder("CREATED", expectedCode);
        } finally {
            executor.shutdownNow();
        }
    }

    private String createConcurrentUser(CyclicBarrier barrier, boolean sameLoginId, int number) {
        String loginId = sameLoginId ? "race_login" : "race_email_" + number;
        String email = sameLoginId ? "race" + number + "@example.com" : "race_email@example.com";
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                // 두 요청의 사전 조회가 통과한 뒤 DB 제약으로 충돌을 해결하는 상황을 재현한다.
                assertThat(userRepository.existsByLoginId(loginId)).isFalse();
                assertThat(userRepository.existsByEmail(email)).isFalse();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                userRepository.saveAndFlush(new User(loginId, "test-hash", "테스트", null, email, UserRole.BUYER));
            });
            return "CREATED";
        } catch (DataIntegrityViolationException exception) {
            var response = exceptionHandler.handleIntegrityException(exception);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            return response.getBody().error().code();
        }
    }
}
