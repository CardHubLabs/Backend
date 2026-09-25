# Google OAuth 백엔드 연동

기준일: 2026-09-25

이 문서는 현재 백엔드가 제공하는 Google OAuth·세션 계약과, 기존 소셜 계정의
재로그인 과정에서 수정한 LAZY 로딩 결함을 설명한다. OAuth 사용자·세션·JWT
골격은 기존 구현이며, 이번 수정의 핵심은 소셜 계정 조회 시 `User`를 함께
로딩하도록 보장한 것이다.

## 인증 흐름

1. 프론트가 `GET /oauth2/authorization/google`로 이동한다.
2. Spring Security가 Google 인증 페이지로 보내고 state·nonce·PKCE 검증을
   포함한 authorization code 흐름을 처리한다.
3. Google은 `GET /login/oauth2/code/google`로 인증 결과를 돌려보낸다.
4. `OAuthUserProvisioningService`가 provider와 subject로 `social_accounts`를
   조회한다. 첫 로그인이라면 `users`와 `social_accounts`를 생성한다.
5. `AuthSessionService`가 로그인 가능 상태를 확인하고 `auth_sessions`에
   refresh token의 SHA-256 hash와 만료 정보를 저장한 뒤 JWT access token을
   발급한다.
6. `OAuthLoginSuccessHandler`가 refresh cookie를 쓰고 임시 HTTP session과
   SecurityContext를 정리한 뒤 `OAUTH_SUCCESS_URL`로 이동시킨다.
7. 프론트가 refresh cookie로 access token을 재발급하고 `/api/v1/users/me`를
   조회한다.

로컬 기본 주소:

```text
OAuth 시작:             http://localhost:8080/oauth2/authorization/google
Google redirect URI:    http://localhost:8080/login/oauth2/code/google
Frontend success URL:   http://localhost:3000/auth/callback
```

## 세션과 API 계약

| 요청 | 인증/입력 | 결과 |
| --- | --- | --- |
| `GET /api/v1/auth/csrf` | 공개 | CSRF header 이름과 token 반환 |
| `POST /api/v1/auth/reissue` | refresh cookie + CSRF | refresh session 회전, 새 access token과 cookie 발급 |
| `POST /api/v1/auth/logout` | refresh cookie + CSRF | 서버 session 폐기, cookie 만료 |
| `GET /api/v1/users/me` | Bearer access token | 현재 사용자 정보 반환 |

성공 응답은 `{ "data": ..., "meta": ... }`, 실패 응답은 최상위
`code`, `message`, `traceId`, 선택적 `fieldErrors` 형식이다.

로컬 refresh cookie 기본 계약:

- 이름: `cardhub_refresh`
- `HttpOnly`: true
- `SameSite`: `Lax`
- 경로: `/api/v1/auth`
- 수명: 기본 14일
- `Secure`: `REFRESH_COOKIE_SECURE` 값 사용. 로컬 기본값은 false, 운영은 true

원본 refresh token은 DB에 저장하지 않고 hash만 저장한다. access token 기본
수명은 30분이며 응답 body로만 전달한다.

## 이번 수정: 기존 계정 재로그인 실패

### 증상

신규 Google 계정의 첫 로그인은 성공하지만 같은 계정으로 다시 로그인하면
`AuthSessionService.ensureLoginAllowed()`에서 다음 예외가 발생할 수 있었다.

```text
LazyInitializationException: Could not initialize proxy [User#...] - no session
```

`SocialAccount.user`는 `ManyToOne(fetch = LAZY)`다. 기존 계정 조회가
`SocialAccount::getUser`로 초기화되지 않은 proxy를 반환하고 provisioning
트랜잭션이 끝난 뒤, 별도 세션 생성 트랜잭션에서 `user.getStatus()`를 호출하면서
detached proxy 초기화가 실패했다.

### 해결

`SocialAccountRepository.findByProviderAndProviderSubject(...)`에 다음 entity graph를
적용했다.

```java
@EntityGraph(attributePaths = "user")
```

소셜 계정을 읽는 쿼리에서 `User`를 함께 로딩하므로 provisioning 트랜잭션이
끝난 뒤에도 로그인 상태와 JWT claim에 필요한 사용자 필드를 읽을 수 있다.
전역 Open Session in View를 켜거나 연관관계를 EAGER로 바꾸지 않아 다른 조회의
로딩 범위는 넓히지 않는다.

### 회귀 테스트

`SocialAccountRepositoryTest.loadsUserBeforeRepositoryTransactionCloses()`는 다음을
검증한다.

1. 한 트랜잭션에서 사용자와 Google 소셜 계정을 저장한다.
2. 별도 트랜잭션에서 provider와 subject로 다시 조회한다.
3. `account.getUser()`가 트랜잭션 안에서 초기화됐는지 확인한다.
4. 트랜잭션 종료 뒤 detached `User`의 `status`를 정상적으로 읽는다.

관련 테스트 실행:

```bash
./gradlew test \
  --tests 'com.kant.cardhub.auth.infrastructure.SocialAccountRepositoryTest' \
  --tests 'com.kant.cardhub.auth.application.AuthSessionServiceTest'
```

## 로컬 설정

Google Console의 웹 애플리케이션 OAuth client에 다음 redirect URI를 등록한다.

```text
http://localhost:8080/login/oauth2/code/google
```

필수 인증 설정:

```bash
export SPRING_PROFILES_ACTIVE='local,oauth'
export GOOGLE_CLIENT_ID='발급받은-client-id'
read -rs "GOOGLE_CLIENT_SECRET?Google client secret: "
echo
export GOOGLE_CLIENT_SECRET
```

Client Secret은 명령행 인자, Git 추적 파일, 로그에 남기지 않는다. 운영에서는
secret manager를 통해 주입하고 `REFRESH_COOKIE_SECURE=true`를 사용한다.

PostgreSQL을 호스트 5433에 실행하는 예시:

```bash
POSTGRES_PORT=5433 docker compose up -d postgres
export DB_URL='jdbc:postgresql://localhost:5433/cardhub'
export DB_USERNAME='cardhub'
export DB_PASSWORD='cardhub'
./gradlew bootRun
```

Compose v2 독립 실행 파일을 쓰는 환경에서는 `docker compose` 대신
`docker-compose`를 사용한다. `bootRun`은 서버가 실행되는 동안 종료되지 않으므로
Gradle 진행률이 `80% EXECUTING`에 머무는 것이 정상이다.

프론트의 기본 origin은 `http://localhost:3000`이다. 다른 origin을 쓰면
`CORS_ALLOWED_ORIGINS`에 정확한 origin을 설정하고 브라우저 요청에는 credentials를
포함해야 한다.

## 운영 전 확인

- Google Console redirect URI와 `OAUTH_SUCCESS_URL`을 운영 도메인으로 변경한다.
- `GOOGLE_CLIENT_SECRET`, JWT secret, DB 자격증명을 secret manager에서 주입한다.
- HTTPS에서 `REFRESH_COOKIE_SECURE=true`인지 확인한다.
- staging에서 로그인, 재로그인, 새로고침 세션 복원, 로그아웃을 모두 확인한다.
- token, cookie, authorization code와 사용자 개인정보를 로그에 남기지 않는다.

