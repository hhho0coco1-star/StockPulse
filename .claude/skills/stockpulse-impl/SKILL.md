---
name: stockpulse-impl
description: "StockPulse MSA 백엔드를 Kotlin+Spring Boot로 구현한다. architect 스펙을 받아 서비스/컨트롤러/리포지토리/도메인 코드, Kafka producer·consumer, Outbox, JPA/TimescaleDB/Redis/MongoDB 접근 코드, Testcontainers 테스트를 작성한다. Kotlin 구현, Spring 서비스 작성, API/엔드포인트 구현, Kafka 연동, DB 접근 코드, Gradle 멀티모듈 설정 요청 시 반드시 사용. 스펙 설계는 stockpulse-spec, 검증은 stockpulse-verify 담당이므로 제외."
---

# StockPulse 구현 — Kotlin+Spring MSA

architect 스펙을 동작하는 코드로 옮긴다. 스펙을 벗어나지 않고, 프로젝트의 기존 컨벤션을 따른다.

## 왜 컨벤션 우선인가
MSA는 서비스가 많아 스타일이 갈리면 유지보수가 무너진다. 새 패턴을 들이기 전에 기존 코드를 읽고 맞추면, 13개 서비스가 한 사람이 짠 것처럼 일관된다.

## 워크플로우

### 1. 스펙·기존 코드 파악
- `_workspace/{wbs}_architect_spec.md`를 읽어 계약을 확인한다
- 같은/인접 서비스의 기존 코드가 있으면 먼저 읽고 패턴·네이밍·패키지 구조를 파악한다
- 스펙에 모호한 점이 있으면 구현 전에 architect에게 질문한다 (추측 구현 금지)

### 2. 구현
스펙의 계약을 그대로 코드로 옮긴다:
- **REST**: 컨트롤러 → 서비스 → 리포지토리 계층. 응답 DTO는 스펙의 필드·타입과 정확히 일치
- **Kafka**: producer는 스펙 이벤트 스키마대로 발행, consumer는 동일 타입으로 역직렬화. 트랜잭션 보장이 필요하면 Outbox 패턴 적용
- **DB**: 스펙 테이블 정의대로 엔티티/마이그레이션 작성. 저장소 역할 분리(PG=트랜잭션, TimescaleDB=시계열 시세, Redis=캐시/실시간, Mongo=문서) 준수

### 3. 외부 API 연동 시 (KIS/네이버/DART)
코드를 쓰기 **전에** 실제 접근 가능 여부를 먼저 확인한다:
- 발급된 키로 실제 1회 호출이 되는지 테스트
- 인코딩(특히 한글/EUC-KR 가능성), 인증 헤더, rate limit 확인
- 검증 후에만 클라이언트 코드를 작성한다

### 4. 테스트 작성
- 핵심 로직에 단위 테스트, 외부 의존(DB/Kafka)은 Testcontainers 통합 테스트
- 컴파일·테스트가 통과하는 상태로 마무리

### 5. 보고
- 변경/생성 파일과 요약을 `_workspace/{wbs}_backenddev_report.md`에 기록
- qa에게 검증 대상 파일 목록과 함께 구현 완료를 알린다

## 작업 원칙
- **스펙 범위만 구현.** 스펙에 없는 기능을 임의 추가하지 않는다
- Kotlin idiomatic 스타일 (data class, null 안전성, 확장함수 적절히)
- 기존 패턴과 다른 선택을 할 때는 이유를 보고에 남긴다
- 빌드 실패 시 1회 자체 수정, 재실패면 에러 로그와 함께 리더에게 보고 (대규모 변경으로 덮지 않는다)

## Gradle 멀티모듈 주의
- 루트 `settings.gradle.kts`에 모듈 등록, 공통 의존은 `common` 모듈로
- 서비스별 `build.gradle.kts`는 필요한 의존만 (불필요한 스타터 추가 금지)

## 체크리스트
- [ ] 스펙의 계약(필드·타입·에러)과 코드가 정확히 일치
- [ ] 기존 컨벤션 따름 (또는 변경 이유 기록)
- [ ] 외부 API는 선검증 후 구현
- [ ] 컴파일·테스트 통과 상태
- [ ] 변경 파일 목록을 보고에 기록하고 qa에 알림
