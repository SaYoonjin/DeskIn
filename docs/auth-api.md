# 인증 API 계약

공통 개발·커밋·push·질문 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

## 구현 구조와 책임

인증 코드는 `com.deskin.auth` 도메인에 모은다. `global/auth` 패키지는 사용하지 않는다.

- `auth/controller`: 요청 바인딩과 `@Valid` 입력 검증, 서비스 호출, `ApiResponse` 및 쿠키 응답 포장을 담당한다.
- `auth/service`, `auth/service/impl`: 가입 조건·중복·자격 증명·Refresh Token·세션 검증과 DB 변경, 토큰 발급을 담당한다. 트랜잭션은 서비스에서 관리한다.
- `auth/token`: `JwtTokenProvider`가 Access Token을 발급·검증하고, `OpaqueTokenProvider`가 Refresh Token을 생성·해시 처리한다.
- `auth/security`: `AuthPrincipal`, `JwtAuthenticationFilter`, `RefreshCookieWriter`, `SecurityErrorHandler`가 요청 인증과 보안 응답을 담당한다.
- `global/config/SecurityConfig`: 경로별 권한, 브라우저 연동용 CORS 설정과 인증 필터를 연결한다.

| 구현된 API | 컨트롤러 책임 | 검증·처리 담당 |
| --- | --- | --- |
| 회원가입 | 요청 DTO 검증, 201 응답 포장 | `AuthService.createUser`: 역할·판매자 정보·중복 검증 및 사용자 생성 |
| 로그인 | 요청 DTO 검증, Access Token JSON과 Refresh Token 쿠키 응답 포장 | `AuthService.authenticateUser`: 자격 증명 검증, 세션 생성 및 토큰 발급 |
| 토큰 갱신 | 쿠키 수신, 새 토큰 응답 포장 | `AuthService.refreshTokens`: 토큰·세션 검증, 재사용 감지 및 토큰 교체 |
| 로그아웃 | 인증된 사용자 전달, 성공 후 쿠키 삭제와 응답 포장 | `AuthService.revokeSession`: 세션 검증 및 폐기 |

쿠키는 HTTP 응답에 해당하므로 컨트롤러가 `RefreshCookieWriter`를 사용하며 서비스에는 `HttpServletResponse`를 전달하지 않는다. 로그인·갱신의 응답 포장은 `AuthController.createTokenResponse`로 통일한다.

현재 상품·주문·결제·판매자·관리자·정산·웹훅 컨트롤러는 빈 파일이며 구현된 API가 아니다. 후속 구현에서도 컨트롤러는 요청 검증과 응답 포장을, 서비스는 세부 검증과 처리를 담당한다.

## 회원가입

`POST /auth/signup`: JWT 불필요. 성공 시 201이며 자동 로그인하지 않는다.

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

## 로그인

`POST /auth/login`: JWT 불필요. 요청은 `id`, `password`다. 성공 시 200을 반환한다.

```json
{"success":true,"message":"로그인 되었습니다.","data":{"accessToken":"<JWT>","userId":12,"role":"BUYER"}}
```

- Access Token은 메모리에 보관하고 `Authorization: Bearer <JWT>`로 전달한다. 수명은 15분이며 일반 API에서는 JWT 서명·만료·클레임만 검증한다.
- Refresh Token은 본문이 아닌 `refreshToken` HttpOnly 쿠키로 전달한다. 운영에서는 Secure, SameSite=Lax, Path=/auth이며 Domain은 지정하지 않는다.
- 세션은 최초 로그인부터 7일간 유지한다. 로그인마다 다른 세션을 만들며 기존 기기를 로그아웃시키지 않는다.
- DB에는 Refresh Token 해시만 저장한다. 없는 아이디와 비밀번호 오류는 동일한 INVALID_CREDENTIALS 응답이다.
- 일반 API 인증 시 DB 세션은 조회하지 않는다. Refresh/Logout에서만 서비스가 DB 세션의 사용자·역할·만료·폐기 상태를 확인한다. JWT 비밀키는 최소 32바이트이며 환경변수 JWT_SECRET으로 주입한다.

## 로그아웃

`POST /auth/logout`: 유효한 Bearer Access Token이 필요하고 본문은 없다. JWT의 sessionId로 현재 세션을 폐기하고 Refresh Token 쿠키를 삭제한다.

```json
{"success":true,"message":"로그아웃 되었습니다."}
```

- 성공은 200이며 data 필드는 생략한다. DB 세션이 이미 폐기되었거나 Access Token이 만료된 경우 401이다.
- Access Token 만료 시 갱신 후 로그아웃한다. 현재 기기의 세션만 종료하고 다른 기기에는 영향을 주지 않는다.
- 갱신과 같은 행 잠금을 사용한다. 로그아웃 후 Refresh Token은 거절된다. 기존 Access Token은 만료까지 일반 API에서 사용할 수 있으므로 프론트는 로그아웃 성공 시 메모리의 Access Token을 삭제한다.

## 토큰 갱신

`POST /auth/refresh`: 본문과 Access Token은 필요하지 않다. Refresh Token 쿠키와 연결된 LoginSession을 DB에서 확인한다. 성공 시 200과 로그인과 같은 data, 메시지 `토큰이 갱신되었습니다.`를 반환하고 Refresh Token 쿠키를 교체한다.

