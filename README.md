# DeskIn

DeskIn은 데스크테리어(desk interior) 상품을 판매하는 멀티셀러 커머스 플랫폼입니다. 상품 탐색부터 주문·결제·배송, 판매자별 정산까지 처리하는 서비스를 목표로 합니다.

단순 CRUD를 넘어 다음 과제를 안정적으로 구현하는 것이 핵심 목표입니다.

- 동시 주문 시 재고 정합성 보장
- 중복 결제 방지
- 외부 PG(Toss Payments)와 내부 결제 상태의 일치
- 판매자별 정산 처리

커머스 도메인은 개발 예정이며, Auth 및 공통 인증·인가 구현 상태는 아래에 별도로 정리합니다.

## Tech Stack

- Java 17 / Spring Boot 3.3
- Spring Web, Spring Data JPA, Spring Security, Spring Batch
- PostgreSQL
- JWT (jjwt)
- Toss Payments (결제 PG)
- Gradle

## 팀 구성 및 담당 범위

| 담당 | 기능 |
| --- | --- |
| 윤진 | 회원 인증·인가, 상품 조회, 주문 생성·조회 및 재고 동시성 제어, 판매자 상품 관리, 결제 준비·승인·조회 |
| 다희 | 환불, 판매자 주문·판매 내역 조회, 정산, 원장 대사, Webhook, 관리자 기능, 배치 |

공통 인증 규약은 팀원이 각자 담당 기능에서 사용할 수 있도록 먼저 정리하고 공유합니다. 본인 주문 조회·본인 상품 수정 등의 소유권 검사는 각 도메인에서 구현합니다.

## 현재 개발 단계: Auth 및 공통 인증·인가 기반

다음 인증 기능을 구현했습니다.

- 아이디·비밀번호 기반 회원가입·로그인, Refresh Token 갱신, 현재 세션 로그아웃
- BUYER / SELLER / ADMIN 단일 역할과 역할별 API 접근 제한
- 판매자 가입 시 User·Seller 동시 생성
- JWT Access Token 15분, 기기별 로그인 세션과 Refresh Token 7일
- 갱신 토큰 교체·재사용 감지, 로그아웃 후 Refresh Token 차단(Access Token은 만료까지 유효)
- JWT 전용 인증 필터·기본 CORS, 공통 principal 및 인증 오류 응답

Docker 없이 실행 가능한 단위·MVC 테스트와 JAR 빌드를 검증했습니다. **PostgreSQL 통합 테스트는 Docker 부재로 미실행**이며, 코드는 작성하고 컴파일했습니다. 실제 PostgreSQL 기동·동시성 검증 완료를 의미하지 않습니다.

상세 내용: [인증 API 계약](docs/auth-api.md), [검증 상태](docs/verification.md), [개발·Git 규칙](CONTRIBUTING.md).

## 패키지 구조 (DDD 기반)

```
com.deskin
 ├─ auth         # 회원가입/로그인/로그아웃, User 엔티티
 ├─ product      # 상품 등록/조회/수정/상태변경 (공개 조회 + 판매자 CRUD)
 ├─ order        # OrderGroup / Order / OrderItem
 ├─ seller       # 판매자 고유 정보, 판매 내역 집계
 ├─ payment      # Toss 결제 준비/승인/환불
 ├─ settlement   # 월 정산
 ├─ ledger       # 대사용 거래 원장 (append-only)
 ├─ webhook      # Toss Webhook 수신
 ├─ admin        # 관리자 조회/처리 (자체 엔티티 없이 각 도메인 서비스 재사용)
 ├─ batch        # Spring Batch (배송 상태 전환, 정산 계산)
 └─ global       # auth(JWT), config, exception, 공통 entity(BaseEntity)
```

컨벤션:
- 도메인별 패키지 = `controller / service / service.impl / repository / dto / entity`
- Service는 인터페이스 + `impl` 구현체로 분리, 모든 구현체는 `@Override` 명시
- DTO는 `record`로 작성, 요청/응답 분리 (`XxxRequest` / `XxxResponse`)
- 인터페이스 구조를 임의로 추가하지 않고 합의된 구조를 따를 것

