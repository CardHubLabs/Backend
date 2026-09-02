# CardHub Figma 대조 보완 개발 기록 — 2026-09-02

## 1. 문서 정보

| 항목 | 내용 |
|---|---|
| 대상 저장소 | CardHub Spring Boot Backend |
| 작업 브랜치 | `feat/backend-mvp` |
| 구현 기준 | 승인된 `CardHub Figma 대조 후 MVP 보완 계획` |
| DB 기준 | PostgreSQL 16, Flyway V001~V023 |
| 최종 로컬 검증 | 2026-09-01 KST, Docker PostgreSQL을 포함한 `./gradlew check` |
| 현재 판정 | 기능 보완 코드는 구현·테스트 통과, 출시 Definition of Done은 미완료 |
| Git 상태 | 변경사항은 working tree에 있으며 아직 커밋·push하지 않음 |

이 문서는 2026-08-25 기준 기록인
[development-status-2026-08-25.md](development-status-2026-08-25.md) 이후 수행한
Figma 기능·상태 보드 및 ERD 대조 보완 작업을 정리한다.

## 2. 요약

기존 MVP 골격에 다음 보완을 반영했다.

- 포켓몬·원피스와 다국어 카드명을 표현하는 확장 카탈로그
- 매물 생명주기와 분리된 관리자 차단 축
- 주문번호, 구조화 암호화 주소, 주문 항목별 스냅샷과 제안 이력
- Toss 승인 결과의 provider identity·금액·결제수단 검증과 UNKNOWN 복구 메타데이터
- 매물 단위에서 카드 Variant 단위로 전환한 관심·가격 알림
- 7일·30일·90일 평균과 중앙값 시세
- SweetTracker 택배사 목록 동기화와 활성 택배사 코드 검증
- 화면용 배송 준비·자동확정 파생 상태
- 관리자 결제 상세 및 복구 작업 등록 경로

기존 Flyway V001~V015는 수정하지 않았고 V016~V023만 추가했다. 빈 DB 전체
migration과 V015 데이터가 있는 DB의 upgrade migration을 모두 실제 PostgreSQL로
검증했다.

## 3. Flyway 변경

| 버전 | 파일 | 주요 내용 |
|---|---|---|
| V016 | `V016__catalog_figjam_alignment.sql` | `GameCode`, 다국어 카드명, 카드 rarity, Variant의 version/print type과 listable 여부, One Piece 게임 seed |
| V017 | `V017__listing_moderation.sql` | 매물 `CLEAR/BLOCKED` moderation 축, 차단 actor·사유·시각, `BLOCKED + ACTIVE` 금지 제약 |
| V018 | `V018__order_snapshots.sql` | `order_no`, 주소 schema version, 주문 항목의 제안·상태·배송비·대표 이미지 스냅샷 |
| V019 | `V019__payment_recovery_metadata.sql` | provider 상태·결제수단·간편결제사, confirm 시도·다음 복구 시각·수동 검토 플래그 |
| V020 | `V020__variant_engagement.sql` | 관심·가격 알림을 `card_variant_id` 기준으로 백필·중복 제거·유니크 제약 적용 |
| V021 | `V021__market_windows.sql` | `median_7d`, `average_30d`, `average_90d`, `median_90d` 추가 |
| V022 | `V022__shipping_carriers.sql` | 택배사 provider code·표시명·활성 여부 테이블 및 로컬 stub seed |
| V023 | `V023__figjam_constraints.sql` | Variant 언어, 주문번호 형식 check와 배송 carrier 조회 인덱스 |

주요 호환 정책:

- 기존 카드 `name`과 Variant `version`, legacy rarity 컬럼은 제거하지 않았다.
- 기존 주문번호는 `CH-L-{UUID}`로 백필한다.
- 신규 주문번호는 `CH-{하이픈 없는 UUID}` 형식이다.
- 기존 관심·가격 알림의 listing ID는 한 릴리스 동안 nullable legacy 컬럼으로 유지한다.
- 기존 문자열 배송지는 schema v1, 구조화 주소 JSON 암호문은 schema v2로 구분한다.

