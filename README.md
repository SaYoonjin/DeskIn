# DestIn

데스크테리어(desk interior) 상품을 다루는 멀티셀러 마켓플레이스. 결제 정합성(payment integrity), 정산, 원장 대사(ledger reconciliation)를 중심으로 백엔드 역량을 보여주기 위한 포트폴리오 프로젝트입니다.

## Tech Stack

- Java 17 / Spring Boot 3.3
- Spring Web, Spring Data JPA, Spring Security, Spring Batch
- MySQL
- JWT (jjwt)
- Toss Payments (결제 PG)
- Gradle

## 팀 구성 및 도메인 오너십

| 담당 | 도메인 |
| --- | --- |
| 다희 | auth, product, order, seller |
| 팀원 | payment, settlement, ledger, webhook |
| 공통 | admin, batch, global |

## 패키지 구조 (DDD 기반)

```
com.destin
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

1. MySQL에 `destin` 데이터베이스 생성
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

## 참고

- 장바구니는 서버에 저장하지 않고 프론트엔드 Zustand 상태로만 관리합니다. 이 저장소에는 관련 테이블/엔티티가 없습니다.
- 현재 커밋은 API 명세에 맞춘 스켈레톤(컨트롤러/서비스 인터페이스/엔티티/DTO)이며, 비즈니스 로직은 `TODO`로 표시된 부분부터 순차 구현 예정입니다.
