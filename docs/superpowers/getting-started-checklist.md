# 시작 체크리스트 — M1 진입 (2026-06-07 기준)

> 동반 문서: [`specs/2026-06-06-kpop-concert-dashboard-design.md`](specs/2026-06-06-kpop-concert-dashboard-design.md)
> 진행 추적용. 각 항목 완료 시 `[ ]` → `[x]`. 섹션 번호(§N)는 design.md 참조.
>
> **대원칙(design §11)**: 코드는 직접 작성. AI는 개념 설명 / 에러 해석 / AWS 디버깅에만.
> 이 체크리스트도 진행 추적 문서이지 코드 대행이 아님.

---

## STEP 0 — 지금 바로 (코딩 0줄, 외부 지연 의존)

승인·전파에 시간이 걸려 "나중에 하면 늦는" 항목. 코딩과 무관하게 제일 먼저. (design §3·§9)

### 0-A. KOPIS 서비스키 (§3 1순위 함정)

- [ ] kopis.or.kr → OpenAPI 신청
- [ ] 신청서 기입 (아래 문안 사용)
- [ ] 승인 → 32-hex 서비스키 발급 확인
- [ ] 마이페이지에서 **rate limit 숫자** 확인 → design.md §3 "미상" 칸 채우기
- [ ] 키는 환경변수 `KOPIS_API_KEY`로만 보관 (코드/커밋 금지)

**신청서 문안 (복사용)**

활용 목적:

```
개인 학습 및 포트폴리오 목적의 공연(대중음악) 정보 집계 대시보드 개발.
KOPIS 공연목록(pblprfr) API로 대중음악(CCCD) 장르 공연 데이터를 주기적으로
수집·저장하여 기간/지역/장르별 조회 기능을 구현하는 Spring Boot 백엔드
학습 프로젝트입니다. 비영리·비상업 용도이며 데이터 재판매·재배포는 하지 않습니다.
```

서비스 URL: GitHub repo URL (1순위) / 없으면 `개인 학습 프로젝트(로컬 개발 단계, 추후 AWS 배포 예정)`.
`localhost`/`127.0.0.1`은 적지 말 것(외부 검증 불가 → 반려 사유).

### 0-B. AWS 비용 가드 (§9 "비용폭탄 80% 차단")

- [ ] AWS Budgets: $1~5 임계 + 이메일 알림 생성
- [ ] Cost Anomaly Detection 켜기
- [ ] 내 계정이 **legacy 12개월/750h** vs **2025-07-15 이후 신규 크레딧($200/6개월)** 중 무엇인지 Billing에서 확인 (한국어 튜토리얼은 대부분 구 모델 전제 → 그대로
  따르면 틀림)
- [ ] 리전 **Seoul(ap-northeast-2)** 확인

---

## STEP 1 — M1 스캐폴드 (오늘 가능, 키 없어도 됨)

design §8 M1 = Spring Boot + Git + 시크릿/프로필 + Hello REST + EC2 조기 1회 배포.

### 1-1. Spring Initializr (start.spring.io)

- [x] Project=**Gradle - Groovy**, Language=**Java**, Java=**17**, Packaging=**Jar**
- [x] **Spring Boot 4.0.6 채택** (2026-06-07; 권장은 3.5였으나 최신 선택) — Jackson 3·starter 모듈화 차이 인지
- [x] Group=`com.sjh` / Artifact=`kpop-concert-dashboard`
- [x] 의존성: webmvc·Data JPA·H2(+h2console)·Thymeleaf·Validation·Lombok·starter-test
- [x] **빌드 검증 그린** (JDK 17, `:test` 통과 = 컨텍스트가 H2로 기동 확인)
- [ ] 주의: M3 XML은 **Jackson 3** — `tools.jackson.dataformat:jackson-dataformat-xml` 좌표(`com.fasterxml` 아님), import `tools.jackson.*` (§3 함정)

### 1-1b. JDK 17 환경 (셸 기본이 Java 8이라 필수)