## 로컬 실행

1. PostgreSQL에 `deskin` 데이터베이스를 생성합니다. 테스트 컨테이너 기준 버전은 PostgreSQL 16입니다.
2. 로컬 환경변수를 설정합니다. 비밀 값은 저장소에 커밋하지 않습니다.

   ```text
   DB_URL=jdbc:postgresql://localhost:5432/deskin
   DB_USERNAME=postgres
   DB_PASSWORD=...
   JWT_SECRET=최소 32바이트의 무작위 비밀 값
   AUTH_ALLOWED_ORIGINS=http://localhost:3000
   ```

   로컬 기본 DB 주소·사용자는 위와 같으며, JWT 비밀키에는 기본값이 없습니다. 여러 Origin은 쉼표로 구분합니다. `prod` 프로필은 DB_URL·DB_USERNAME·DB_PASSWORD·JWT_SECRET·AUTH_ALLOWED_ORIGINS를 필수로 주입하며 HTTPS 쿠키를 사용합니다. 기존 Toss 설정을 위해 prod에는 TOSS_SECRET_KEY·TOSS_CLIENT_KEY도 지정합니다.

3. 실행합니다.

   ```bash
   ./gradlew bootRun
   ```

운영 주소는 프론트와 API가 같은 사이트인 구성을 전제로 합니다. 다른 사이트 배포가 필요하면 쿠키 정책을 먼저 협의합니다. 현재 JPA 스키마 설정은 기존 개발 방식인 `ddl-auto: update`를 유지합니다.

## API 명세

| Method | Endpoint | 인증 방식 |
| --- | --- | --- |
| POST | `/auth/signup` | JWT 불필요 |
| POST | `/auth/login` | JWT 불필요 |
| POST | `/auth/refresh` | Refresh Token 쿠키 + DB 세션 확인 |
| POST | `/auth/logout` | Bearer Access Token + DB 세션 확인 |

첨부 명세의 로그인 인증 필요 표시는 불필요로, 로그아웃은 Bearer 헤더 기준 필요로 정정했습니다. 현재 판매자 storeName과 토큰 갱신 API를 제공하며, 별도 CSRF API는 제거했습니다. 외부 Notion 문서는 자동 수정하지 않았습니다.

공개 상품 목록·숫자 ID 상세 GET은 비회원도 접근할 수 있습니다. 주문·결제는 BUYER, `/seller/**`는 SELLER, `/admin/**`는 ADMIN만 접근합니다. 권한 상속은 없으며 Webhook은 별도 검증 정책 확정 전까지 차단합니다. 해당 도메인 API의 실제 비즈니스 구현은 후속 작업입니다.

## 테스트

```bash
./gradlew test                  # Docker 없는 단위·MVC 테스트
./gradlew compileTestJava       # PostgreSQL 통합 테스트 포함 컴파일
./gradlew postgresTest          # Docker 환경에서 실제 PostgreSQL 통합 테스트
./gradlew bootJar               # 실행 JAR 빌드
```

`test`와 기본 `check`는 Docker 테스트를 포함하지 않습니다. DB 검증 완료 여부는 `postgresTest`를 별도로 실행해 확인합니다. Docker가 없을 때 다른 DB로 대체하거나 자동으로 성공 처리하지 않습니다.

## 참고

- 장바구니는 서버에 저장하지 않고 프론트엔드 Zustand 상태로만 관리합니다. 이 저장소에는 관련 테이블/엔티티가 없습니다.
- 사용자 정보 조회, 이메일 인증, 소셜 로그인, 비밀번호 재설정, 역할 전환, 판매자 사업자·정산 정보는 후속 범위입니다.
- 본인 주문·본인 상품 등의 소유권 검사는 각 도메인 서비스에서 구현합니다.