## 4. 기능별 구현 내용

### 4.1 카탈로그

- `GameCode`: `POKEMON`, `ONE_PIECE`
- `CardLanguage`: `KO`, `JA`, `EN`
- `VersionType`: `NORMAL`, `PROMO`, `MANGA`
- `PrintType`: `NORMAL`, `HOLO`
- 카드에 `nameKo`, `nameJa`, `nameEn`, `rarity`를 추가했다.
- 독립 Variant 검색에서 게임·세트·카드·언어·버전·인쇄 방식·판매 가능 여부를 필터링한다.
- 검색어는 한·일·영 카드명과 카드 번호를 대상으로 한다.
- 매물 등록 시 Variant 존재 여부와 `isListable=true`를 함께 검증한다.
- 관리자 게임·세트·카드·Variant 생성·수정 및 판매 가능 여부 변경 API를 추가했다.

핵심 코드:

- `catalog/api/CatalogController.java`
- `catalog/application/CatalogService.java`
- `catalog/domain/CardGame.java`, `Card.java`, `CardVariant.java`

### 4.2 매물 관리자 차단

- 판매 상태 `ACTIVE/HIDDEN/RESERVED/SOLD/DELETED`와 별도로
  moderation 상태 `CLEAR/BLOCKED`를 관리한다.
- ACTIVE 매물을 차단하면 lifecycle을 HIDDEN으로 바꾼다.
- RESERVED/SOLD 매물은 주문·결제 상태를 유지한 채 moderation만 차단한다.
- 차단된 매물의 예약 해제·환불 복원 결과는 ACTIVE가 아니라 HIDDEN이다.
- 차단 매물은 공개 검색·상세·장바구니·제안·주문·최저가·시세에서 제외한다.
- 판매자는 차단된 매물을 수정·활성화·삭제할 수 없다.
- 차단 시 열린 제안을 종료하고 장바구니에서 제거한다.
- unblock 후 HIDDEN 매물은 판매자가 별도로 활성화해야 한다.

핵심 코드:

- `listing/domain/Listing.java`
- `listing/domain/ListingModerationStatus.java`
- `admin/application/AdminService.java`
- `order/infrastructure/JpaListingReservationAdapter.java`

### 4.3 주문·주소·장바구니

- 모든 신규 주문에 외부 노출용 `orderNo`를 생성한다.
- 단일·묶음 주문은 다음 구조화 주소를 지원한다.
  - `recipientName`
  - `phoneNumber`
  - `postalCode`
  - `addressLine1`
  - `addressLine2`
- 구조화 주소는 JSON 전체를 `SensitiveDataCipher`로 암호화한다.
- 한 릴리스 동안 legacy 문자열 `shippingAddress`도 허용한다.
- 문자열 주소와 구조화 주소를 동시에 전달하면 validation 400으로 거부한다.
- 주문 항목별로 `sourceOfferId`, 카드 상태, 배송비, 대표 이미지 storage key를 저장한다.
- 수락된 제안은 단일 주문에서만 사용하며 해당 주문 항목에 제안 ID를 보존한다.
- 주문 생성이 성공하면 구매자의 해당 장바구니 항목을 같은 transaction에서 제거한다.
- 주문 응답에 화면용 `fulfillmentStatus`, `autoConfirmationStatus`,
  `autoConfirmAt`, `autoConfirmNoticeAt`을 추가했다.
- 구매확정 응답은 묶음 주문을 위해 단일 ID가 아닌 `tradeIds[]`를 반환한다.

화면용 파생 상태는 별도 DB OrderStatus가 아니다.

| 파생 필드 | 값 |
|---|---|
| `fulfillmentStatus` | `NOT_STARTED`, `PREPARING`, `SHIPPED`, `DELIVERED` |
| `autoConfirmationStatus` | `NONE`, `SCHEDULED`, `HOLD`, `COMPLETED` |

핵심 코드:

- `order/api/OrderController.java`
- `order/application/OrderApplicationService.java`
- `order/application/ShippingAddressCodec.java`
- `order/domain/Order.java`, `OrderItem.java`, `ShippingAddressSnapshot.java`
- `cart/infrastructure/CartOrderCleanupAdapter.java`

