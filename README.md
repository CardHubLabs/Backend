# CardHub Backend

> 트레이딩 카드의 탐색부터 거래 완료까지 연결하는 카드 거래 플랫폼 백엔드

CardHub는 포켓몬·원피스 등 트레이딩 카드의 종류와 상태를 기준으로 매물을 찾고, 가격 제안부터 주문·결제·배송·구매확정까지 진행할 수 있도록 설계한 서비스입니다. 이 저장소의 백엔드는 거래 과정에서 발생할 수 있는 **동시 주문, 결제 응답 유실, 배송 상태 변경**을 고려한 REST API와 비동기 작업을 제공합니다.

**프로젝트 상태:** MVP 기능 코드 구현 단계입니다. 아래 설명은 [`feat/google-oauth-integration` 브랜치](https://github.com/CardHubLabs/Backend/tree/feat/google-oauth-integration)를 기준으로 합니다. 기본 브랜치에는 초기 프로젝트 골격이 있으며, 실제 서비스 연동과 출시 전 검증은 진행이 필요합니다.

## 주요 기능

| 영역 | 기능 |
| --- | --- |
| 인증·사용자 | Google, Kakao, Naver OAuth2 기반 소셜 로그인, JWT와 refresh token 재발급, 사용자 정보 관리 |
| 카드·매물 | 게임·세트·카드·Variant 카탈로그, 다국어 검색, 이미지 업로드, 매물 등록·검색·관리 |
| 거래 | 장바구니, 가격 제안과 역제안, 단일 주문 및 동일 판매자의 묶음 주문 |
| 결제·배송 | 결제 승인·환불, 배송 추적, 구매확정 및 항목별 거래 기록 |
| 탐색·알림 | 카드 Variant 관심 등록, 가격 하락 알림, 인앱 알림, 거래 기반 시세 집계 |
| 운영 | 매물 차단, 사용자 관리, 결제 복구, 배송 수동 처리, 관리자 감사 로그 |

## 거래 흐름

```text
카드 탐색 → 매물 등록·검색 → 장바구니 또는 가격 제안
    → 주문 및 매물 예약 → 결제 → 배송 → 구매확정 → 거래 기록·시세·리뷰
```

주문 시 상품 가격, 카드 상태, 배송비와 배송지 등을 스냅샷으로 보존합니다. 묶음 주문은 같은 판매자의 매물만 허용하며, 구매확정 시 주문 항목별 거래 기록을 생성합니다.

## 기술적 특징

### 동시 주문과 매물 예약

같은 매물에 주문이 동시에 들어올 때 중복 판매를 막기 위해 PostgreSQL 행 잠금과 조건부 상태 변경을 사용합니다. 여러 매물을 주문할 때는 정해진 순서로 잠그고, 하나라도 예약하지 못하면 주문과 전체 예약을 함께 롤백합니다. 구매자와 판매자의 활성 상태도 잠금 후 확인합니다. [예약 구현](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/src/main/java/com/kant/cardhub/order/infrastructure/JpaListingReservationAdapter.java)

### 결제 응답 유실 복구

결제 승인 요청을 DB 준비, 외부 API 호출, 결과 확정 단계로 나눠 외부 호출 중 DB 트랜잭션을 유지하지 않도록 구성했습니다. 승인 결과를 알 수 없는 경우 `UNKNOWN` 상태로 남기고 매물 예약을 유지하며, worker가 결제 제공자의 상태를 재확인합니다. 멱등 키와 주문번호·결제 금액 검증으로 중복 요청과 불일치도 처리합니다. [결제 서비스](https://github.com/CardHubLabs/Backend/tree/feat/google-oauth-integration/src/main/java/com/kant/cardhub/payment)

### PostgreSQL 기반 작업 처리

별도 메시지 브로커 없이 `scheduled_jobs` 테이블과 `FOR UPDATE SKIP LOCKED`를 이용해 여러 worker가 작업을 나눠 가져옵니다. 중복 등록 방지 키, lease 만료 복구와 재시도를 통해 결제 복구, 예약 만료, 배송 추적 등의 작업을 처리합니다. 작업 handler는 재실행을 고려해 멱등하게 구현해야 합니다. [Worker 구현](https://github.com/CardHubLabs/Backend/tree/feat/google-oauth-integration/src/main/java/com/kant/cardhub/worker)

### 인증과 개인정보 보호

Refresh token 원문 대신 해시를 저장하고 재발급 시 토큰을 교체합니다. 쿠키 기반 재발급·로그아웃에는 CSRF 방어를 적용합니다. 배송지 등 민감정보는 로컬에서 AES-GCM, 운영 설정에서 AWS KMS 기반 envelope encryption을 사용하도록 구성했습니다. [Google OAuth 연동 문서](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/google-oauth-integration.md)

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language & Framework | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16, Flyway |
| API & Test | REST API, OpenAPI / Swagger UI, JUnit, Testcontainers, JaCoCo |
| Infrastructure | Docker, Docker Compose, GitHub Actions, AWS S3 / KMS |
| External integration | Google·Kakao·Naver OAuth2, Toss Payments, SweetTracker |

외부 서비스는 포트와 adapter로 분리했으며 로컬에서 사용할 수 있는 stub도 제공합니다. 실제 외부 서비스 연동 및 운영 환경 검증 상태는 아래 문서를 참고하세요.

## 구조

도메인별 패키지 안에서 API, application, domain, infrastructure를 분리한 모듈러 모놀리스입니다. API와 worker는 같은 이미지를 사용하며 별도 프로세스로 실행합니다.

```text
클라이언트
    │
    ▼
Spring Boot API ───── 외부 서비스 adapter (결제·배송·스토리지)
    │
    ▼
PostgreSQL ◀──────── Spring Boot Worker
  ├─ 거래 데이터        └─ 만료·복구·배송 추적·시세 집계
  └─ scheduled_jobs
```

핵심 패키지: `auth`, `catalog`, `listing`, `cart`, `offer`, `order`, `payment`, `shipment`, `trade`, `market`, `notification`, `worker`

## 로컬 실행

아래 명령은 **구현 브랜치**를 기준으로 합니다. Docker와 Docker Compose가 필요합니다.

```bash
git clone https://github.com/CardHubLabs/Backend.git
cd Backend
git checkout feat/google-oauth-integration
cp .env.example .env
docker compose up --build
```

- API: `http://localhost:8080`
- 상태 확인: `http://localhost:8080/actuator/health/readiness`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

기본 Compose 설정은 로컬 프로필을 사용합니다. 실제 OAuth 로그인에는 별도 provider 자격증명과 `local,oauth` 프로필이 필요합니다. 환경변수는 [`.env.example`](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/.env.example)과 [OAuth 설정 문서](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/google-oauth-integration.md)를 확인하세요.

## 테스트와 검증

```bash
./gradlew check
```

단위·통합 테스트와 JaCoCo 리포트를 실행합니다. PostgreSQL Testcontainers 테스트에는 Docker가 필요합니다. GitHub Actions는 Java 21에서 `check`와 `bootJar`를 실행하고 Docker 이미지를 빌드하도록 구성되어 있습니다.

2026-09-02 개발 기록에는 PostgreSQL migration·동시 예약 테스트를 포함한 **123개 테스트 통과**가 기록되어 있습니다. 이 결과는 해당 시점의 로컬 검증이며 현재 배포 상태나 외부 서비스 연동 완료를 의미하지 않습니다. [검증 결과와 남은 과제](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/development-status-2026-09-02.md)

## API와 추가 문서

API prefix는 `/api/v1`입니다. 주요 경로는 `/auth`, `/users/me`, `/catalog`, `/listings`, `/cart`, `/offers`, `/orders`, `/payments`, `/shipments`, `/market`, `/reviews`, `/favorites`, `/price-alerts`, `/notifications`, `/admin`입니다.

- [개발 현황과 검증 결과](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/development-status-2026-09-02.md)
- [Google OAuth 인증 흐름과 로컬 설정](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/google-oauth-integration.md)
- [배포·마이그레이션·장애 복구 runbook](https://github.com/CardHubLabs/Backend/blob/feat/google-oauth-integration/docs/runbook.md)
