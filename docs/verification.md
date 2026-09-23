# 검증 상태

## 세션 없는 인증 구조

`./gradlew test bootJar` 성공: 단위·MVC 테스트 36개 통과, 실패 0, 건너뜀 0. PostgreSQL 테스트 소스 컴파일 및 실행 JAR 빌드 성공.

검증한 내용:

- 로그인 자격 증명, dummyPasswordHash 비교, 사용자에 연결된 Refresh Token 해시·만료시간 저장.
- JWT 서명·발급자·대상·필수 클레임·만료 검증, userId/role 추출.
- 일반 API에서 RefreshTokenRepository 및 AuthService를 호출하지 않음.
- 같은 Refresh Token 반복 갱신 시 DB 변경·만료 연장 없이 Access Token만 발급.
- 형식 오류·미등록·만료 토큰 거절, 만료 시각 경계 검사.
- 로그아웃은 지정한 해시만 삭제하며 반복 요청 성공.
- 로그인 두 토큰 JSON 반환, 갱신 Access Token만 반환, 쿠키 미사용.
- 갱신·로그아웃은 Access Token 없이 처리하며 만료된 Authorization 헤더에 영향을 받지 않음.
- 기존 회원가입 응답·필수 연락처·판매자 가게 미설정 정책 유지.
- 기본 CORS 허용 목록 유지, 쿠키 credentials 허용 제거.

PostgreSQL 통합 테스트는 로그인 해시/createdAt 저장, 반복 갱신, 만료 거절, 로그아웃 후 Refresh Token 삭제 및 Access Token 유효성을 확인하도록 변경했다.
Docker 미설치로 통합 테스트는 미실행이며 실제 DB 동작 검증을 완료했다고 간주하지 않는다.

기존 DB 전환 SQL: `docs/migrations/20260921_refresh_token_user.sql`. 실행하지 않았으며 실제 DB에 적용하기 전 백업과 애플리케이션 중지가 필요하다. 새 스키마 생성과 기존 스키마 전환은 별도 검증 대상이다.

실행 명령:

- `./gradlew test`: 단위·MVC 테스트
- `./gradlew postgresTest`: Docker 기반 PostgreSQL 테스트
- `./gradlew bootJar`: 실행 JAR 빌드

## JWT claim 정리

`./gradlew test`: 37개 통과. JWT의 6개 claim만 발급하는지와 각각의 필수 claim 누락·잘못된 역할의 거절을 검증했다. jti 제거에 맞춰 토큰 문자열의 매 발급 고유성을 요구하던 테스트를 제거했다. NullPointerException catch는 제거하고 sub/role 누락은 명시적으로 IllegalArgumentException으로 처리한다.

## JWT 인증 필터 정리

`./gradlew test`: 38개 통과. 인증 제외 경로 4개를 static final Set으로 관리한다. 컨텍스트 경로 유무에 따른 제외 처리 및 Authorization 헤더 누락 시 다음 필터로 넘기는 동작을 검증했다. 기존 MVC 테스트의 401/403 응답과 DB 조회 없는 JWT 인증도 통과했다. AuthPrincipal과 SecurityErrorHandler 구조는 유지했다. PostgreSQL 통합 테스트는 Docker 미설치로 미실행이다.
