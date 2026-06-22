---
name: stockpulse-dev
description: "StockPulse MSA 개발 에이전트 팀을 조율하는 오케스트레이터. WBS 작업(예: 0-1 Gradle 멀티모듈, 시세 파이프라인, 모의투자 Saga 등)을 받아 architect(스펙)→backend-dev(Kotlin/Spring 구현)→qa(경계면 검증) 흐름으로 완료한다. StockPulse 개발/구현/기능 추가 요청, WBS 작업 진행, 서비스/API/Kafka/DB 구현, 'StockPulse OO 만들어줘', '다음 WBS 진행' 요청 시 반드시 사용. 후속 작업: 구현 수정, 부분 재실행, 업데이트, 보완, 다시 실행, 이전 결과 개선, '검증 다시', '스펙만 다시' 요청 시에도 반드시 이 스킬을 사용. 단순 질문·설계 상담은 직접 응답 가능."
---

# StockPulse Dev Orchestrator

StockPulse(Kotlin+Spring MSA) 개발 에이전트 팀을 조율하여 WBS 작업 1개를 스펙→구현→검증까지 완료하는 통합 스킬.

## 실행 모드: 에이전트 팀

설계·구현·검증은 서로 피드백을 주고받아야 품질이 오른다(생성-검증 루프). 따라서 에이전트 팀으로 구성하고, 산출물은 `_workspace/` 파일로, 조율은 공유 작업 목록과 SendMessage로 한다.

## 에이전트 구성

| 팀원 | 타입 | 모델 | 역할 | 스킬 | 출력 |
|------|------|------|------|------|------|
| architect | architect (커스텀) | opus | WBS→스펙, 문서 동기화 | stockpulse-spec | `_workspace/{wbs}_architect_spec.md` |
| backend-dev | backend-dev (커스텀) | sonnet | Kotlin/Spring 구현 | stockpulse-impl | 소스 파일 + `_workspace/{wbs}_backenddev_report.md` |
| qa | qa (커스텀, general-purpose 기반) | opus | 경계면 검증, 빌드/테스트 | stockpulse-verify | `_workspace/{wbs}_qa_report.md` |

## 워크플로우

### Phase 0: 컨텍스트 확인 (후속 작업 지원)

`c:\study\StockPulse\_workspace/` 존재 여부로 실행 모드를 결정한다:
- **미존재** → 초기 실행. Phase 1로
- **존재 + 부분 수정 요청**(예: "스펙만 다시", "검증만 다시") → 부분 재실행. 해당 에이전트만 재호출하고 이전 산출물 경로를 프롬프트에 포함해 피드백 반영
- **존재 + 새 WBS 작업** → 새 실행. 기존 `_workspace/`는 그대로 두고(작업별 파일명이 `{wbs}_`로 구분됨) 새 작업 진행

### Phase 1: 준비
1. 대상 WBS 작업 번호 확인 (사용자가 지정하지 않으면 `todo.md`의 다음 미완료 작업 제안 후 확인받기)
2. `c:\study\StockPulse\_workspace/` 없으면 생성
3. 대상 작업과 관련 `docs/` 경로를 정리

### Phase 2: 팀 구성
```
TeamCreate(
  team_name: "stockpulse-team",
  members: [
    { name: "architect",   agent_type: "architect",   model: "opus",
      prompt: "stockpulse-spec 스킬을 사용해 지정된 WBS 작업의 구현 스펙을 작성하라. docs/를 근거로 REST/Kafka/DB 계약과 DoD를 확정해 _workspace/{wbs}_architect_spec.md에 저장하라." },
    { name: "backend-dev", agent_type: "backend-dev", model: "sonnet",
      prompt: "stockpulse-impl 스킬을 사용해 architect 스펙대로 Kotlin/Spring 코드를 구현하라. 스펙 범위만 구현하고 완료 시 qa에 검증 대상 파일을 알려라." },
    { name: "qa",          agent_type: "qa",          model: "opus",
      prompt: "stockpulse-verify 스킬을 사용해 구현을 스펙·설계와 대조하고 경계면 shape을 교차 비교하라. 빌드/테스트를 실제 실행해 PASS/FAIL을 _workspace/{wbs}_qa_report.md에 판정하라." }
  ]
)
```
작업 등록:
```
TaskCreate(tasks: [
  { title: "스펙 작성",  assignee: "architect" },
  { title: "구현",      assignee: "backend-dev", depends_on: ["스펙 작성"] },
  { title: "검증",      assignee: "qa",          depends_on: ["구현"] }
])
```

