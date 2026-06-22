# 스펙: 0-1 Gradle 멀티모듈 루트 + common 모듈 골격

> 작성: architect | 대상 구현자: backend-dev | 검증: qa
> 범위: **WBS 0-1 한 개만.** Gradle 멀티모듈 "빌드 골격"만 만든다. 서비스 비즈니스 코드(Gateway/Auth 등 실제 로직)는 0-5 이후 담당이므로 여기서 작성하지 않는다.

---

## 0. 한 줄 요약

루트에 Gradle 멀티모듈 빌드(루트 `settings.gradle.kts` + `build.gradle.kts` + 버전 카탈로그)를 세우고, 모든 서비스가 공유할 `common` 모듈의 **골격(빈 패키지 구조 + build.gradle.kts)** 만 만든다. services/ 하위 13개 서비스 디렉토리는 이번 단계에서는 **등록하지 않는다**(0-5에서 실제 서비스 추가 시 등록). 단, 디렉토리 자리(폴더)는 만들어 둔다.

> 용어: **멀티모듈(multi-module)** = 하나의 Gradle 빌드 안에 여러 하위 프로젝트(모듈)를 두고 공통 설정·의존성을 공유하는 구조. **버전 카탈로그(version catalog)** = 라이브러리 버전을 `gradle/libs.versions.toml` 한 파일에 모아 관리하는 Gradle 표준 기능.

---

## 1. 대상

- 서비스/모듈: **루트 빌드 프로젝트** + **`common` 모듈** (라이브러리 모듈, 실행 불가)
- 디렉토리 자리만 생성(빈 폴더): `services/` (13개 서비스가 들어갈 자리)
- 의존 작업: 없음(Phase 0의 최선행). 단 선행으로 `git init`/`.gitignore`는 이미 완료됨(todo.md 39행).
- 근거: `docs/02_시스템아키텍처.md` §3(서비스 13개), `docs/07_기술선택이유.md` §2~3(Kotlin/Coroutines/Spring), `docs/09_개발환경.md`(JDK), WBS Phase 0 "모노레포 + Gradle 멀티모듈", "공통 모듈: 응답/에러 포맷, Outbox 공통 구현 베이스".

---

## 2. 만들 디렉토리 구조

```
StockPulse/                       (= 루트 = Gradle 루트 프로젝트)
├── settings.gradle.kts           ★ 신규 — 모듈 등록
├── build.gradle.kts              ★ 신규 — 루트 공통 설정
├── gradle/
│   ├── libs.versions.toml        ★ 신규 — 버전 카탈로그
│   └── wrapper/
│       ├── gradle-wrapper.jar    ★ 신규 — gradle wrapper init 으로 생성
│       └── gradle-wrapper.properties
├── gradlew                       ★ 신규 — wrapper 실행 스크립트(*nix)
├── gradlew.bat                   ★ 신규 — wrapper 실행 스크립트(Windows)
├── common/                       ★ 신규 — 공통 라이브러리 모듈
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/stockpulse/common/   (빈 패키지, .gitkeep)
│       └── test/kotlin/com/stockpulse/common/   (빈 패키지, .gitkeep)
└── services/                     ★ 신규 — 서비스들이 들어갈 자리(.gitkeep만)
```

- **루트 그룹(group)**: `com.stockpulse`
- **base 패키지**: `com.stockpulse.common` (common 모듈), 서비스는 추후 `com.stockpulse.<service>`
- `.gitkeep`: 빈 디렉토리를 git이 추적하도록 두는 placeholder 파일(0바이트).

> 주의: 빈 폴더만 만들 곳(`services/`, 빈 패키지)에는 반드시 `.gitkeep`을 넣어라. git은 빈 디렉토리를 커밋하지 못한다.

---

## 3. 버전 결정 (★ 핵심 — docs 근거 + 호환성)

### 3-1. JDK / Gradle 툴체인 — ⚠️ 경고 포함

