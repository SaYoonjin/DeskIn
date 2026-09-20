# 검증 상태

DB 기준: PostgreSQL. 통합 테스트는 실제 PostgreSQL Testcontainers를 사용한다.

- `./gradlew test`: Docker 없이 단위 테스트 실행.
- `./gradlew postgresTest`: Docker가 필요한 PostgreSQL 통합 테스트 실행.
- `./gradlew compileTestJava`: 통합 테스트를 실행하지 않고 컴파일 검증.

현재 작업 환경에는 Docker 명령과 소켓이 없다. PostgreSQL 통합 테스트는 **작성했으나 미실행**이며, 실행 성공으로 간주하지 않는다. 사용자가 허용한 절차에 따라 단위 테스트 검증 후 기능별로 로컬 커밋한다. 배포 전 Docker 환경에서 `postgresTest`를 실행해야 한다.

기존 다른 DB 기반 테스트 결과는 PostgreSQL 검증 결과가 아니다.
