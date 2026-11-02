# CardHub 운영 Runbook

## 배포 전 확인

1. 대상 이미지 SHA가 staging에서 `check`, OAuth 왕복, S3 업로드, Toss sandbox,
   SweetTracker contract smoke를 통과했는지 확인한다.
2. RDS 자동 백업과 PITR 보존 기간, 최근 복원 테스트 시각을 확인한다.
3. 새 Flyway SQL이 additive/forward-only인지 검토한다. 이미 배포된 migration은
   수정하지 않는다.
4. Secrets Manager/KMS의 DB, JWT, OAuth, Toss, SweetTracker, KMS 설정이 대상
   task role에서 조회 가능한지 확인한다.

## 배포 순서

1. 동일 이미지 SHA로 일회성 migration task를 하나만 실행한다.
2. migration 성공 후 worker를 교체하고 로그에서 작업 오류가 없는지 확인한다.
3. API를 rolling 교체한다.
4. `/actuator/health/liveness`, `/actuator/health/readiness`와 핵심 read API를
   smoke test한다.
5. 로그인 → 매물 조회 → sandbox 주문/결제 → 배송 등록 흐름을 확인한다.

동시에 여러 인스턴스에서 migration을 실행하지 않는다. 운영 API/worker는
각각 `CARDHUB_WORKER_ENABLED=false/true`로 실행한다.

## Rollback

- 애플리케이션 장애는 직전 immutable 이미지 SHA로 API와 worker를 되돌린다.
- migration은 되돌리지 않는다. 이전 애플리케이션이 새 컬럼과 공존하도록 DB
  변경을 additive하게 유지한다.
- 데이터 손상이 확인된 경우 쓰기 트래픽을 차단한 뒤 별도 RDS 인스턴스로
  PITR 복원하고, 검증 후 전환한다. 원본 DB에 즉시 덮어쓰지 않는다.

## 결제 복구

- `UNKNOWN` 또는 오래된 `CONFIRMING` 결제는 worker가 Toss 조회 API로
  재확인한다. redirect나 webhook payload로 상태를 강제 확정하지 않는다.
- `PARTIAL_CANCELED`나 취소 잔액을 검증할 수 없는 응답은 수동 조사 전까지
  `UNKNOWN`으로 유지한다.
- 결제키, 카드 정보, Authorization header를 로그나 티켓에 복사하지 않는다.

## 배송 장애

- SweetTracker 장애 중에는 마지막 내부 상태를 유지하고 지수 backoff로
  재시도한다.
- 운송장을 변경한 뒤 도착한 이전 운송장 결과는 폐기된다.
- 관리자의 수동 배송완료는 실제 택배사 증빙을 확인한 뒤 사용하며 actor ID가
  감사 로그에 남는지 확인한다.

## 키와 비밀정보 교체

- JWT secret 교체는 기존 access token(최대 30분)이 무효화됨을 고려해
  공지·배포한다. 필요하면 모든 refresh session을 폐기한다.
- KMS 키 rotation 후에도 보존 중인 ciphertext의 decrypt 권한을 유지한다.
- 유출 의심 시 Toss/OAuth/SweetTracker 자격증명을 공급자에서 먼저 폐기하고
  Secrets Manager 값과 실행 task를 순차 교체한다.
