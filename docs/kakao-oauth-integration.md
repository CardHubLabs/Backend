# 카카오 OAuth 백엔드 연동

카카오 로그인은 기존 `local,oauth` 프로필과 OAuth 세션을 사용한다. 카카오
REST API 키와 카카오 로그인용 시크릿은 `KAKAO_CLIENT_ID`,
`KAKAO_CLIENT_SECRET` 환경변수로 전달한다. 값은 저장소와 로그에 남기지 않는다.
현재 `oauth` 프로필은 Google·네이버 설정도 함께 읽는다.

카카오 개발자센터에서 로그인을 활성화하고, REST API 키에 다음 로컬 Redirect
URI를 등록한다. 닉네임 동의항목을 설정하고 시크릿 활성 상태를 확인한다.

```text
http://localhost:8080/login/oauth2/code/kakao
```

요청 scope는 `profile_nickname`이다. 카카오 사용자 ID로 계정을 식별하며,
이메일은 요청하거나 수집하지 않는다. 닉네임이 없으면 `CardHub 사용자`를 쓴다.
같은 이메일이라는 이유로 다른 소셜 공급자 계정과 자동 연결하지 않는다.

성공하면 기존 `OAUTH_SUCCESS_URL`로 이동한다. 취소, 이용 불가 계정, 기타
오류는 같은 callback에 각각 `error=access_denied`, `account_unavailable`,
`oauth_failed`를 붙여 전달한다. provider 오류 원문과 토큰은 전달하지 않는다.

실제 테스트에서는 `/login` → 카카오 로그인 → `/me` → 새로고침 → 로그아웃 →
재로그인을 확인한다. 첫 로그인 뒤와 재로그인 뒤 같은 사용자로 표시되는지
확인하고, 취소 및 Google·네이버 로그인도 회귀 테스트한다.