### 4.4 결제·환불 복구

- 주문 생성 시 발급한 `orderNo`를 Toss `orderId`로 사용한다.
- confirm 요청의 `tossOrderId`가 주문 `orderNo`와 다르면 외부 호출 전에 거부한다.
- Toss 승인·조회 결과의 `paymentKey`, `orderId`, `totalAmount`를 저장값과 대조한다.
- 결제수단은 `CARD`, `EASY_PAY`만 허용하고 간편결제 provider를 보존한다.
- provider identity 또는 금액 불일치는 승인하지 않고 `UNKNOWN` 및 수동 검토로 남긴다.
- 동일 `Idempotency-Key`와 동일 payload의 UNKNOWN 요청은 같은 key로 재시도한다.
- 같은 key에 다른 payload를 보내면 409로 거부한다.
- 이미 CONFIRMING이면 외부 중복 호출 없이 202를 반환한다.
- 복구 worker는 동일 idempotency key confirm 재시도 후 조회 결과로 검증한다.
- `NOT_FOUND_PAYMENT`만으로 즉시 FAILED 처리하지 않는다.
- 기본 10회 후에도 불명확하면 UNKNOWN을 유지하면서 관리자 검토 대상으로 표시한다.
- 구매자는 주문별 환불 진행 상태를 조회할 수 있다.
- 관리자는 결제 상세와 복구 job을 조회·등록할 수 있지만 APPROVED 강제 변경은 할 수 없다.

핵심 코드:

- `payment/application/PaymentPreparationService.java`
- `payment/application/PaymentFinalizationService.java`
- `payment/application/PaymentApplicationService.java`
- `payment/infrastructure/TossPaymentGateway.java`
- `payment/domain/Payment.java`, `PaymentMethod.java`

### 4.5 Variant 관심·가격 알림

- 신규 관심 경로는 카드 Variant를 기준으로 한다.
- legacy listing 관심 경로는 listing을 Variant로 변환하는 adapter로 유지한다.
- 가격 알림 기준가는 등록 시점의 해당 Variant 활성 최저가다.
- 활성 매물이 없으면 비즈니스 validation 오류(422)를 반환한다.
- 사용자당 활성 가격 알림은 최대 20개이며 기본 하락 임계치는 10%다.
- Variant별 활성 알림은 사용자당 한 건만 허용한다.
- 신규 매물 등록 또는 가격 변경 시 Variant 최저가를 다시 계산해 알림을 평가한다.
- 조건을 충족한 알림은 한 번 발송한 뒤 비활성화한다.
- Variant 관심 사용자는 같은 Variant의 신규 매물 `LISTING_ALERT` 대상이 된다.

### 4.6 시세

- 시세 집계는 PostgreSQL `FILTER`와 `percentile_cont`를 사용한다.
- 다음 값을 한 번의 집계 query로 계산한다.
  - 최근 거래가
  - 7일 평균·중앙값
  - 30일 평균·중앙값
  - 90일 평균·중앙값
  - 현재 활성 최저가
  - 30일 거래량
- 환불 또는 관리자 제외 거래는 모든 기간 집계에서 제외한다.
- 차단된 매물은 활성 최저가 계산에서 제외한다.

### 4.7 배송·택배사

- 활성 택배사 목록 조회 API를 추가했다.
- 운송장 등록·수정 시 현재 활성화된 carrier code만 허용한다.
- `Shipment.createdAt`을 API에서 `shippedAt`으로 노출하며 JPA에서 변경 불가로 명시했다.
- SweetTracker `companylist` 응답을 provider port 뒤에서 동기화한다.
- 동기화 실패나 빈 응답에서는 마지막 성공 목록을 유지한다.
- 로컬 환경은 실제 택배사 코드와 혼동되지 않는 stub provider 목록을 사용한다.
- 주문 응답의 배송·자동확정 파생값은 Shipment projection으로 계산한다.
- 배송 추적 polling, 정규화, provider event 중복 제거와 역순 방어는 기존 경로를 유지한다.

