# 인증 API 계약

공통 개발 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

## 구조

- Controller: JSON 요청과 @Valid 검증, 서비스 호출, ApiResponse 포장.
- AuthServiceImpl: 가입·비밀번호 검증, 토큰 발급·조회·삭제.
- JwtTokenProvider: User로 JWT 생성, 서명·만료·발급자·대상·클레임 검증.
- JwtAuthenticationFilter: JWT에서 userId/role을 추출해 AuthPrincipal과 SecurityContext 구성. DB 조회 없음.
- RefreshToken: tokenHash, User, expiresAt. createdAt/updatedAt은 BaseEntity의 JPA auditing을 재사용한다.
- OpaqueTokenProvider: SecureRandom 32바이트 토큰 생성, SHA-256 해시, 형식 검사.

LoginSession, 기기별 세션, rotation, usedAt, 재사용 탐지, 비관적 락, 쿠키 처리는 사용하지 않는다.

## 회원가입

`POST /auth/signup`: JWT 불필요. 성공 시 201이며 자동 로그인하지 않는다.

요청: `id`, `password`, `name`, `phone`, `role`, `email` 모두 필수. `storeName`은 가입 요청에서 받지 않는다. SELLER는 가게 이름이 없는 판매자 프로필을 만들고 이후 마이페이지에서 가게를 설정한다. 가게 설정 API는 아직 구현하지 않았다.

```json
{"id":"buyer_1","password":"Password123!","name":"홍길동","phone":"010-1234-5678","role":"BUYER","email":"buyer@example.com"}
```

```json
{"success":true,"message":"회원가입이 완료되었습니다.","data":{"userId":12,"name":"홍길동","role":"BUYER"}}
```

- 로그인 문자열 `id`와 내부 숫자 `userId`는 다르다.
- 아이디는 영문·숫자·밑줄 4~30자이며 공백 제거 후 소문자로 정규화한다. 이메일도 공백 제거 후 소문자로 정규화한다.
- 아이디·이메일은 각각 중복될 수 없다. 사전 조회 이후의 DB 충돌도 409로 응답한다.
- 비밀번호는 10~64자, UTF-8 기준 72바이트 이하이며 원문을 변환하지 않는다.
- 이름 1~50자, 이메일 최대 254자.
- 연락처는 공백·하이픈을 제거하고 선택적인 선행 + 및 숫자 8~15자리를 허용한다. 누락·null·빈 값은 허용하지 않는다.
- 공개 가입 역할은 BUYER·SELLER뿐이다. 판매자 User와 가게 미설정 상태의 Seller는 한 트랜잭션으로 생성한다.
- 기존 PostgreSQL DB는 [store_name 제약 변경 SQL](migrations/20260921_seller_store_name_optional.sql)을 적용해야 한다. 새 스키마에서는 store_name이 nullable로 생성된다.

## 로그인

`POST /auth/login`, Content-Type: application/json. 기존 요청 필드명 id를 유지한다.

```json
{"id":"buyer_1","password":"Password123!"}
```

성공 200:

```json
{"success":true,"message":"로그인 되었습니다.","data":{"accessToken":"<JWT>","refreshToken":"<opaque token>","userId":12,"role":"BUYER"}}
```

- BCrypt 비밀번호 비교와 dummyPasswordHash를 유지한다. 없는 아이디와 잘못된 비밀번호는 모두 INVALID_CREDENTIALS.
- Access Token은 15분, Refresh Token은 발급 후 7일(auth.refresh-token-duration).
- DB에는 Refresh Token 해시만 저장하고 User를 직접 참조한다. 로그인마다 별도 토큰을 발급하지만 기기 정보나 세션을 관리하지 않는다.
- 토큰은 JSON으로 반환하고 Set-Cookie는 사용하지 않는다.

## 일반 API

`Authorization: Bearer <accessToken>`으로 JWT만 검사한다. AuthPrincipal은 userId와 role만 포함한다.
DB 사용자·Refresh Token을 조회하지 않는다. 역할 기반 경로 제한은 유지한다. 로그아웃·사용자 역할 변경은 기존 Access Token에 즉시 반영되지 않는다.

## 토큰 갱신

`POST /auth/refresh`, Content-Type: application/json. Access Token이나 쿠키는 필요하지 않다.

```json
{"refreshToken":"<opaque token>"}
```

성공 200:

```json
{"success":true,"message":"토큰이 갱신되었습니다.","data":{"accessToken":"<new JWT>"}}
```

형식 확인 → 해시 조회 → 토큰 존재·만료와 연결 User 확인 → Access Token만 발급.
Refresh Token은 교체하지 않으며 만료시간도 연장하지 않는다. 같은 토큰으로 반복 갱신할 수 있다.

## 로그아웃

`POST /auth/logout`, Content-Type: application/json. 본문은 갱신과 동일한 refreshToken 필드다. Access Token은 필요하지 않으며 만료된 Access Token이 있어도 처리한다.

형식 확인 → 해시 생성 → 해당 해시의 DB 행 삭제. 다른 토큰에는 영향을 주지 않는다.
이미 삭제된 토큰이나 형식이 올바른 미등록 토큰은 성공으로 처리한다. 만료된 토큰도 삭제할 수 있다.

```json
{"success":true,"message":"로그아웃 되었습니다."}
```

Access Token은 서버에서 폐기하지 않고 만료까지 유효하다. 클라이언트는 로그아웃 성공 시 보관한 두 토큰을 삭제한다.
락을 사용하지 않으므로 로그아웃과 겹쳐 이미 토큰을 읽은 갱신 요청은 Access Token을 발급할 수 있다.

## 검증과 오류

- 회원가입은 기존 역할·중복·입력 검증을 유지한다.
- 갱신/로그아웃의 refreshToken 누락·null·공백, 잘못된 JSON: 400 VALIDATION_FAILED.
- 토큰 형식 오류, 갱신 시 미등록·만료: 401 INVALID_REFRESH_TOKEN.
- JWT 만료: ACCESS_TOKEN_EXPIRED. JWT 위변조·잘못된 클레임: INVALID_ACCESS_TOKEN.
- 업무 오류는 기존 CustomException/ErrorCode/ErrorResponse로 통일한다. 기본 CORS 거절은 Spring의 403 응답을 사용한다.

## 프론트와 보안 설정

- 로그인 응답으로 두 토큰을 받고 갱신·로그아웃에 Refresh Token을 JSON으로 전달한다.
- credentials: include, CSRF 준비 API/헤더, Origin 필수 헤더는 사용하지 않는다.
- CORS는 브라우저 교차 출처 통신을 위해 유지하며 auth.allowed-origins를 설정한다. 쿠키 credential 허용은 끈다.
- 토큰 보관은 클라이언트 책임이다. 브라우저 JavaScript가 Refresh Token에 접근할 수 있어 HttpOnly 쿠키의 읽기 차단 효과는 없다.
- HTTPS로 전달하고 비밀번호·토큰을 로깅하지 않는다. Spring Security 기본 no-store 헤더를 유지한다.

## 기존 DB 전환

[Refresh Token User 연결 SQL](migrations/20260921_refresh_token_user.sql)을 애플리케이션 중지 및 DB 백업 후 1회 적용한다. 유효한 미사용 토큰을 이전하고 기존 rotation 이력·만료·폐기 토큰과 LoginSession 테이블을 제거한다. JPA ddl-auto:update만으로는 기존 데이터 및 컬럼 정리가 완료되지 않는다.
새 DB는 엔티티 구조로 생성할 수 있다. 이 SQL은 자동 실행되지 않으며 실제 DB 적용은 별도 작업이다.
