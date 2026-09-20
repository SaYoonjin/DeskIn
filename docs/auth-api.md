# 인증 API 계약

공통 개발·커밋·push·질문 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

## 회원가입

`POST /auth/signup`: JWT 불필요, CSRF 헤더 필요. 성공 시 201이며 자동 로그인하지 않는다.

요청: `id`, `password`, `name`, `role`, `email` 필수. `phone` 선택. SELLER는 `storeName` 필수이고 BUYER에는 허용하지 않는다.

```json
{"id":"buyer_1","password":"Password123!","name":"홍길동","role":"BUYER","email":"buyer@example.com"}
```

```json
{"success":true,"message":"회원가입이 완료되었습니다.","data":{"userId":12,"id":"buyer_1","role":"BUYER"}}
```

- 로그인 문자열 `id`와 내부 숫자 `userId`는 다르다.
- 아이디는 영문·숫자·밑줄 4~30자이며 공백 제거 후 소문자로 정규화한다. 이메일도 공백 제거 후 소문자로 정규화한다.
- 아이디·이메일은 각각 중복될 수 없다. 사전 조회 이후의 DB 충돌도 409로 응답한다.
- 비밀번호는 10~64자, UTF-8 기준 72바이트 이하이며 원문을 변환하지 않는다.
- 이름 1~50자, 이메일 최대 254자, 상점명 1~100자. 상점명 중복은 허용한다.
- 연락처는 공백·하이픈을 제거하고 선택적인 선행 + 및 숫자 8~15자리를 허용한다. 빈 값은 null이다.
- 공개 가입 역할은 BUYER·SELLER뿐이다. 판매자 User와 Seller는 한 트랜잭션으로 생성한다.

## 오류

## CSRF 토큰

`GET /auth/csrf`는 JWT 없이 호출한다. `data.csrfToken`과 `data.headerName`을 반환하고 HttpOnly XSRF-TOKEN 쿠키를 설정한다. 가입·로그인·갱신·로그아웃 요청은 쿠키와 `X-XSRF-TOKEN` 헤더를 함께 전달한다. 브라우저 fetch에는 `credentials: 'include'`가 필요하다.

## 공통 오류 형식

```json
{"success":false,"message":"요청 값이 올바르지 않습니다.","error":{"code":"VALIDATION_FAILED","fieldErrors":[]}}
```

400 입력 오류, 401 인증 오류, 403 권한·CSRF 오류, 409 중복 아이디·이메일. ErrorCode가 코드·HTTP 상태·기본 메시지를 관리한다.

## 검증 실행

- `./gradlew test`: Docker 없이 실행하는 단위 테스트.
- `./gradlew postgresTest`: Docker가 필요한 Testcontainers PostgreSQL 테스트. Docker가 없으면 실패하며, 자동으로 건너뛰지 않는다.
- Docker가 없는 환경에서는 단위 테스트를 통과하고 PostgreSQL 통합 테스트의 미실행 상태를 기록한 뒤 기능별 로컬 커밋한다. PostgreSQL 테스트를 다른 DB로 대체하지 않는다.