## 5. 변경된 주요 API

### 신규·확장 API

| Method | 경로 | 내용 |
|---|---|---|
| GET | `/api/v1/catalog/variants` | 독립 Variant 검색 |
| PATCH | `/api/v1/admin/catalog/games/{gameId}` | 게임 수정 |
| PATCH | `/api/v1/admin/catalog/sets/{setId}` | 세트 수정 |
| PATCH | `/api/v1/admin/catalog/cards/{cardId}` | 카드 수정 |
| PATCH | `/api/v1/admin/catalog/variants/{variantId}` | Variant 수정 |
| PATCH | `/api/v1/admin/catalog/variants/{variantId}/listability` | Variant 판매 가능 여부 변경 |
| POST | `/api/v1/admin/listings/{listingId}/block` | 사유를 포함한 관리자 차단 |
| POST | `/api/v1/admin/listings/{listingId}/unblock` | 관리자 차단 해제 |
| POST | `/api/v1/favorites/card-variants/{variantId}` | Variant 관심 추가 |
| DELETE | `/api/v1/favorites/card-variants/{variantId}` | Variant 관심 삭제 |
| GET | `/api/v1/orders/{orderId}/refund` | 구매자 환불 상태 조회 |
| GET | `/api/v1/admin/payments/{paymentId}` | 관리자 결제 복구 상세 조회 |
| POST | `/api/v1/admin/payments/{paymentId}/reconcile` | 결제 복구 job 등록 |
| GET | `/api/v1/shipping/carriers` | 활성 택배사 목록 조회 |

### 호환 기간 legacy 경로·필드

- `POST/DELETE /api/v1/favorites/{listingId}`
- 가격 알림 생성 요청의 `listingId`
- 주문 요청의 문자열 `shippingAddress`
- 관리자 `POST /api/v1/admin/listings/{listingId}/hide`

legacy 경로는 한 릴리스 후 제거 여부를 별도로 결정한다.

### 현재 구현과 계획 간 알려진 차이

- 계획의 계산형 단일 `effectiveStatus=BLOCKED` 필드는 아직 응답에 없다. 현재 API는
  lifecycle `status`와 `moderationStatus`를 분리해 제공한다.
- legacy 관리자 `/hide`, listing 기반 관심·가격 알림 경로는 동작하지만 코드에
  `@Deprecated` 표시는 아직 추가하지 않았다.
- 구조화 주소 request 필드명은 일반 `shippingAddress` 객체가 아니라
  `shippingAddressSnapshot`이다.
- SweetTracker callback 수신 경로는 계약상 callback 사용이 확정되지 않아 구현하지
  않았고 현재 polling 정규화 경로만 사용한다.
- `shipments.carrier_code`는 application service에서 활성 택배사 여부를 검증하지만
  `shipping_carriers` 테이블을 직접 참조하는 DB FK는 없다.
- V021의 신규 시세 컬럼은 migration에서 과거 값을 백필하지 않고 다음 시세 refresh에서
  계산한다.

## 6. 검증 결과

### 6.1 전체 Gradle 검증

실행 명령:

```bash
DOCKER_HOST=unix:///Users/seojichan/.colima/default/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./gradlew check --no-daemon
```

결과:

| 항목 | 결과 |
|---|---|
| Gradle `check` | `BUILD SUCCESSFUL` |
| 전체 테스트 | 107 |
| 통과 | 107 |
| 실패 | 0 |
| 오류 | 0 |
| skip | 0 |
| `git diff --check` | 경고 없음 |

### 6.2 PostgreSQL 필수 검증

`PostgreSqlMigrationTest` 4건을 PostgreSQL 16 Testcontainers에서 통과했다.

1. 빈 DB에서 V001부터 V023까지 적용 후 JPA model validation
2. 대표 V015 데이터를 V023까지 upgrade하고 주문·관심·알림 백필 검증
3. 두 구매자의 동일 매물 동시 예약에서 한 요청만 성공
4. 7·30·90일 시세 경계와 짝수·홀수 중앙값 계산

### 6.3 JaCoCo

