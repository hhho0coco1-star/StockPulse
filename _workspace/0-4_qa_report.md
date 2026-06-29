# QA 리포트: WBS 0-4 외부 API 키 실제 호출 검증

> 검증 수행: 오케스트레이터 직접 실행 (PowerShell Invoke-RestMethod로 실제 HTTP 요청)

## 최종 판정: **PASS** (D1~D3 전체 충족)

## DoD 항목별 결과

| 항목 | 내용 | 결과 |
|------|------|-------|
| D1 | 네이버 뉴스 API 1회 호출 → 정상 응답 | **PASS** |
| D2 | DART 기업정보 API 1회 호출 → 정상 응답 | **PASS** |
| D3 | KIS 모의투자 토큰 발급 API 1회 호출 → Bearer 토큰 수신 | **PASS** |

## 실행 증거

### D1 — 네이버 뉴스 API
```
GET https://openapi.naver.com/v1/search/news.json?query=삼성전자&display=1
Headers: X-Naver-Client-Id / X-Naver-Client-Secret

응답:
  total: 4,351,917
  title: <b>삼성</b> 11개 계열사, 1~3차 협력회사와 상생 생태계 조성 협약 체결
→ HTTP 200, 정상 뉴스 데이터 수신
```

### D2 — DART 기업정보 API
```
GET https://opendart.fss.or.kr/api/company.json?crtfc_key=...&corp_code=00126380

응답:
  status: 000 (정상)
  corp_name: 삼성전자(주)
  stock_code: 005930
→ HTTP 200, 삼성전자 공시 데이터 정상 수신
```

### D3 — KIS 모의투자 토큰 발급
```
POST https://openapivts.koreainvestment.com:29443/oauth2/tokenP
Body: { grant_type: "client_credentials", appkey: "...", appsecret: "..." }

응답:
  token_type: Bearer
  expires_in: 86400 (24시간)
  access_token: eyJ0eXAiOiJKV1QiLCJh... (JWT)
→ HTTP 200, 유효한 Bearer 토큰 수신
```

## 정합성 확인

- 모든 키가 `.env`에만 저장됨 (.gitignore로 커밋 제외) ✅
- KIS는 모의투자 엔드포인트(`openapivts`) 사용 — 실전 아님 ✅
- 토큰 만료: 86400초(24시간) → Phase 1 구현 시 토큰 갱신 로직 필요

## 다음 단계 권고

| 서비스 | Phase | 비고 |
|--------|-------|------|
| KIS 시세 WebSocket | Phase 1 | 토큰 발급 후 ws:// 연결 |
| 네이버 뉴스 수집 | Phase 2 | News Collector 서비스 |
| DART 재무 수집 | Phase 2 | Fundamentals Collector 서비스 |
