# CardHub 백엔드 개발 기록 및 잔여 백로그 — 2026-08-25

- 기준 문서: [CardHub MVP Implementation Roadmap](https://app.notion.com/p/3c4784ed5731804ebabac9a41bbf1f87?pvs=204)
- Notion 진행 기록: [2026-08-25 개발 기록 및 잔여 백로그](https://app.notion.com/p/3c6784ed5731811aa89df20eddc35016?pvs=204)
- 대상: `cardhub` Spring Boot 백엔드 저장소
- 상태: 코드 레벨 MVP 구현 완료, 통합·Staging·출시 검증 진행 전
- 주의: 현재 변경사항은 working tree에 있으며 아직 기준 커밋으로 고정하지 않았다.

## 1. 요약

기존 Spring Boot 골격에서 기능별 모듈러 모놀리스 구조로 CardHub MVP 백엔드를
구현했다. 인증, 카탈로그, 이미지, 매물, 장바구니, 가격 제안, 묶음 주문,
결제·환불 복구, 배송·자동 구매확정, 거래·시세·리뷰, 관심·가격 알림,
인앱 알림, 관리자와 worker까지 코드와 Flyway migration이 존재한다.

전체 Gradle 빌드와 96개 비-Docker 테스트는 통과했다. 다만 실제 PostgreSQL
Testcontainers 테스트 2건, OAuth 3사, Toss sandbox, SweetTracker 계약 계정,
AWS S3/KMS Staging 왕복은 아직 수행하지 않았다. 핵심 domain/application branch
coverage도 35.1%로 계획 목표 85%에 미달한다. 따라서 현재 상태는
"기능 코드가 구현된 MVP 후보"이며 Production 출시 완료 상태는 아니다.

## 2. 오늘 완료한 개발

### 2.1 공통 기반과 구조

- Java 21, Spring Boot 4.1, PostgreSQL 16, Gradle 기반을 구성했다.
- 기능 패키지를 `common`, `auth`, `user`, `catalog`, `listing`, `storage`,
  `cart`, `offer`, `order`, `payment`, `shipment`, `trade`, `market`, `review`,
  `favorite`, `alert`, `notification`, `admin`, `worker`로 분리했다.
- 공통 성공 응답 `data/meta`, 오류 응답
  `code/message/traceId/fieldErrors`를 구현했다.
- `X-Trace-Id`를 요청, 응답, 로그에 연결했다.
- `Clock`과 외부 연동 포트를 주입 가능하게 구성했다.
- JPA `ddl-auto=validate`, `open-in-view=false`, UTC를 적용했다.
- API와 worker를 동일 이미지의 별도 프로세스로 실행하도록 구성했다.
- Redis와 메시지 브로커 없이 PostgreSQL 조건부 갱신, unique 제약,
  `FOR UPDATE SKIP LOCKED` 작업 큐를 사용했다.

### 2.2 Flyway와 데이터 모델

- `V001`~`V015` migration을 작성했다.
- 사용자, 소셜 계정, 인증 세션, 카드 카탈로그, 업로드, 매물, 장바구니,
  제안, 주문과 주문 항목, 결제, 환불, 배송, 거래, 시세, 리뷰, 관심,
  가격 알림, 인앱 알림, worker job과 인덱스를 구성했다.
- 주문과 `order_items`를 분리해 동일 판매자 묶음 주문을 지원한다.
- 카드 카탈로그 기준 데이터를 고정 UUID로 seed한다.
- 결제 webhook dedupe, 배송 provider 이벤트 시각, 업로드 optimistic version을
  후속 migration으로 보강했다.
- 적용된 migration을 수정하지 않고 forward-only 원칙을 유지했다.

### 2.3 인증·사용자·보안

- Google, Kakao, Naver OAuth2 registration과 사용자 provisioning을 구현했다.
- Access Token 30분 JWT와 Refresh Token 14일을 구현했다.
- refresh token 원문 대신 hash를 저장하고 재발급마다 rotation한다.
- 이전 refresh token 재사용 시 해당 사용자의 활성 세션을 모두 폐기한다.
- refresh cookie에 HttpOnly, 운영 Secure, SameSite=Lax 정책을 적용했다.
- cookie 기반 reissue/logout에는 CSRF 방어를 적용했다.
- 정확한 Origin CORS allowlist와 JSON `401/403` 응답을 구성했다.
- `/users/me` 조회·수정·탈퇴와 만 15세·약관 onboarding을 구현했다.
- 정지·탈퇴 사용자의 쓰기 요청을 차단한다.
- 진행 중 거래가 있는 탈퇴를 차단하고, 탈퇴·정지 시 판매 가능한 매물과
  관련 활동을 정리한다.
- 주문 예약 시 buyer/seller를 UUID 순서로 잠그고 두 사용자 모두 ACTIVE인지
  확인해 탈퇴·정지와 주문 간 TOCTOU를 방어했다.

### 2.4 카탈로그·업로드·매물

- 게임, 세트, 카드, variant 검색·상세·관리자 등록 API를 구현했다.
- Local/S3 `ObjectStoragePort`, presigned upload, CloudFront 조회를 구현했다.
- HEAD, MIME, magic signature, 파일 소유권을 검증한다.
- JPG/PNG/WEBP, 파일당 최대 10MB, 매물 이미지 2~6장 정책을 반영했다.
- 매물 등록·수정·숨김·재활성화·삭제·검색·상세·내 매물 API를 구현했다.
- `ACTIVE/HIDDEN`에서만 편집하고 `RESERVED/SOLD` 편집을 차단한다.
- 업로드 완료와 만료 worker 경합을 optimistic locking으로 방어한다.

### 2.5 장바구니·가격 제안

- 같은 매물 중복 추가를 차단하는 장바구니 CRUD를 구현했다.
- 장바구니를 판매자별로 그룹화해 반환한다.
- 가격 제안, 수락, 거절, 판매자 1회 역제안, 구매자 역제안 응답을 구현했다.
- 제안은 48시간 후 만료되고 worker가 처리한다.
- 수락 제안은 지정 구매자·매물·가격에만 한 번 소비된다.
- actor와 상태에 따라 응답 권한을 검증하고 action history를 저장한다.

### 2.6 주문·예약·결제·환불

- 단일 주문과 동일 판매자 묶음 주문을 구현했다.
- 요청 매물을 ID 순서로 처리하고 조건부 `ACTIVE → RESERVED` 갱신을 수행한다.
- 한 매물이라도 실패하면 주문, 항목, 전체 예약이 rollback된다.
- 묶음 주문 배송비는 최고 배송비 한 번만 부과한다.
- 가격, 카드, 판매자, 배송지를 주문 시점 snapshot으로 저장한다.
- 예약 시간 10분과 `now >= reservationExpiresAt` 만료 경계를 적용했다.
- Toss 결제를 DB 준비 transaction, 외부 호출, 최종 확정 transaction으로
  분리했다.
- `Idempotency-Key`, 결제 요청 기록, 금액 재검증, 중복 승인을 구현했다.
- timeout·응답 유실은 실패가 아니라 `UNKNOWN`으로 유지한다.
- `UNKNOWN/CONFIRMING` 동안 매물 예약을 해제하지 않고 reconcile worker가
  provider 상태로 수렴시킨다.
- webhook payload는 직접 신뢰하지 않고 알려진 결제와 상태만 dedupe한 뒤
  provider 재조회 작업을 예약한다.
- 부분 취소를 전체 환불로 오인하지 않도록 Toss 응답 매핑을 보강했다.
- 배송 전 환불 요청, 관리자 승인, 외부 취소, 불명 환불 복구를 구현했다.

### 2.7 배송·구매확정·거래·시세·리뷰

- SweetTracker adapter와 non-prod stub을 `ShipmentTrackingPort` 뒤에 구현했다.
- 운송장 등록·수정·조회와 배송 polling을 구현했다.
- provider 이벤트 dedupe와 역순·오래된 이벤트 방어를 구현했다.
- 조회 중 운송장이 변경되면 이전 운송장 결과를 폐기한다.
- `deliveredAt + 6일` 사전 알림, `+7일` 자동 구매확정을 구현했다.
- 구매자 hold와 관리자 해제·수동 배송완료 경로를 제공한다.
- 구매확정 시 `order_item`마다 Trade를 원자적으로 생성한다.
- 거래 가격에서 배송비를 제외한다.
- 최근 거래가, 7일 평균, 30일 중앙값, 최저가, 30일 거래량을 집계한다.
- 환불 Trade는 보존하면서 시세 집계에서 제외한다.
- 구매확정 주문당 구매자 리뷰 한 건을 허용한다.

### 2.8 관심·알림·관리자·worker

- 관심 매물 추가·삭제·목록을 구현했다.
- 활성 가격 알림 사용자당 20개, 기본 하락률 10%, one-shot 전송을 구현했다.
- 요구된 인앱 알림 10종과 읽음·일괄 읽음을 구현했다.
- 원인 상태 변화와 알림 생성을 연결하고 DB dedupe를 적용했다.
- 관리자 사용자 정지·해제, 매물 숨김, 주문·결제·환불·Trade 조회,
  환불 승인, 배송 수동 처리, 시세 집계 제외·복원을 구현했다.
- 민감한 관리자 상태 변경에 actor와 구조화 감사 로그를 남긴다.
- 예약·제안·업로드 만료, 결제·환불 reconcile, 배송 polling,
  자동확정, 시세 refresh worker를 구현했다.

### 2.9 운영 기반

- Actuator liveness/readiness와 OpenAPI/Swagger를 구성했다.
- JaCoCo 리포트를 Gradle `check`에 연결했다.
- Dockerfile과 Compose에 PostgreSQL, API, worker를 분리했다.
- GitHub Actions에 Java 21, Gradle check, bootJar, Docker build를 구성했다.
- local/test AES-GCM과 prod AWS KMS envelope encryption을 구현했다.
- prod S3/CloudFront, Toss, SweetTracker adapter와 환경변수 검증을 구현했다.
- worker non-web context와 prod adapter bootstrap 테스트를 추가했다.
- 배포·migration·backup·rollback 절차를 [runbook](runbook.md)에 기록했다.

## 3. 핵심 기술 결정

1. MVP에서는 Redis와 broker를 쓰지 않고 PostgreSQL을 coordination 원본으로 쓴다.
2. 모든 상태 변경은 application service transaction과 domain 메서드로 수행한다.
3. 외부 결제·배송 호출 중 DB transaction을 유지하지 않는다.
4. 결제 timeout은 실패가 아니며 조회 전에는 재고를 풀지 않는다.
5. redirect 또는 webhook만으로 결제를 확정하지 않는다.
6. 배송 결과는 현재 운송장과 provider event time을 함께 확인한다.
7. 판매 가능한 매물을 예약하기 전에 buyer/seller ACTIVE 상태를 잠금 검증한다.
8. prod 개인정보는 KMS data key와 AES-GCM envelope 방식으로 암호화한다.
9. local/test에는 안전한 stub, prod에는 실제 외부 adapter를 활성화한다.

## 4. 검증 결과

| 항목 | 결과 |
|---|---|
| `./gradlew clean check bootJar --no-daemon` | 성공 |
| 테스트 | 총 98건, 96건 통과, 실패 0건, 2건 skip |
| skip | Docker 미감지로 PostgreSQL migration·동시 예약 Testcontainers 테스트 |
| prod adapter bootstrap | Toss·SweetTracker·S3·KMS non-web context 성공 |
| 실행 JAR | `build/libs/cardhub-0.0.1-SNAPSHOT.jar`, 약 82MB |
| 전체 line coverage | 45.7% |
| 전체 branch coverage | 31.5% |
| domain/application line coverage | 48.3% |
| domain/application branch coverage | 35.1%, 계획 목표 85% 미달 |

## 5. 현재 환경과 제약

- Homebrew PostgreSQL 16이 로컬 5432 포트에서 실행 중이다.
- `docker compose` plugin 명령은 동작하지 않는다.
- `/opt/homebrew/bin/docker-compose` 2.32.2는 사용할 수 있다.
- Compose DB는 로컬 포트 충돌을 피하도록 `POSTGRES_PORT=5433`으로 띄워야 한다.
- 기본 Compose API는 `local`만 활성화하므로 OAuth 로그인이 꺼져 있다.
- 비밀번호 로그인이나 dev token endpoint가 없어 보호 API 수동 테스트에는
  실제 OAuth 또는 prod에 절대 포함되지 않는 `local-dev` bootstrap이 필요하다.
- 실제 OAuth, Toss, SweetTracker, AWS credential이 없어 Staging 왕복을
  수행하지 않았다.

## 6. 남은 개발·검증 백로그

### P0 — 출시 차단

#### P0-1. 기준 커밋과 변경 검토

- [ ] 기능별 `git diff` 검토
- [ ] secret·불필요 파일 검사
- [ ] 현재 MVP 기준 커밋 생성
- [ ] GitHub branch와 PR 정책 결정

완료 조건: clean checkout에서 빌드·CI가 동일하게 재현된다.

#### P0-2. PostgreSQL·로컬 runtime 검증

- [ ] Docker Desktop에서 Compose API/worker/PostgreSQL 실행
- [ ] 빈 PostgreSQL에 V001~V015 적용
- [ ] readiness, 공개 catalog, 로그인 전 401 smoke
- [ ] PostgreSQL Testcontainers 2건을 skip 없이 실행
- [ ] 동일 매물 동시 주문에서 정확히 한 요청만 성공하는지 확인

완료 조건: migration·동시성 테스트 2건과 runtime smoke가 모두 통과한다.

#### P0-3. 보호 API 수동 테스트 경로

- [ ] 실제 OAuth만 쓸지 `local-dev` user/JWT bootstrap을 만들지 결정
- [ ] local-dev 사용 시 prod context에 bean·endpoint가 없음을 테스트
- [ ] seller, buyer, admin fixture 준비
- [ ] Swagger/Postman smoke collection 작성

완료 조건: 비밀정보 없이 매물 → 주문 → 결제 → 배송 → 확정 흐름을 반복한다.

#### P0-4. OAuth 3사 Staging

- [ ] provider 앱과 HTTPS callback 등록
- [ ] Origin allowlist 구성
- [ ] 신규·재로그인, 정지·탈퇴·충돌 검증
- [ ] refresh rotation, reuse 차단, logout, CSRF 검증
- [ ] 생년월일·약관 onboarding 계약 검증

선행조건: provider client ID/secret, Staging domain.

완료 조건: Google, Kakao, Naver 각각에서 보호 API 호출까지 성공한다.

#### P0-5. Toss sandbox 결제·환불 E2E

- [ ] 카드·간편결제 정상 승인
- [ ] 금액 불일치·거절·중복 idempotency
- [ ] timeout·응답 유실·중복/역순 webhook
- [ ] 승인 후 DB 확정 실패 reconcile
- [ ] 전체 취소·부분 취소·불명 환불 복구

선행조건: Toss test key와 결제 화면 callback.

완료 조건: 주문·결제·매물이 provider 상태로 수렴하고 UNKNOWN 중 예약이 유지된다.

#### P0-6. SweetTracker contract·자동확정

- [ ] 운영 계약 계정, API key, 택배사 code table 확보
- [ ] 실제 응답 fixture와 status normalization 검증
- [ ] 역순·중복·오래된 이벤트·운송장 교체 경합 검증
- [ ] +6일 알림, +7일 자동확정, hold 검증
- [ ] 장애·timeout·backoff·관리자 수동 완료 검증

완료 조건: 실제 tracking 응답으로 배송완료와 자동확정이 안전하게 동작한다.

#### P0-7. AWS S3·CloudFront·KMS Staging

- [ ] presigned upload와 CDN 조회
- [ ] HEAD·magic signature·소유권·만료 cleanup
- [ ] KMS 최소 권한과 envelope encrypt/decrypt
- [ ] key rotation과 이전 ciphertext 복호화

선행조건: AWS bucket, CloudFront, KMS key, IAM role.

완료 조건: prod profile 구성으로 실제 이미지와 배송지 snapshot이 왕복한다.

#### P0-8. 핵심 테스트 확대

- [ ] domain/application branch coverage 85% 목표
- [ ] 예약 만료·결제 승인 경합
- [ ] 결제 승인 전후 process crash
- [ ] refund·shipment·manual confirm worker 경쟁
- [ ] 리뷰·제안·알림·업로드 중복/경합
- [ ] API 400/401/403/404/409/422 계약

완료 조건: 계획의 P0 시나리오 전체가 통과하고 핵심 상태 분기 누락이 없다.

#### P0-9. 명세 동기화

- [ ] 실제 Controller/DTO와 API 명세 대조
- [ ] migration 문서를 `order_items`, V013~V015 기준으로 갱신
- [ ] 테스트 명세를 묶음 주문·제안·리뷰·SweetTracker·KMS 기준으로 갱신
- [ ] Figma ERD·상태도와 migration 재대조

완료 조건: 코드, Flyway, API, Notion, Figma 간 알려진 불일치가 없다.

### P1 — 품질·보안·운영

- [ ] 주요 application service 정상·경계·권한·invalid state 테스트 보강
- [ ] MockMvc 계약과 OpenAPI 예시 보강
- [ ] Toss·SweetTracker·S3 WireMock contract suite
- [ ] JaCoCo 변경 코드 품질 gate
- [ ] 공개 API·webhook rate limiting
- [ ] CI dependency·secret scan
- [ ] 개인정보 보존·익명화 기간과 관리자 감사 로그 정책
- [ ] 대표 데이터셋과 `EXPLAIN ANALYZE`
- [ ] V012 인덱스 실제 사용·중복 여부 확인
- [ ] pool·timeout·worker batch 부하 테스트
- [ ] DB 장애 시 readiness 실패·복구 확인
- [ ] payment UNKNOWN, worker, 예약·배송 지연 metric과 CloudWatch alarm

### P2 — 출시 준비

- [ ] Staging RDS·S3·CloudFront·KMS·API·worker 배포
- [ ] ECR image push와 동일 SHA Production 승격
- [ ] 단일 Flyway release job
- [ ] ALB·Route53·HTTPS·OAuth callback
- [ ] RDS backup/PITR와 restore 훈련
- [ ] immutable image rollback과 additive migration 호환 검증
- [ ] Production smoke와 최종 출시 판단
- [ ] 장애·환불 수동 복구 runbook 리허설

## 7. 권장 다음 작업 순서

1. working tree 검토와 기준 커밋
2. Docker PostgreSQL에서 migration·동시성 테스트를 skip 없이 실행
3. `local-dev` 수동 테스트 경로 또는 실제 OAuth Staging 연결
4. seller → listing → buyer order → payment → shipment → confirm smoke
5. OAuth·S3/KMS·Toss·SweetTracker 실제 adapter 검증
6. 핵심 상태 전이 테스트와 coverage 확대
7. 성능·보안·관측성 보강
8. Staging 복구 훈련 후 Production

## 8. 로컬 smoke 명령

```bash
cd /Users/seojichan/Desktop/cardhub
POSTGRES_PORT=5433 /opt/homebrew/bin/docker-compose up --build -d
POSTGRES_PORT=5433 /opt/homebrew/bin/docker-compose ps
curl -i http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/v1/catalog/games
curl -i http://localhost:8080/api/v1/users/me
./gradlew clean check
```

예상 결과:

- readiness는 `200`과 `UP`
- catalog는 seed 데이터 반환
- 로그인 전 `/users/me`는 의도된 `401`
- Gradle은 `BUILD SUCCESSFUL`
- Docker 사용 가능 환경에서는 PostgreSQL 테스트 2건이 skip되지 않음

## 9. 이번 마일스톤 Definition of Done

- [x] 주요 MVP 기능 코드와 migration 존재
- [x] 공통 보안·오류·trace 계약 구현
- [x] local stub과 prod adapter 분리
- [x] Gradle check와 bootJar 성공
- [ ] PostgreSQL 통합 테스트 skip 0건
- [ ] OAuth·Toss·SweetTracker·AWS Staging 왕복
- [ ] 핵심 domain/application branch coverage 85%
- [ ] P0 acceptance scenario 전체 통과
- [ ] Staging 배포·backup·rollback 검증