| 범위 | Line | Branch |
|---|---:|---:|
| 전체 코드 | 44.8% | 31.2% |
| 핵심 domain/application | 47.0% | 34.5% |

계획 목표인 핵심 domain/application branch 85%에는 아직 도달하지 못했다.
`check`는 리포트를 생성하지만 현재 coverage 실패 gate를 강제하지 않는다.

## 7. 주요 설계 결정

1. 매물 `BLOCKED`는 lifecycle 상태가 아니라 독립 moderation 상태다.
2. `SELLER_PREPARING`, `AUTO_CONFIRM_PENDING`, `AUTO_CONFIRM_HOLD`는 DB 상태가
   아니라 응답에서 계산하는 화면용 상태다.
3. 주문과 Trade 관계는 `ORDER → ORDER_ITEM → TRADE`이며 묶음 주문은 항목 수만큼
   Trade를 생성한다.
4. 배송비는 주문 총액에는 포함되지만 Trade 가격과 시세에서는 제외한다.
5. 주소는 구조화 JSON 의미를 유지하되 DB에는 전체 암호문으로 저장한다.
6. 결제 identity가 하나라도 일치하지 않으면 안전하게 UNKNOWN으로 남긴다.
7. 결제 UNKNOWN 상태에서는 매물 예약을 해제하지 않는다.
8. 관리자도 provider 확인 없이 결제를 APPROVED로 강제할 수 없다.
9. 운영 SweetTracker 택배사 코드는 고정 seed가 아니라 provider catalogue 동기화 결과를 사용한다.
10. 배포된 Flyway migration은 수정하지 않고 후속 migration만 추가한다.

## 8. 아직 남은 항목

### P0 — 출시 전 필수

- [ ] 핵심 domain/application branch coverage 85% 달성 및 Gradle gate 적용
- [ ] 예약 만료·결제 승인, 차단·환불, worker 간 경쟁 조건 테스트 확대
- [ ] Google·Kakao·Naver 실제 Staging OAuth 왕복
- [ ] Toss sandbox 카드·간편결제·UNKNOWN·전체 환불 E2E
- [ ] SweetTracker 계약 계정의 company list·tracking contract 검증
- [ ] AWS S3·CloudFront·KMS Staging 왕복
- [ ] 보호 API seller/buyer/admin 수직 smoke collection
- [ ] Figma ERD·상태 보드에 실제 구현 모델 반영
- [ ] API·Migration·테스트 Notion 원문 명세의 상세 항목 갱신
- [ ] working tree 검토 후 커밋·GitHub push

### P1 — 품질·운영

- [ ] 변경 API의 OpenAPI 설명·예시 보강
- [ ] Toss·SweetTracker WireMock contract suite
- [ ] 공개 API·webhook rate limiting
- [ ] dependency·secret scan
- [ ] 관리자 감사 로그 조회·보존 정책
- [ ] 대표 데이터셋 `EXPLAIN ANALYZE`와 인덱스 검증
- [ ] 결제 UNKNOWN·worker·배송 지연 metric 및 alarm

## 9. 재검증 순서

```bash
cd /Users/seojichan/Desktop/cardhub

# Colima가 실행 중인지 확인
docker context show
docker info

# 전체 단위·통합·PostgreSQL 테스트
DOCKER_HOST=unix:///Users/seojichan/.colima/default/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
./gradlew check --no-daemon

# 결과
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html

# 변경 검토
git status --short
git diff --check
git diff --stat
```

## 10. 완료 기준

현재 완료된 것은 **Figma 대조 보완 기능 코드와 로컬 자동 테스트**다.

Production 출시 완료로 판단하려면 다음 조건이 모두 필요하다.

- [x] V001~V023 빈 DB migration 성공
- [x] V015→V023 upgrade migration 성공
- [x] PostgreSQL 동시 예약과 시세 기간 통합 테스트 성공
- [x] 전체 Gradle 테스트 실패·skip 0
- [ ] 핵심 branch coverage 85%
- [ ] 외부 서비스 Staging 실제 왕복
- [ ] 문서·OpenAPI·Figma 최종 동기화
- [ ] Staging 배포·backup·rollback smoke
