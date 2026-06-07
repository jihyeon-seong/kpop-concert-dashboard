# KPOP 콘서트 집계 대시보드 — Design (2026-06-06)

> 학습 프로젝트. 목적은 "포트폴리오가 있다"가 아니라 **"면접에서 이 Spring Boot 코드를 설명할 수 있다"**.
> 따라서 핵심 제약: **직접 학습·구현** (AI는 개념 설명/에러 해석/AWS 디버깅에만, "대신 짜줘"는 금지).
> KOPIS API 스펙은 2026-06-06 live curl로 직접 검증함(아래 §3).

---

## 1. 한 줄 정의

KOPIS 공개 API로 **대중음악(KPOP) 공연**을 주기 수집·저장하고, 기간·지역·장르로 조회·필터하는 Spring Boot 백엔드 + 최소 화면. AWS 배포 + GitHub 커밋 이력.

타깃: 엔터테크/플랫폼 백엔드 채용에서 "모던스택 production 증거 + 내가 설명 가능"을 만드는 것.

## 2. 스택 (확정)

| 항목      | 값                                           | 비고                                                                                   |
|---------|---------------------------------------------|--------------------------------------------------------------------------------------|
| 언어      | Java 17 (LTS)                               | Java 8 → 17 갭 학습. **Spring Boot 3 = `jakarta.*` 네임스페이스**(옛 `javax.*` 예제 복붙 시 컴파일 에러) |
| 프레임워크   | Spring Boot 4.0.x (채택 2026-06-07)         | Jackson 3·starter 모듈화 차이 §3·§7. RestClient는 3.2+ 도입·4.0 포함                                                                |
| 외부 HTTP | **RestClient**                              | SB 3.2+ 동기 클라이언트. WebClient(reactive 과함)/RestTemplate(maintenance) 아님                |
| 영속성     | Spring Data JPA + Hibernate                 | MyBatis → JPA 전환이 핵심 학습                                                              |
| DB      | 로컬 H2 → 로컬 Docker MySQL(M4) → AWS MySQL(M5) | dialect 차이를 RDS가 아닌 로컬에서 먼저 잡기                                                       |
| 빌드      | Gradle                                      | Spring Initializr가 생성                                                                |
| 화면      | Thymeleaf 목록/상세 2페이지                        | 백엔드 집중, 별도 프론트 학습 회피                                                                 |
| 배포      | AWS EC2 + jar + systemd                     | EB(ALB 과금)/Docker(1GB OOM) 아님                                                        |
| HTTPS   | Caddy + nip.io/DuckDNS                      | LE는 bare IP 인증서 발급 안 함                                                               |
| CI      | GitHub Actions (러너 빌드 → scp → ssh restart)  | 인스턴스 빌드 OOM 회피                                                                       |

## 3. KOPIS OpenAPI 스펙 (2026-06-06 live curl 검증, 8/9 항목)

- **공연목록**: `GET http://www.kopis.or.kr/openApi/restful/pblprfr` — **HTTP 평문**(https 아님). 코드/EC2 outbound에서 http 직접 호출.
- **공연상세**: `GET .../openApi/restful/pblprfr/{mt20id}` (MVP는 목록만으로 충분, 상세/시설은 2차)
- **인증**: `?service={32-hex 서비스키}` query 파라미터 (헤더/OAuth 아님). kopis.or.kr 신청+승인 발급.
- **응답: XML 전용** (JSON 없음). 구조 `<dbs><db>...</db></dbs>` (db=공연 1건). → `jackson-dataformat-xml`(XmlMapper) DTO 매핑 필수. *
  *이게 첫 실질 난관**.
- **목록 응답 필드**: `mt20id`(공연ID=upsert key), `prfnm`(공연명), `prfpdfrom`/`prfpdto`(기간), `fcltynm`(시설명), `genrenm`(장르명),
  `area`(**한글 시도명** '서울특별시'), `poster`(절대 URL), `prfstate`(공연예정/중/완료).
