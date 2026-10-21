# CLAUDE.md

## 프로젝트 개요
배터리 해상 운송 주문/경로 시뮬레이션 웹 앱 (Spring Boot 4.1.1, Java 17, Gradle Kotlin DSL).
한국(KOREA)이 목적지, 4개국 공장(중국·일본·베트남·인도네시아)에서 배터리를 생산해 배로 수거한다.
주문 수량을 입력하면 경로 3가지(COST 운항비 최소 / FAST 가장 빠르게 / NEAR 가까운 곳부터)를 계산하고, 선택한 경로로 주문을 확정한다.

## 기술 스택
- Spring MVC (`webmvc`) + Thymeleaf (서버 렌더링) + 바닐라 JS
- Spring Data JPA + H2 (파일 DB `./data/battery`, `AUTO_SERVER=TRUE`, `ddl-auto=update`)
- 테스트: JUnit 5 (`spring-boot-starter-*-test`), 테스트 DB는 H2 인메모리(`create-drop`)
- 루트 프로젝트명 `demo`, 패키지 `com.example.demo`

## 명령어
```
./gradlew bootRun     # 실행 (http://localhost:8080)
./gradlew test        # 전체 테스트
./gradlew build
```
로그인 계정: `admin` / `admin` (`AppController.ACCOUNT`, 단일 하드코딩 계정, 세션 기반).

## 구조 (`src/main/java/com/example/demo`)
| 패키지 | 내용 |
|---|---|
| `controller/AppController` | 모든 화면·API 엔드포인트 (단일 컨트롤러) |
| `service/BatteryService` | 비즈니스 로직: 마스터 데이터, 시뮬레이션, 주문 확정/상태 변경. `BusinessException` 사용 |
| `simulation/` | 순수 계산 로직(JPA 비의존). `Planner`(경로 계산), `Master`(마스터 스냅샷 record), `Plan`, `StoredPlan` |
| `entity/` | `Country`, `Factory`, `Route`, `Setting`(싱글톤 행), `Order`, `OrderStatus`, `OrderStatusHistory` |
| `repository/` | `MasterRepositories`, `OrderRepositories` (여러 리포지토리 인터페이스를 한 파일에 모음) |
| `config/` | `LoginInterceptor`(+`WebConfig`), `DataInitializer`(기동 시 `initDefaults`), `JpaConfig` |

리소스: `templates/` (login, orders, order-new, order-detail, master, fragments), `static/js/` (common, order-new, planview, anim), `static/css/app.css`, `static/images/`.

### 엔드포인트
- 화면: `GET /` → `/orders`, `/login`(GET/POST), `/logout`, `/orders`, `/orders/new`, `/orders/{id}`, `POST /orders/{id}/status`, `/master`(GET/POST)
- API(JSON, 미로그인 시 401): `GET /api/master`, `POST /api/simulate`, `POST /api/orders`
- `LoginInterceptor`가 `/login`, 정적 리소스, `/error` 외 전부 보호

## 도메인 규칙
- 주문 상태 전이(`OrderStatus.next`): 확정 → 진행/취소, 진행 → 완료/취소, 완료·취소는 종료.
- 마스터 데이터(공장 스펙, 국가 간 항해일수, 선박 수 등)는 DB에 있고 `initDefaults()`가 비어 있을 때만 기본값을 채운다.
- 주문 확정·상태 변경은 낙관적 버전 검사(`masterVersion`, `expectedVersion`)를 한다 — 마스터가 바뀐 뒤의 오래된 시뮬레이션으로 확정되는 것을 막기 위함.
- `Planner` 규칙 (클래스 Javadoc 참고):
  - 0일차에 모든 공장 동시 생산, 하루 생산 = min(하루 최대, 재고 상한 − 재고, 남은 주문)
  - 배는 도착 시점 재고를 전부 수거, 한 나라는 한 척만 방문
  - 방문 횟수 = 올림(주문 / 재고 상한), 여러 번이면 한국에 들러 내려놓고 재출항
  - 배는 재고가 찰 때까지 늦게 출항(한국 대기는 무료)
  - 총 수량 상한 `MAX_TOTAL_K` = 100,000
- 금액은 원 단위 저장, 억 단위 변환 상수 `WON_PER_EOK`.

## 컨벤션
- 탭 들여쓰기, 한국어 주석/UI 문구/에러 메시지.
- 계산 로직은 `simulation` 패키지에 순수 Java로 두고 `PlannerTest`로 단위 테스트; 흐름 검증은 `OrderFlowTests`.
- `open-in-view=false` — 컨트롤러/템플릿에서 지연 로딩에 기대지 말고 서비스 트랜잭션 안에서 필요한 것을 로딩할 것.
- Thymeleaf 캐시는 꺼져 있음(`spring.thymeleaf.cache=false`).