### Phase 3: 스펙→구현→검증 (팀원 자체 조율)
**통신 규칙:**
- architect는 스펙 완료 시 backend-dev에 SendMessage로 알리고 경로를 전달
- backend-dev는 구현 중 스펙 모호성을 architect에 질문, 완료 시 qa에 검증 대상 파일 알림
- qa는 FAIL 시 backend-dev에 줄 번호와 함께 재작업 요청(최대 2~3회), 설계-구현 불일치면 architect에 보고
- 리더(오케스트레이터)는 TaskGet으로 진행을 모니터링하고 막힌 팀원을 지원

### Phase 4: 통합·마무리
1. qa의 최종 판정(PASS/FAIL) 확인
2. PASS면: `todo.md`의 해당 WBS 항목을 `[x]`로 갱신, architect가 `docs/` 동기화 수행
3. FAIL이고 재시도 소진 시: 미해결 이슈를 보고에 명시하고 todo는 갱신하지 않음
4. 결과를 사용자에게 요약 보고

### Phase 5: 정리
1. 팀원 종료(SendMessage) 후 TeamDelete
2. `_workspace/`는 보존 (사후 검증·감사 추적용)
3. 사용자에게 실행 후 피드백 요청 (개선점/팀 구성 변경 희망 여부)

## 데이터 흐름
```
[리더] → TeamCreate
  architect → spec.md ──SendMessage──> backend-dev → 소스+report.md ──알림──> qa → qa_report.md
                                                                                   │
                                          FAIL 시 줄번호 피드백 ←──────────────────┘
                                                                                   ↓ PASS
                                              [리더] todo.md 갱신 + docs 동기화 → 보고
```

## 에러 핸들링
| 상황 | 전략 |
|------|------|
| 스펙 단계서 docs 모순/누락 | architect가 양쪽 출처 병기 → 리더가 사용자에 결정 요청 |
| 빌드/컴파일 실패 | backend-dev 1회 자체 수정, 재실패 시 에러 로그와 함께 리더 보고 |
| qa FAIL 반복(2~3회 초과) | 재작업 중단, 미해결 이슈 명시하고 todo 미갱신 |
| 외부 API 호출 불가 | 추정 구현 금지, "검증 불가"로 보고하고 키 발급/네트워크 확인 요청 |
| 팀원 중지/타임아웃 | 리더가 감지 → 재시작 또는 부분 결과로 진행, 누락 명시 |

## 테스트 시나리오
### 정상 흐름
1. 사용자: "StockPulse WBS 0-1 진행해줘"
2. Phase 1: 대상 `0-1 Gradle 멀티모듈 골격` 확정, `_workspace/` 생성
3. Phase 2: 3명 팀 구성 + 3개 작업 등록
4. Phase 3: architect 스펙 → backend-dev 구현 → qa 빌드 검증
5. Phase 4: qa PASS → todo.md `0-1` 체크, docs 동기화
6. 예상 결과: Gradle 루트/common 골격 + `_workspace/0-1_*` 산출물 3종

### 에러 흐름
1. Phase 3에서 qa가 빌드 FAIL 판정(엔티티-스키마 컬럼 타입 불일치, 파일:줄 명시)
2. qa가 backend-dev에 재작업 요청
3. backend-dev 수정 후 qa 재검증 → PASS
4. 2~3회 내 미해결 시: "0-1 일부 미완(컬럼 타입 불일치 잔존)"으로 보고, todo 미갱신