- **대중음악 장르코드 = `shcate=CCCD`** (live 검증: CCCD 조회 시 10건 전부 `genrenm='대중음악'`). 코드표: AAAA 연극/CCCA 클래식/CCCC 국악/**CCCD 대중음악
  **/EEEA 복합/GGGA 뮤지컬.
- **페이징**: `cpage`(1-base)/`rows`(≤100). **`totalCount` 없음** → "rows보다 적게 오면 끝" 루프.
- **날짜**: 요청 `stdate`/`eddate` = `YYYYMMDD`, 응답 `prfpdfrom`/`prfpdto` = **`YYYY.MM.DD`** (불일치!). → 요청/응답
  DateTimeFormatter 분리.
- **rate limit**: 숫자 미상(키 발급 후 마이페이지 확인). → **증분 수집 전제**.

**함정 (design 반영됨)**:

- **Spring Boot 4.0 = Jackson 3** (4.0.6 채택 2026-06-07): `jackson-dataformat-xml` 좌표가 `com.fasterxml.jackson.dataformat` → **`tools.jackson.dataformat`**, import 네임스페이스도 `com.fasterxml.*` → `tools.jackson.*`. 튜토리얼/SO 대부분 Jackson 2(`com.fasterxml`)라 그대로 복사 시 좌표·import 미스매치 — 위 "첫 실질 난관"(XML 파싱, M3)에 겹침. 빌드는 `tools.jackson.dataformat:jackson-dataformat-xml` 의존성 + import만 맞추면 됨(Boot 4 자동구성이 클래스패스의 Jackson 3 XML 모듈을 잡음).
- 키 발급 승인 지연 → 키 신청을 **가장 먼저**, 대기 중 더미 XML로 파서/엔티티 선개발.
- 빈 응답도 HTTP 200 + `<dbs></dbs>`, 에러도 XML 본문 → **status만으로 성공판단 금지**, XML 파싱 검증.
- 장르 경계: 일부 대형 콘서트가 복합(EEEA)으로 분류될 수 있음 → MVP는 CCCD만, 필요시 보정.
- 시설 좌표/주소는 목록에 없음(별도 prfplc API, list→detail→facility 3단계 N+1) → **MVP 지역필터는 `area` 한글 문자열로** (호출 절약).

## 4. 아키텍처 (레이어 분리, 각 단위 1책임)

```
collector  →  KOPIS RestClient 호출 + @Scheduled 증분 수집
service    →  XML→DTO 변환, upsert(by kopisId), 조회·필터 로직
domain     →  Concert JPA 엔티티 (단일)
repository →  Spring Data JPA (Pageable)
controller →  REST(목록/상세/필터) + view 컨트롤러
view       →  Thymeleaf 목록·상세
```

## 5. 데이터 모델 (YAGNI — 단일 엔티티)

`Concert`:

- `id` Long (PK, IDENTITY)
- `kopisId` String **UNIQUE** (= mt20id, upsert 기준)
- `title`(prfnm), `startDate`/`endDate` LocalDate(응답 `yyyy.MM.dd` 파싱), `venueName`(fcltynm), `genre`(genrenm), `region`(
  area 한글 시도명 그대로), `posterUrl`(poster), `state`(prfstate)

연관관계(Venue/Artist 분리)는 **이 프로젝트 범위 밖**. 단일 엔티티 + 파생 쿼리만 → M2 1주 붕괴 방지. (집계 대시보드라 연관 불필요.)

## 6. 핵심 기능 + YAGNI 경계

- **수집**: `@Scheduled` 일 1회. `shcate=CCCD` 고정, 최근 7~14일 `stdate~eddate` **증분**, `cpage` 1부터 `rows=100` 빈응답까지 루프, 호출 간 짧은
  sleep(트래픽 보호).