- 사용한 토큰을 보관하며 재사용 감지 시 해당 세션 전체를 폐기한다. 오류 응답이어도 폐기는 커밋된다.
- 세션 행 잠금을 먼저 획득한 다음 토큰의 최신 사용 상태를 읽는다. 세션 만료 시점은 연장하지 않는다.
- 프론트는 여러 탭을 포함하여 같은 세션의 갱신 요청을 직렬화해야 한다. 응답 유실 후 이전 토큰 재시도는 재로그인을 요구할 수 있다.
- 누락·만료·폐기·잘못된 토큰은 INVALID_REFRESH_TOKEN, 재사용 감지는 REFRESH_TOKEN_REUSED로 401을 반환한다.

## 최소 인증 구조

- Access Token: 15분 JWT, Authorization Bearer 헤더. 일반 API는 JWT만 검사하며 인증을 위한 DB 세션 조회는 없다.
- Refresh Token: HttpOnly 쿠키, DB LoginSession과 연결. 갱신·로그아웃 서비스에서 세션 확인.
- CSRF 토큰·API·쿠키, Origin 검증 필터, SessionAuthenticator, 커스텀 CORS 필터는 제거했다.
- JWT 인증 필터 하나와 Spring Security 기본 CORS 설정만 사용한다. 별도 Origin 헤더 요구는 없다. 브라우저의 교차 출처 요청을 위해 auth.allowed-origins와 credentials 설정은 유지한다.
- Refresh Token 쿠키만 사용한다. HttpOnly·Secure(운영)·SameSite=Lax·Path=/auth 속성은 유지한다.
- 로그아웃이나 역할 변경은 기존 Access Token에 즉시 반영되지 않으며 만료 또는 새 토큰 발급 후 반영된다.

## 공통 오류 형식

```json
{"success":false,"message":"요청 값이 올바르지 않습니다.","error":{"code":"VALIDATION_FAILED","fieldErrors":[]}}
```

400 입력 오류, 401 인증 오류, 403 권한 오류, 409 중복 아이디·이메일. ErrorCode가 코드·HTTP 상태·기본 메시지를 관리한다. Spring Security 기본 CORS 거절 응답은 프레임워크의 403 응답을 사용한다.

## 검증 실행

- `./gradlew test`: Docker 없이 실행하는 단위 테스트.
- `./gradlew postgresTest`: Docker가 필요한 Testcontainers PostgreSQL 테스트. Docker가 없으면 실패하며, 자동으로 건너뛰지 않는다.
- Docker가 없는 환경에서는 단위 테스트를 통과하고 PostgreSQL 통합 테스트의 미실행 상태를 기록한 뒤 기능별 로컬 커밋한다. PostgreSQL 테스트를 다른 DB로 대체하지 않는다.


## 도메인 개발자용 인증 계약

```java
import com.deskin.auth.security.AuthPrincipal;

@GetMapping("/orders/{orderId}")
public ApiResponse<OrderResponse> getOrder(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long orderId) {
    return ApiResponse.success("주문이 조회되었습니다.", orderService.getOrder(principal.userId(), orderId));
}
```

위 코드는 후속 도메인 구현을 위한 사용 예시다. 실제 주문 구현을 추가한 것은 아니다.

- `AuthPrincipal`은 userId, role, sessionId를 제공한다. JWT 검증을 통과한 값이며 일반 API에서 DB 세션의 현재 상태를 보장하지 않는다.
- 판매자 ID는 `SellerRepository.findByUserUserId(principal.userId())`로 조회한다.
- 요청 본문에 전달된 사용자·판매자 ID를 소유권 근거로 사용하지 않는다.
- 역할 허용과 소유권 검사는 별개다. 각 도메인 서비스에서 본인 데이터인지 검증한다.
- BUYER는 `/orders/**`, `/payments/**`, SELLER는 `/seller/**`, ADMIN은 `/admin/**`에만 해당 역할로 접근한다. ADMIN도 구매자·판매자 권한을 상속하지 않는다.
- 공개 조회는 GET `/products`, GET `/products/{숫자 ID}`만 허용한다. 나머지 미등록 경로와 Webhook은 기본 차단한다.

## 프론트 요청 순서

1. 가입·로그인은 credentials를 포함하여 바로 호출한다. 별도 CSRF 준비 요청은 없다.
2. 로그인 응답의 Access Token을 메모리에 보관하고 도메인 요청에 Bearer 헤더로 전달한다.
3. 새로고침 또는 ACCESS_TOKEN_EXPIRED 발생 시 `/auth/refresh`를 credentials 포함으로 호출한다. 쿠키는 브라우저가 전달한다.
4. 여러 탭의 갱신은 하나씩 수행한다. 갱신 401은 반복 재시도하지 않고 메모리 토큰을 지운 뒤 재로그인한다.
5. 로그아웃은 유효한 Access Token과 credentials를 포함해 요청한다. 성공 후 메모리 토큰을 지운다.

운영에서는 같은 사이트와 HTTPS를 전제로 한다. 로그인·갱신 응답은 캐시하지 않으며 비밀번호·토큰을 로깅하지 않는다.