- [ ] 셸 기본 `JAVA_HOME`=Java 8(SDKMAN current) → 터미널 `./gradlew`가 안 뜸(Gradle 9 = JVM 17+ 필요). IntelliJ는 자체 JDK라 무관
- [ ] 영구화: `sdk install java 17.x-tem` → 루트에 `.sdkmanrc`(`java=17.x-tem`) + `sdkman_auto_env=true` → cd 시 자동 17/나가면 8 (주력 Java 8 무손상)
- [x] (임시 검증은 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`로 그린 확인함)

### 1-2. Git / GitHub / 시크릿 (§7)

- [x] `git init` (IntelliJ 수행, 파일 staged 상태) — **첫 커밋은 아직**
- [x] `.gitignore` build/·.idea·*.iml 커버 확인 / [ ] `.env` 한 줄 추가(키를 .env로 둘 경우)
- [ ] GitHub 새 repo + remote + 첫 커밋 push
- [ ] API 키는 `${KOPIS_API_KEY}` 환경변수로만 (코드에 직접 기재 금지 → push 시 GitHub secret-scanning이 키 무효화 → 재발급=또 승인대기)

### 1-3. 프로파일 골격 (§7)

- [ ] `application.yml`(공통) + `application-dev.yml`(H2) + `application-prod.yml`(MySQL)
- [ ] `spring.jpa.open-in-view=false` 명시 (§7, LazyInit 예외를 드러내 fetch 경계 학습)
- [ ] dev `ddl-auto=update` / prod `validate`

### 1-4. Hello REST + 로컬 구동

- [x] `GET /hello` 컨트롤러 (HelloController: @RestController + @GetMapping + @RequestParam(defaultValue) + String.format) → curl 200 `Hello 지현!`/`Hello World!` 확인
- [ ] `./gradlew bootRun`으로 기동 확인 (내장 톰캣·Gradle 체득)
- [ ] 함정: import 빨간 줄 = `javax.*` 예제 그대로 복사한 경우. Spring Boot 3은 **`jakarta.*`** (§2)

### 1-5. EC2 조기 1회 배포 (§8·§9, 저위험에서 파이프라인 선통과)

- [ ] EC2 free-tier 단일 인스턴스(Seoul t2/t3.micro)
- [ ] **스왑 2GB 필수** + 실행 시 `-Xmx384m` (§9)
- [ ] 빌드는 **로컬/GitHub Actions** (인스턴스 빌드=Gradle OOM 금지)
- [ ] jar를 scp → `systemd` unit으로 기동 → `journalctl` 확인
- [ ] 보안그룹 80/443, Public IPv4 과금($3.6/월)·중지 인스턴스 EIP 방치 주의
- [ ] 세션 종료 시 EC2 stop (크레딧 모델이면 상시 가동 지양)

---

## STEP 2 — 키 대기 중 병행 (= M2 진입, §3·§8)

키 승인 전에도 멈추지 않는 법.

- [ ] `Concert` 단일 엔티티(§5) — `kopisId` UNIQUE, `record` 불가(@Entity no-arg 필요), `equals/hashCode`는 `kopisId` 기반 직접 구현(§7)
- [ ] H2 + Spring Data JPA repository CRUD
- [ ] **더미 XML**(`<dbs><db>...</db></dbs>`)로 XmlMapper 파싱·매핑 선개발 → 키 오면 RestClient 호출만 교체
- [ ] `@DataJpaTest`로 **upsert 중복방지** 테스트 1개(§10) — 학습+포트폴리오 동시
- [ ] `show-sql=true`로 "save() 없이 왜 UPDATE가 나가는가"(dirty checking) 직접 관찰(§7)

---

## AI 사용 경계 재확인 (§11 — 프로젝트 생명선)

완성 기준 = "면접에서 내 코드를 라인 설명 가능".

- **금지**: "엔티티 짜줘 / 수집기 써줘" 같은 대신-작성.
- **허용**: 개념 설명(JPA dirty checking 등), 에러 메시지 해석, AWS 네트워킹/보안그룹 디버깅, M3 XML 파싱·M5 RDS dialect stall 풀기.
- 판정 한 줄: **"이 코드를 면접에서 설명 가능한가?" Yes면 OK.**

막혔을 때 물어볼 예시(허용 범위):

- "이 import가 왜 안 잡혀?" (→ jakarta 네임스페이스)
- "show-sql에 UPDATE가 두 번 나가는데 왜?" (→ dirty checking + 명시 save 중복)
- "EC2 80포트가 안 열려" (→ 보안그룹/방화벽)

---

## 진행 메모

- 2026-06-07: 프로젝트 생성(SB 4.0.6 / Java 17 / com.sjh). 빌드+테스트 그린(JDK 17). KOPIS 인증키 발급받음(저장방식·rate limit 확인 잔여).
- 결정: Spring Boot 4.0.6 채택(권장 3.5 대비 최신 선택) → Jackson 3·starter 모듈화 함정 design §3·§7 동기화 완료.
- 환경: 셸 기본 Java 8 → 이 프로젝트만 JDK 17 필요(.sdkmanrc 영구화 잔여).
- 앱 기동 확인: IntelliJ Run → `localhost:8080` 응답(404, 엔드포인트 0 = 정상; curl=JSON / 브라우저=HTML content negotiation).
- Hello REST 완료 + 개선 적용(defaultValue="World" + String.format): `/hello`→`Hello World!`, `?name=지현`→`Hello 지현!` (curl 200). 다음 = 첫 커밋.
- (여기에 막힌 지점·결정·발급된 rate limit 숫자 등 계속 기록)