- **upsert**: `findByKopisId` → 있으면 필드 갱신(dirty checking)/없으면 `save`. `kopisId` UNIQUE를 idempotency 안전망으로.
- **조회**: 기간/지역(area)/장르 필터 + `Pageable`. **DB 조회만**(매 조회마다 외부 API 호출 금지).
- **화면**: Thymeleaf 목록 + 상세.
- **제외(YAGNI)**: 회원·인증·알림·관리자·시설좌표·지도 → (B) 확장 시.

## 7. Spring Boot / JPA 함정 체크리스트 (MyBatis 경험자)

- **MyBatis→JPA 멘탈모델**: `@Transactional` 안에서 managed 엔티티 setter 호출 → `save()` 없이도 커밋 시 UPDATE 자동(dirty checking). **명시
  UPDATE 한 번 더 호출 금지**. 코드 전 `show-sql`로 "save 없이 왜 UPDATE 나가는가" 직접 관찰.
- `spring.jpa.open-in-view=false` 명시 (끄면 LazyInit 예외가 드러나 fetch 경계 학습).
- 엔티티: `record` 불가(@Entity는 no-arg 생성자 필요). `@NoArgsConstructor(access=PROTECTED)`, id는 `Long`(wrapper),
  `equals/hashCode`는 자연키 `kopisId` 기반 직접 구현(@Data/@EqualsAndHashCode 전체필드 금지).
- `@Scheduled` 메서드는 트랜잭션 없음 → 별도 서비스 `@Transactional` 메서드로 위임.
- upsert TOCTOU race는 단일 인스턴스라 무시 가능 + UNIQUE 안전망. 락은 YAGNI.
- `Page<T>`는 count 쿼리 추가 실행. 컨트롤러는 엔티티 아닌 **DTO 반환**(SB 3.3+ PageImpl 직렬화 경고 회피).
- 프로파일: `application.yml`(공통)+`-dev`(H2)+`-prod`(MySQL). 키는 `${KOPIS_API_KEY}` 환경변수. dev `ddl-auto=update` / *
  *prod `validate`**(create/update 금지).

## 8. 학습 마일스톤 (8~10주, walking skeleton — research 교정 반영)

> "1주" = 풀타임 아닌 **약 10시간**(정규직 SI + 코테 병행 실가용 주 8~12시간 기준). 5~7주는 무마찰 가정.
> 각 단계 git 커밋 → "꾸준히 빌드한 흔적".

| 단계            | 내용                                                                                      | 학습 포인트                                                    |
|---------------|-----------------------------------------------------------------------------------------|-----------------------------------------------------------|
| **M1** (1~2주) | Spring Boot + Git/GitHub + **.gitignore/시크릿/프로필** + Hello REST + **EC2 조기 1회 배포**       | 프로젝트 구조·Gradle·**배포 파이프라인을 저위험에서 먼저**                     |
| **M2** (1~2주) | `Concert` 단일 엔티티 + H2 + repository CRUD + repository 테스트 1개                             | JPA(dirty checking/1차캐시) — **의도적으로 얕게**                   |
| **M3** (2~3주) | KOPIS 단건 fetch→XML 파싱→저장(E2E 먼저) → upsert → `@Scheduled`+예외처리                           | RestClient·XmlMapper·@Transactional·upsert. **숨은 난이도 최대** |
| **M4** (1~2주) | 조회필터+페이징+목록/상세 화면 + `@RestControllerAdvice` 전역 예외 + **로컬 Docker MySQL 도입**(dialect 선검증) | 파생쿼리·Pageable·Thymeleaf                                   |
| **M5** (1~2주) | AWS MySQL 전환 + 재배포(M1 경험으로 증분) + 인덱스(date/region/genre) + README                        | 프로파일·배포 마무리                                               |

- **조기 동력**: M3의 단건 fetch를 CRUD 완벽화보다 앞당겨 "실 KPOP 데이터가 보인다"를 빨리 경험.
- **코테 병행**: 주당 N문제 별도 트랙(이 프로젝트와 독립, 둘 다 step2).

