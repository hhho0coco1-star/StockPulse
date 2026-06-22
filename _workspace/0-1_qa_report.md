# QA 리포트: WBS 0-1 Gradle 멀티모듈 골격

> 검증 수행: 오케스트레이터(qa 서브 에이전트가 서버 과부하 529로 시작 실패하여 리더가 직접 stockpulse-verify 절차로 검증).

## 최종 판정: **PASS** (9/9 DoD 충족, 경미한 개선 권고 1건)

## DoD 항목별 결과
| 항목 | 내용 | 결과 |
|------|------|------|
| D1 | 루트 빌드 파일 6종 존재 | PASS |
| D2 | rootProject.name="stockpulse" + common include + services 미등록 주석 | PASS |
| D3 | common/build.gradle.kts + main/test 소스 디렉토리(.gitkeep) | PASS |
| D4 | services/ 디렉토리(.gitkeep) | PASS |
| D5 | kotlin=1.9.25, springBoot=3.3.5, springDependencyManagement=1.1.6 | PASS |
| D6 | JDK 21 toolchain 고정 (JavaLanguageVersion.of(21)) | PASS |
| D7 | `./gradlew :common:build` → BUILD SUCCESSFUL, bootJar SKIPPED | PASS |
| D8 | `./gradlew projects` → Root 'stockpulse' + ':common' | PASS |
| D9 | 라이브러리 jar 생성(common-0.0.1-SNAPSHOT-plain.jar), bootJar 비활성 | PASS |

## 실행 증거
```
$ ./gradlew projects --console=plain
Root project 'stockpulse'
\--- Project ':common'
BUILD SUCCESSFUL in 2s

$ ./gradlew :common:build --console=plain
> Task :common:bootJar SKIPPED
> Task :common:jar UP-TO-DATE
> Task :common:build
BUILD SUCCESSFUL in 3s

$ ls common/build/libs/
common-0.0.1-SNAPSHOT-plain.jar
```

## 경계면/정합성
- settings.gradle.kts include 대상(`common`) ↔ 실제 `common/` 디렉토리: 일치
- libs.versions.toml 버전 ↔ 스펙 §3-1: 일치
- build.gradle.kts JDK 21 toolchain ↔ 스펙 D6: 일치

## 발견 이슈
1. (경미) D9 산출물 jar 이름이 `common-0.0.1-SNAPSHOT-plain.jar`로 `-plain` 분류자가 붙음. Spring Boot 플러그인 적용 모듈의 표준 동작(bootJar 비활성 시 일반 jar에 -plain 부여). 라이브러리 jar라는 의도는 충족. 깔끔한 이름을 원하면 common/build.gradle.kts에서 `tasks.jar { archiveClassifier.set("") }` 권고. → FAIL 아님.
2. (기록) backend-dev 보고서(`0-1_backenddev_report.md`)는 서버 과부하로 작성 전 중단되어 부재. 구현 자체는 완료·검증됨.

## 설계 결정 필요 (리더 → 사용자 확인)
- Java 24 ↔ Spring Boot 3.3 비호환을 toolchain JDK 21 고정으로 우회함. 현재 빌드는 Gradle이 JDK 21을 auto-provisioning하여 성공. 향후 Java 24를 직접 쓰려면 Spring Boot 3.4+로 상향 필요(설계 결정).

## 권고사항
- todo.md의 WBS 0-1 항목을 `[x]`로 갱신 가능.
- docs/09_개발환경.md를 확정 버전(JDK 21 toolchain, Kotlin 1.9.25, Spring Boot 3.3.5)으로 동기화 권고.
