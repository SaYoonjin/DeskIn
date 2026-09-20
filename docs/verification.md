# 검증 상태

DB 기준: PostgreSQL. 통합 테스트는 실제 PostgreSQL Testcontainers를 사용한다.

- `./gradlew test`: Docker 없이 단위 테스트 실행.
- `./gradlew postgresTest`: Docker가 필요한 PostgreSQL 통합 테스트 실행.
- `./gradlew compileTestJava`: 통합 테스트를 실행하지 않고 컴파일 검증.

현재 작업 환경에는 Docker 명령과 소켓이 없다. PostgreSQL 통합 테스트는 **작성했으나 미실행**이며, 실행 성공으로 간주하지 않는다. 사용자가 허용한 절차에 따라 단위 테스트 검증 후 기능별로 로컬 커밋한다. 배포 전 Docker 환경에서 `postgresTest`를 실행해야 한다.

기존 다른 DB 기반 테스트 결과는 PostgreSQL 검증 결과가 아니다.

## 2026-09-21 검증 결과

`./gradlew test compileTestJava bootJar` 성공.

- Docker 없는 단위·MVC 테스트: **43개 성공, 실패 0, 건너뜀 0**.
- PostgreSQL 통합 테스트 소스: 컴파일 성공. `postgresTest`는 Docker 부재로 **실행하지 않음**.
- 실행 JAR: 빌드 성공. 실제 PostgreSQL 연결 및 애플리케이션 운영 기동 검증을 의미하지 않음.
- 소스·문서 UTF-8 및 `git diff --check` 확인.
- DB 드라이버·Testcontainers·설정은 PostgreSQL 기준이며 대체 DB 의존성은 제거함.

| 기능 | Docker 없는 검증 | PostgreSQL 통합 테스트 코드 |
| --- | --- | --- |
| 회원가입 | 정규화·해시·역할·중복·입력 검증, HTTP 계약 | User/Seller 저장·롤백·동시 중복 제약 |
| 로그인 | 비밀번호 실패·독립 세션·JWT·쿠키, HTTP 계약 | 실제 세션·해시 저장·다중 로그인 |
| 토큰 갱신 | 교체·고정 만료·재사용 폐기·만료/폐기 거절 | 행 잠금·동시 갱신·재사용 폐기 커밋 |
| 로그아웃 | 현재 세션 폐기·사용자/역할 검사·쿠키 삭제 | 토큰 즉시 차단·다른 기기 유지·갱신 경합·만료 후 갱신 |
| 공통 보안 | CSRF·CORS·권한 행렬·principal·JWT 필터 | 실제 JWT/DB 세션 기반 역할 제한·CSRF |

위 표의 PostgreSQL 항목은 모두 **작성 및 컴파일만 완료**한 상태다. Docker 환경에서는 `./gradlew test postgresTest`를 실행해 DB 관련 검증을 완료해야 한다.