| 항목 | 결정 값 | 근거 / 비고 |
|------|---------|-------------|
| **Java toolchain** | **JDK 21 (LTS)** | docs/09는 "JDK 17+"만 명시. 17·21 모두 17+ 충족. Spring Boot 3.x가 권장·검증한 최신 LTS가 21이므로 **21로 상향**한다. |
| **Gradle** | **8.10 이상** (wrapper 8.10.x 권장) | Java 21 toolchain 완전 지원 버전. |
| **Kotlin** | **1.9.25** | Spring Boot 3.3.x와 검증된 조합. |
| **Spring Boot** | **3.3.x (3.3.5 권장)** | docs가 버전을 명시하지 않음(문서 누락). Kotlin 1.9.25·Java 21과 안정 조합인 3.3 라인 채택. |
| **Spring Dependency Management plugin** | **1.1.6** | Spring Boot BOM 버전 정렬용. |

> ⚠️ **호환성 경고 (반드시 읽을 것)**
> - 이 PC에는 **Java 24**가 설치되어 있다(`java -version` → 24.0.2). 그러나 **Spring Boot 3.3.x는 Java 24를 공식 지원하지 않는다**(3.3은 17~21 검증). Java 24로 빌드하면 Gradle/Kotlin/일부 라이브러리에서 경고·실패 가능성이 있다.
> - 해결책: **Gradle toolchain 기능으로 JDK 21을 명시 고정**한다(아래 build.gradle.kts 참조). toolchain을 쓰면 시스템 기본 JVM(24)과 무관하게 빌드는 21로 수행된다.
> - **전제조건**: 로컬에 **JDK 21이 설치되어 있어야 한다.** 없으면 Gradle이 자동 다운로드(toolchain auto-provisioning, Foojay resolver 플러그인 필요)하거나 수동 설치해야 한다. → backend-dev는 빌드 전 `JDK 21 설치 여부`를 먼저 확인하고, 없으면 **설치 안내 후 진행**하라. (이 항목은 qa DoD에 "JDK 21 toolchain으로 빌드 성공"으로 검증)
> - 만약 사용자가 Java 24 그대로 쓰길 원하면 → Spring Boot 3.4.x 이상으로 올려야 호환된다. 이는 **설계 결정 사항**이므로 임의 변경하지 말고 리더에게 확인 요청.

### 3-2. 버전 카탈로그(`gradle/libs.versions.toml`)에 넣을 항목

권장: **버전 카탈로그 사용 = YES.** 13개 서비스가 같은 버전을 공유해야 하므로 한 곳에서 관리하는 것이 이득. 0-1에서는 골격 단계라 최소 항목만 넣고, 서비스 추가 시 확장.

`[versions]` 블록 (값은 3-1 표 기준):
- `kotlin = "1.9.25"`
- `springBoot = "3.3.5"`
- `springDependencyManagement = "1.1.6"`

`[libraries]` 블록 (common 모듈이 당장 쓸 최소 의존만):
- `jackson-module-kotlin` — Kotlin 객체 JSON 직렬화(공통 응답/에러 포맷에 필요). 버전은 Spring BOM이 관리하므로 버전 생략 가능.
- `kotlin-reflect` — Kotlin 리플렉션(Jackson Kotlin 모듈이 요구). 버전은 Kotlin 플러그인이 정렬.

`[plugins]` 블록:
- `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`
- `kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }`  ← Spring 빈을 위한 all-open 처리(클래스 자동 open). common 골격엔 당장 불필요할 수 있으나 공통 적용 대비 등록만 해둔다.
- `spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }`
- `spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "springDependencyManagement" }`

> 참고: `[libraries]`에 SLF4J 등 추가 공통 라이브러리는 0-1 시점엔 넣지 않는다(YAGNI). 공통 모듈에 실제 코드가 들어가는 단계(공통 응답/에러/Outbox 구현)에서 추가.

