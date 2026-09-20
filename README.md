# DeskIn

DeskIn은 데스크테리어(desk interior) 상품을 판매하는 멀티셀러 커머스 플랫폼입니다. 상품 탐색부터 주문·결제·배송, 판매자별 정산까지 처리하는 서비스를 목표로 합니다.

단순 CRUD를 넘어 다음 과제를 안정적으로 구현하는 것이 핵심 목표입니다.

- 동시 주문 시 재고 정합성 보장
- 중복 결제 방지
- 외부 PG(Toss Payments)와 내부 결제 상태의 일치
- 판매자별 정산 처리

아래 기능과 구조는 구현 목표 및 개발 계획이며, 구현이 완료된 기능을 의미하지 않습니다.

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

첫 작업으로 다음 범위를 구현할 예정이며, 관련 GitHub Issue는 생성된 상태입니다.

- 회원가입·로그인·로그아웃 API
- Spring Security와 JWT 기반 토큰 발급·검증
- 각 도메인에서 일관되게 사용할 로그인 사용자 ID와 역할 제공
- 역할별 API 접근 제어 및 공통 인증·인가 오류 응답
- 관련 단위·통합 테스트와 팀원용 인증 설정·사용 방법 안내

현재 사용자 정보는 내부 공통 인증 객체로 제공하며, 별도의 사용자 정보 조회 API는 이번 범위에 포함하지 않습니다.

인증 정책과 공통 계약을 먼저 확정한 뒤 구현합니다. 로그인 식별자·가입 필수 정보·비밀번호 조건, 역할 부여 및 판매자 전환 방식, User와 Seller의 연결, 토큰 전달 방식·유효기간·Refresh Token 도입 여부, 로그아웃 시 토큰 무효화 방식, 요청·응답 및 오류 형식은 아직 확정하지 않았습니다. 현재 설정의 Access Token 유효기간 1시간도 유지 여부를 결정할 예정입니다.

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

아래는 구현 진행 시 사용할 로컬 실행 절차입니다. 현재 인증 관련 주요 Java 파일은 비어 있는 초기 단계이며, 애플리케이션 실행과 인증 동작 검증이 완료된 상태는 아닙니다.

1. PostgreSQL에 `deskin` 데이터베이스 생성
2. 환경변수 설정 (또는 `application-local.yml`로 오버라이드)

   ```
   DB_PASSWORD=...
   JWT_SECRET=...
   TOSS_SECRET_KEY=...
   TOSS_CLIENT_KEY=...
   ```

3. 실행

   ```bash
   ./gradlew bootRun
   ```

## API 명세

Notion API 명세서 기준으로 구현합니다. (auth / seller / products / orders / payments / webhooks / admin)

현재 명세는 개발 계획이며, 상세 요청·응답은 각 기능의 구체적인 개발을 시작할 때 확정합니다. API 변경이 필요하면 윤진에게 먼저 알리고, 팀원과 공유한 뒤 명세에 반영합니다.

Auth API의 현재 기준은 다음과 같습니다. 첨부 명세의 로그인 “인증 필요” 표기는 JWT 인증 여부 기준으로 수정이 필요합니다.

| Method | Endpoint | JWT 인증 필요 여부 |
| --- | --- | --- |
| POST | `/auth/signup` | 불필요 |
| POST | `/auth/login` | 불필요 |
| POST | `/auth/logout` | 토큰 무효화 정책 확정 후 결정 |

공개 상품 조회는 비인증 접근을 허용하고, 주문·결제 등 보호 API에는 인증을 요구할 계획입니다. 판매자·관리자 API에는 역할별 접근 제한을 적용합니다. Webhook의 사용자 JWT 예외 경로와 별도 요청 검증 방식은 담당자와 협의합니다.

## 참고

- 장바구니는 서버에 저장하지 않고 프론트엔드 Zustand 상태로만 관리합니다. 이 저장소에는 관련 테이블/엔티티가 없습니다.
- 현재 프로젝트는 초기 구조를 준비한 단계이며, 기능은 정책과 공통 계약을 확정한 후 순차적으로 구현·검증할 예정입니다.
