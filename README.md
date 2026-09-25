# CardHub Backend

CardHub의 웹 우선·앱 호환 MVP REST API입니다. Java 21, Spring Boot 4.1,
PostgreSQL 16과 Flyway를 사용하는 모듈러 모놀리스이며 API와 worker는 같은
이미지를 서로 다른 프로세스로 실행합니다.

## 현재 구현 범위

- Google/Kakao/Naver OAuth2 기반 사용자·세션/JWT 골격
- 카드 카탈로그, 매물과 업로드 포트
- 장바구니, 가격 제안, 동일 판매자 단일·묶음 주문과 원자적 예약
- 결제·환불 포트와 복구 가능한 상태 모델
- 배송·구매확정, 항목별 거래·시세·리뷰
- 관심 상품, 최대 20개 one-shot 가격 알림, 인앱 알림 10종
- 최소 관리자 API와 PostgreSQL `SKIP LOCKED` 작업 worker

Toss Payments, SweetTracker, S3는 도메인 포트 뒤의 adapter 또는 안전한
stub으로 구성되어 있습니다. 개인정보는 로컬·테스트에서 AES-GCM, 운영에서
AWS KMS envelope encryption을 사용하며 `.env.example`에는 변수 이름만
제공합니다.

## 로컬 실행

필수 도구는 Java 21과 PostgreSQL 16입니다. Docker Compose를 사용할 수 있다면:

```bash
cp .env.example .env
docker compose up --build
```

API는 `http://localhost:8080`, readiness probe는
`/actuator/health/readiness`입니다. 로컬에서 애플리케이션만 실행할 때는:

```bash
./gradlew bootRun
```

OAuth를 실제로 확인할 때만 각 provider의 callback URI와 자격증명을 등록하고
`local,oauth` 프로필을 사용합니다. 토큰이나 외부 서비스 키를 저장소에
커밋하지 마세요.

Google 로컬 연동에는 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`과
`SPRING_PROFILES_ACTIVE=local,oauth`가 필요합니다. Google Console의 redirect
URI는 `http://localhost:8080/login/oauth2/code/google`이며, 프론트 성공 URL의
기본값은 `http://localhost:3000/auth/callback`입니다. 인증 흐름, 세션 계약과
기존 계정 재로그인 수정은
[`docs/google-oauth-integration.md`](docs/google-oauth-integration.md)에 정리되어
있습니다.

기본 허용 frontend origin은 `http://localhost:3000`입니다. Vite 등 다른 포트를
사용하거나 origin이 여러 개라면 `CORS_ALLOWED_ORIGINS`에 쉼표로 구분해
명시하고, 브라우저 요청에는 credentials를 포함해야 합니다.

## 검증

```bash
./gradlew check
```

CI는 Java 21에서 `check`와 `bootJar`를 실행하고 동일 SHA의 Docker 이미지도
빌드합니다. 단위 테스트는 빠른 피드백을 위해 격리되고, PostgreSQL 전용
migration·부분 인덱스·잠금 동작은 PostgreSQL 통합 테스트 대상으로 둡니다.

배포, migration, 장애 복구와 rollback 절차는 [`docs/runbook.md`](docs/runbook.md)에
정리되어 있습니다.

## 개발 진행 현황

2026-08-25까지 구현한 기능, 검증 결과, 알려진 제약과 P0/P1/P2 잔여
백로그는 [`docs/development-status-2026-08-25.md`](docs/development-status-2026-08-25.md)에
정리되어 있습니다. 이 문서는 코드 구현 완료와 실제 Staging·출시 검증 완료를
구분하는 현재 인수 기준입니다.

## 주요 API

모든 경로의 prefix는 `/api/v1`입니다.

- `/auth`, `/users/me`: 인증과 사용자
- `/catalog`, `/listings`, `/uploads`: 카탈로그·매물·이미지
- `/cart`, `/offers`, `/orders`, `/payments`: 구매 흐름
- `/shipments`, `/market`, `/reviews`: 배송·시세·리뷰
- `/favorites`, `/price-alerts`, `/notifications`: 관심·알림
- `/admin`: `ADMIN` 역할 전용 운영 API

응답은 `{ "data": ..., "meta": ... }` 형식을 사용하고 오류는 `code`,
`message`, `traceId`와 선택적 `fieldErrors`로 반환됩니다.

## Worker 계약

`scheduled_jobs`는 `dedupe_key`로 등록을 멱등화합니다. worker는 due job을
`FOR UPDATE SKIP LOCKED`로 짧게 lease한 뒤 트랜잭션 밖에서 handler를 실행하고,
실패 시 지수 backoff로 재시도합니다. 만료된 lease는 다시 `PENDING`으로
복구됩니다. 따라서 handler는 동일 작업이 재실행되어도 안전한 멱등 동작이어야
합니다. 새 작업은 `ScheduledJobHandler`를 구현하고 고유한 `jobType()`을
반환해야 합니다.

운영에서는 API 프로세스에 `CARDHUB_WORKER_ENABLED=false`, 별도 worker
프로세스에 `true`를 설정합니다.

## 개인정보 암호화

로컬·테스트 기본값은 `CIPHER_PROVIDER=aes`이며 `PII_ENCRYPTION_KEY`와
`PII_KEY_VERSION`이 필요합니다. 운영 프로필은 기본적으로
`CIPHER_PROVIDER=kms`이고 `KMS_KEY_ID`와 `KMS_REGION`(미지정 시
`AWS_REGION`)이 필수입니다. 애플리케이션 실행 역할에는 해당 KMS 키의
`kms:GenerateDataKey`와 `kms:Decrypt` 권한만 부여합니다.

KMS 모드는 민감정보마다 새 AES-256 data key를 발급해 로컬 AES-GCM으로
암호화합니다. DB에는 암호화된 data key, 무작위 IV와 ciphertext만 저장하며
평문 data key byte buffer는 사용 직후 최선의 범위에서 지웁니다. KMS 키를 교체하더라도 이전
ciphertext blob을 복호화할 권한은 데이터 보존 기간 동안 유지해야 합니다.

## 배포 원칙

- Flyway가 유일한 스키마 원본이며 적용된 migration은 수정하지 않습니다.
- 동일 이미지 SHA를 staging에서 검증한 뒤 production에 승격합니다.
- migration job을 먼저 한 번 실행하고 API/worker를 순차적으로 교체합니다.
- DB 변경은 이전 애플리케이션과 공존할 수 있는 additive/forward-only 방식으로 진행합니다.
- 운영 DB는 백업과 PITR을 활성화하고, 로그에는 토큰·결제키·주소 등 민감정보를 남기지 않습니다.