---

## 4. 빌드 파일 내용 명세 (코드가 아니라 "무엇을 담아야 하는가")

> 아래는 backend-dev가 작성할 파일의 **요구사항**이다. 실제 Kotlin DSL 코드는 backend-dev가 작성.

### 4-1. `settings.gradle.kts`
- 루트 프로젝트 이름: `rootProject.name = "stockpulse"`
- 등록 모듈: **`include("common")` 만.**
- `services/` 하위 서비스는 **이번 단계에서 include하지 않는다**(아직 모듈이 없으므로). 0-5에서 서비스 추가 시 `include("services:api-gateway")` 형태로 추가될 예정 — 이 점을 주석으로 남겨라.
- 버전 카탈로그는 `gradle/libs.versions.toml`이 기본 경로이므로 별도 선언 불필요.

### 4-2. 루트 `build.gradle.kts`
- 플러그인 선언: 카탈로그의 플러그인들을 `apply false`로 선언(루트는 적용 안 하고 하위 모듈이 적용). 예: `alias(libs.plugins.kotlin.jvm) apply false` 등.
- `allprojects` 또는 `subprojects` 블록에서 공통 설정:
  - `group = "com.stockpulse"`, `version = "0.0.1-SNAPSHOT"`
  - 공통 저장소: `mavenCentral()`
- **Java toolchain 고정** (subprojects 공통): Kotlin/Java toolchain을 `JavaLanguageVersion.of(21)`로 설정. → 시스템 Java 24와 무관하게 빌드는 21 사용.
- Kotlin 컴파일 옵션: `jvmTarget = "21"`, `freeCompilerArgs = ["-Xjsr305=strict"]` (JSR-305 null 어노테이션 엄격 적용 — Spring과의 null 안정성).

### 4-3. `common/build.gradle.kts`
- 적용 플러그인: `kotlin-jvm`, `kotlin-spring`, `spring-dependency-management` (Spring BOM으로 버전 정렬).
  - ⚠️ **`spring-boot` 플러그인은 적용하되 `bootJar` 비활성·`jar` 활성**으로 둔다. common은 실행형이 아니라 **라이브러리**이므로 실행 가능한 boot jar를 만들면 안 된다.
    - 구체 요구: `tasks.bootJar { enabled = false }`, `tasks.jar { enabled = true }`.
    - (대안: spring-boot 플러그인을 빼고 `io.spring.dependency-management`만 적용해 BOM만 쓰는 방법도 가능 — backend-dev가 둘 중 하나 선택하되, **라이브러리 jar가 생성되는 것**이 요구사항.)
- 의존성(최소):
  - `implementation`: `org.jetbrains.kotlin:kotlin-reflect`, `com.fasterxml.jackson.module:jackson-module-kotlin`
  - `testImplementation`: `org.springframework.boot:spring-boot-starter-test`, `org.jetbrains.kotlin:kotlin-test-junit5`
- 테스트 태스크: `tasks.test { useJUnitPlatform() }` (JUnit5 사용).

> common 모듈 안에는 **실제 클래스 파일을 만들지 않는다**(골격이므로). 패키지 디렉토리 + `.gitkeep`만. 실제 공통 응답/에러/Outbox 베이스 코드는 WBS Phase 0의 "공통 모듈" 별도 작업에서 채운다.

---

## 5. 데이터 모델 / 인터페이스 계약

- 해당 없음. 이 작업은 **빌드 골격**이며 REST/Kafka/DB 계약을 만들지 않는다.

---

## 6. 완료 기준 (DoD) — qa가 PASS/FAIL 판정 가능