## 9. AWS 배포 (비용폭탄 가드 우선)

- **첫날 가드**: AWS
  Budgets **$1~5 임계 + 이메일 알림** + Cost Anomaly Detection. Billing에서 본인 계정이 **legacy 12개월/750h vs 2025-07-15 이후 신규 크레딧 모델($
  200/6개월)**인지 확인. (한국어 튜토리얼 대부분 구 모델 전제 → 틀릴 수 있음.) **이 한 가지가 비용폭탄 80% 차단.**
- **EC2**: free-tier 단일 인스턴스(Seoul t2/t3.micro) + **스왑 2GB 필수** + `-Xmx384m`. **인스턴스에서 빌드 금지**(Gradle OOM) → 로컬/GitHub
  Actions 빌드.
- **DB**: 메모리 안전·관리형 학습 → RDS db.t3.micro + "미사용 시 stop". 비용 극단 회피 → EC2 내 native MySQL(`innodb_buffer_pool_size=128M`).
  **Docker DB는 1GB에서 비권장**.
- **비용 트리거 주의**: Public IPv4 $3.6/월(EIP 방치/중지 인스턴스 부착), EBS는 중지 중에도 과금, terminate 후 고아 볼륨/EIP, **NAT Gateway 생성 금지**, 리전
  Seoul 확인.
- **배포**: jar + systemd(unit + `journalctl`). EB 쓰면 single-instance(ALB 회피).
- **HTTPS**: Caddy + nip.io(`{IP}.nip.io`)/DuckDNS 무료 FQDN(Caddyfile 2줄 자동 TLS). 보안그룹 80/443.
- **CI**: GitHub Actions(Temurin 17 → `bootJar` → scp → `systemctl restart`), 자격증명 GH Secrets. 처음엔 수동 scp+ssh 검증 후 자동화.
- **세션 종료 시** EC2/RDS stop(크레딧 모델이면 상시 가동 지양).

## 10. 테스트 (면접 "테스트 짜봤나" 방어, 과하지 않게)

- `@DataJpaTest`(H2)로 repository + **upsert 중복방지** 단위테스트.
- 핵심 쿼리/페이징/마이그레이션은 `@DataJpaTest(replace=NONE)` + Testcontainers + `@ServiceConnection`(실 MySQL) 이중화.
- `MockMvc`로 조회 API 1슬라이스.
- 코테 병행 맥락상 테스트 작성 = 학습 + 포트폴리오 동시 이득.

## 11. AI 사용 규칙 (step2 목적의 생명선)

- **코드는 직접 작성** (학습 목적 = 면접 방어). "대신 짜줘" 금지 — 이걸 어기면 step1의 "방어 못하는 코드" 함정이 사이드프로젝트로 재발.
- **AI 허용**: 개념 설명("JPA dirty checking이 뭐냐"), 에러 메시지 해석, AWS 네트워킹 디버깅. (M3 XML 파싱·M5 AWS에서 며칠 stall 방지.)
- 경계 기준: "내가 짠 코드를 면접에서 라인 설명 가능한가?" Yes면 AI를 어떻게 썼든 무관.

## 12. 면접 방어 포인트 (이게 완성 기준)

직접 짠 코드라 라인 설명 가능 + 다음을 말로 설명할 수 있을 것:

- MyBatis와 JPA 차이(영속성 컨텍스트/dirty checking)
- upsert를 비즈니스키로 구현한 이유(save()가 PK 기준이라)
- `@Scheduled` 증분 수집을 택한 이유(rate limit/totalCount 없음)
- open-in-view를 끈 이유, DTO로 반환한 이유
- AWS 프리티어 1GB에서 스왑/빌드 분리한 이유

---

## 변경 이력

- 2026-06-06: 초안. brainstorming 승인 design + 4-레인 research 보강(KOPIS live 검증 / SB·JPA 함정 / 마일스톤 8~10주·walking skeleton /
  AWS 비용 가드).
