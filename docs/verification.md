# 검증 상태

DB 기준: PostgreSQL. 통합 테스트는 PostgreSQL Testcontainers를 사용한다.

- `./gradlew test`: Docker 없이 단위·MVC 테스트 실행.
- `./gradlew postgresTest`: Docker가 필요한 PostgreSQL 통합 테스트 실행.
- `./gradlew compileTestJava`: 통합 테스트 소스 컴파일.

## 인증 단순화 검증

`./gradlew test` 성공: 39개 통과, 실패 0, 건너뜀 0. PostgreSQL 통합 테스트 소스 컴파일 성공.

- Access Token은 15분 JWT이며 Authorization Bearer 헤더로 전달한다.
- 일반 API 요청에서 LoginSessionRepository와 AuthService 호출이 없음을 MVC 테스트로 확인한다.
- 세션 만료·폐기와 독립적으로 Access Token이 만료 시점까지 유효하고, 이후 거절되는지 검증한다.
- 로그인·갱신·로그아웃·회원가입은 Origin 및 CSRF 토큰 없이 호출한다.
- Refresh Token의 HttpOnly 쿠키 발급·교체·삭제를 검증한다.
- Refresh/Logout 서비스의 DB 세션 조회·만료·폐기 검증을 유지한다.
- PostgreSQL 통합 테스트에는 로그아웃 후 일반 API에서 기존 Access Token을 사용할 수 있고 Refresh Token은 거절되는 시나리오가 있다.
- 기본 CORS의 허용 출처·preflight 동작을 검증한다. 커스텀 CORS 필터는 사용하지 않는다.

PostgreSQL 통합 테스트는 Docker 미설치로 미실행이다. 테스트 소스 컴파일과 단위 테스트 성공을 실제 DB 검증 성공으로 간주하지 않는다.

## 회원가입 계약 변경 검증

- `./gradlew test`: 40개 통과, 실패 0, 건너뜀 0. PostgreSQL 통합 테스트 소스 컴파일 성공.
- 회원가입 응답의 name 포함 및 id 제외, phone 누락·null·공백·형식 오류 거절을 검증했다.
- SELLER 가입 시 가게 이름 없이 판매자 프로필을 생성하고 BUYER는 판매자 프로필을 생성하지 않는다.
- 로그인은 기존 id/password 요청을 유지한다.
- 기존 DB의 sellers.store_name NOT NULL 해제 SQL을 docs/migrations에 준비했다. 실제 DB에는 적용하지 않았다.
- Docker 미설치로 PostgreSQL 통합 테스트는 미실행.