- [ ] **D1** 루트에 `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`가 모두 존재한다.
- [ ] **D2** `settings.gradle.kts`에 `rootProject.name = "stockpulse"`가 있고 **`common` 모듈 1개가 include되어 있다**(services 미등록 + 미등록 사유 주석 존재).
- [ ] **D3** `common/build.gradle.kts`가 존재하고, `common/src/main/kotlin/com/stockpulse/common/`, `common/src/test/kotlin/com/stockpulse/common/` 디렉토리가 존재한다(각각 .gitkeep 포함).
- [ ] **D4** `services/` 디렉토리가 존재한다(.gitkeep 포함, 하위 서비스는 없어도 됨).
- [ ] **D5** `libs.versions.toml`에 kotlin=1.9.25, springBoot=3.3.5(또는 3.3.x), springDependencyManagement 버전이 명시되어 있다.
- [ ] **D6** 빌드 toolchain이 **JDK 21**로 고정되어 있다(루트 build.gradle.kts에 `JavaLanguageVersion.of(21)` 존재).
- [ ] **D7** `./gradlew :common:build` (또는 `gradlew.bat :common:build`)가 **BUILD SUCCESSFUL**로 끝난다. (JDK 21 toolchain으로 수행, 시스템 Java 24와 무관하게 성공해야 함)
- [ ] **D8** `./gradlew projects` 실행 시 root 프로젝트와 `common` 서브프로젝트가 목록에 나온다.
- [ ] **D9** common 빌드 산출물이 **일반 라이브러리 jar**다(실행형 boot jar가 아님). → `common/build/libs/`에 `common-0.0.1-SNAPSHOT.jar`가 생성되고 bootJar는 비활성.

> qa 검증 절차 권장: D1~D6은 파일/내용 확인, D7~D9는 실제 `gradlew` 실행 후 출력으로 판정.

---

## 7. 참고 문서 + 문서 누락/주의사항

### 근거 문서 경로
- `c:\study\StockPulse\todo.md` (Phase 0, 0-1 항목 / git init 선행 완료)
- `c:\study\StockPulse\docs\02_시스템아키텍처.md` §3 서비스 13개 목록 (services/ 자리 근거)
- `c:\study\StockPulse\docs\07_기술선택이유.md` §2~3 (Kotlin+Coroutines, Spring Boot/Cloud Gateway 선택)
- `c:\study\StockPulse\docs\09_개발환경.md` 사전 요구사항(JDK 17+)
- `c:\study\StockPulse\docs\05_WBS_개발계획.md` Phase 0 작업분해(모노레포 + Gradle 멀티모듈, 공통 모듈 역할)

### ⚠️ 문서 누락 / 결정 필요 (리더 확인 권장)
1. **Spring Boot / Kotlin / Gradle 정확한 버전이 docs에 없음.** 09_개발환경은 "JDK 17+"만 명시. → 본 스펙에서 Spring Boot 3.3.5 / Kotlin 1.9.25 / Gradle 8.10 / JDK 21을 **제안 확정**. 추후 09 문서에 이 버전을 반영 필요(구현 후 architect가 동기화).
2. **로컬 환경 Java 24 vs Spring Boot 3.3 비호환.** toolchain JDK 21 고정으로 우회하나, **JDK 21이 로컬에 설치되어 있어야 함.** 미설치 시 빌드 실패 → backend-dev는 빌드 전 JDK 21 설치 확인 필수.
3. **멀티모듈 vs 서비스별 독립 빌드 선택.** WBS는 "Gradle 멀티모듈(또는 서비스별 디렉토리)"로 양자 허용. → 본 스펙은 **단일 루트 멀티모듈**로 확정(공통 버전/설정 공유 이점, 13개 서비스 버전 정합). 만약 서비스별 완전 독립 배포를 위해 별도 빌드를 원하면 설계 변경 필요 — 리더 확인.
4. **base 패키지명 컨벤션이 docs에 명시 없음.** → `com.stockpulse.*`로 제안. 다른 컨벤션 원하면 변경.
5. 09_개발환경의 "JDK 17+"는 본 결정(21)과 충돌하지 않으나(21도 17+), 문서를 "JDK 21"로 구체화 권장.
