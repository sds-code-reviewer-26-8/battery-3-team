# PLAN — TASK-007-add-factory-operating-cost

## 시도 1/3
- 리뷰 수준: Red (checklist.md 기준)
- 요청 이해: 공장별 하루 운영비(억 원 소수 입력, 기본 1억)를 기준 정보에 추가한다. 운영비 = 주문 있는 공장 수 x 공장별 하루 운영비 x 계획안 totalDays(주문 0 공장 0원). 총비용 = 생산비+운항비+운영비. COST 안은 "운항비+운영비" 최소로 고르고 이름을 "비용 최소"로 바꾼다. FAST/NEAR 선택 기준은 그대로. 확정 주문(Order, StoredPlan)과 화면에 운영비를 포함하고, 운영비 없는 과거 주문은 "-" 로 보이되 금액·날짜는 그대로. PRD 갱신.
- 반영한 입력: verify_feedback.md 없음, approval.md 없음

### 1. 변경할 파일과 작업 순서
| 순서 | 파일 | 변경 | 이유 |
|---|---|---|---|
| 1 | src/main/java/com/example/demo/simulation/Master.java | FactoryParam 에 operatingCostWon(long, 하루 운영비 원) 추가 | 계산 입력. StoredPlan 에 스냅샷으로 저장됨 |
| 2 | src/main/java/com/example/demo/simulation/Plan.java | operatingCost(long) 필드 추가 (totalCost 앞쪽 비용 필드들과 함께) | 계획안 비용 표시 |
| 3 | src/main/java/com/example/demo/simulation/Planner.java | Candidate 에 운영비 계산(주문 있는 공장별 운영비 합 x totalDays), COST 선택을 (운항비+운영비, 동률이면 totalDays) 최소로 변경, label "비용 최소", toPlan 에서 totalCost = 생산비+운항비+운영비. FAST/NEAR 선택·schedule·simulateFactory 는 건드리지 않음 | 핵심 계산 |
| 4 | src/main/java/com/example/demo/entity/Factory.java | dailyOperatingCostWon 컬럼 추가(Long, nullable: ddl-auto=update 로 기존 행은 null), 생성자·getter/setter | 기준 정보 저장 |
| 5 | src/main/java/com/example/demo/service/MasterService.java | initDefaults 기본 1억, currentMaster 에서 null 이면 기본 1억 적용, updateMaster 에 "operatingCost_{국가}"(억 원, BigDecimal, 0 이상·원 단위로 떨어짐) 검증 추가(오류는 기존 errors 에 모아 전부 저장 안 함), 저장 후 bumpMasterVersion 기존 경로 유지 | 원칙 3, 5 |
| 6 | src/main/java/com/example/demo/controller/MasterController.java, src/main/resources/static/master.html 또는 js/master.js | toForm 에 operatingCost_{국가} 억 원 문자열 추가, 공장 표에 입력칸 추가 | 기준 정보 화면 |
| 7 | src/main/java/com/example/demo/entity/Order.java | operatingCost(Long, nullable) 컬럼, 생성자 인자, getOperatingCostEok()(null 이면 null) | 확정 주문 저장, 과거 주문 null |
| 8 | src/main/java/com/example/demo/service/OrderService.java | confirm 에서 서버 재계산한 plan.operatingCost() 를 Order 에 저장 (클라이언트 값 미사용) | 원칙 1 |
| 9 | src/main/java/com/example/demo/controller/dto/OrderList.java, OrderDetail.java | operatingCostEok(null 허용) 추가 | 목록·상세 표시 |
| 10 | src/main/resources/static/js/planview.js, order-new.js, orders.js, anim.js(필요 시), order-detail.js/html | 운영비 표시, 값이 없으면(null/undefined, 저장 JSON 에 필드 없음) "-". 총비용 표기는 저장값 그대로. anim.js 비용 누적에 운영비 반영 여부는 구현 시 확인 | 화면 |
| 11 | docs/PRD.md | 계획안 이름 "비용 최소", FR-2·3·5·17, 기준 정보 정의, 수용 기준(운항비 최소 vs 가장 빠르게 항목을 새 기준에 맞게 교체) 갱신 | 요청 |
| 12 | src/test/java/com/example/demo/simulation/PlannerTest.java, OrderFlowTests.java, JsonApiTests.java | Master/FactoryParam 생성자 변경 반영, 라벨 단언 갱신, 새 테스트 추가 | CK 증명 |

### 2. 체크리스트 증명 방법
| 항목 | 증명할 것 | 테스트 또는 확인 방법 |
|---|---|---|
| CK-1 | 운영비 = 주문 공장 수 x 일 운영비 x totalDays, 주문 0 공장 0원, 총비용 = 합 | PlannerTest 신규 `운영비는_주문있는_공장수x일운영비xtotalDays` (일부 나라만 주문, 공장별 다른 운영비 사용) |
| CK-1 | COST 가 운항비+운영비 최소, label "비용 최소" | PlannerTest 신규 `비용최소는_운항비와_운영비_합이_최소` (운영비를 크게 한 Master 에서 COST 합이 FAST·NEAR 합 이하, 운영비 0 에 가까우면 기존 운항비 최소와 일치) |
| CK-1 | FAST/NEAR 기준 불변, 검산 유지 | 기존 PlannerTest FAST/NEAR 단언 통과 + 신규 `FAST는_totalDays_최소_운영비무관`; simulateFactory 미수정(diff 확인), 모든 계획안 생성 시 IllegalStateException 없음 |
| CK-2 | 운영비 저장·기본값 1억·masterVersion 증가 | JsonApiTests(또는 OrderFlowTests)에 신규 `기준정보_운영비_저장시_버전증가` (GET master 의 operatingCost_CHINA 기본 "1", 소수 "0.5" 저장 후 값·버전 확인) |
| CK-2 | 잘못된 값 하나면 전부 저장 안 함 | 신규 `기준정보_운영비_잘못된값이면_전부_저장안됨` (한 공장 "abc"/음수와 다른 공장 변경값 동시 전송 → 400, 값·버전 불변) |
| CK-3 | 확정 시 서버 재계산 운영비 저장·표시 | 신규 `확정시_운영비_저장` (주문 확정 후 상세/목록 API 의 operatingCostEok 가 simulate 결과와 일치, 총비용 = 생산+운항+운영). 화면 표시는 planview/order-new/orders JS 코드 위치 확인 |
| CK-3 | 과거 주문 "-" 및 금액·날짜 불변 | 신규 `운영비없는_과거주문은_빈값으로_열림` (Order 를 운영비 null + 운영비 필드 없는 planJson 으로 저장 → 상세·목록 API 200, operatingCostEok null, totalCostEok·confirmedAt 저장값 그대로). JS 의 "-" 분기는 코드 위치 확인 |
(전체 테스트 `gradlew.bat test` 는 AC-T, 6원칙 대조는 AC-P 로 별도 확인한다.)

### 3. 6원칙 "주의" 항목 대응
| 원칙 | 위험 | 대응 | 확인 방법 |
|---|---|---|---|
| 1 서버 재계산 | 운영비·총비용을 클라이언트 값으로 저장 | confirm() 의 simulate() 결과 Plan 에서만 읽어 저장 | 신규 확정 테스트, diff 에서 요청 DTO 에 운영비 입력 없음 확인 |
| 2 확정 주문 불변 | 과거 주문 재계산·변경, null 로 인한 오류 | 과거 주문은 Order.operatingCost null 유지, planJson 재작성 없음, 화면은 "-" | 과거 주문 테스트 |
| 3 masterVersion | 운영비 저장이 버전을 안 올림 | updateMaster 의 기존 bumpMasterVersion 경로 안에서 저장 | CK-2 버전 증가 단언 |
| 5 전부 맞아야 저장 | 운영비 검증이 개별 저장을 일으킴 | 검증을 기존 errors 수집 단계에 추가, 저장은 검증 뒤 일괄 | CK-2 잘못된 값 테스트 |
| 6 검산 | 선택 기준 변경이 일정·검산을 건드림 | Planner 에서 선택 비교식과 toPlan 비용 합산만 수정, schedule/simulateFactory 미수정 | git diff 확인, 전체 PlannerTest 통과 |

### 4. 이번 작업에서 하지 않을 것 (범위 밖)
- 운영비 세부 분류(인건비 등)와 공장별 이외의 운영비 항목
- FAST/NEAR 선택 기준 변경, 일정 계산(schedule)·simulateFactory 수정
- 과거 주문의 금액·planJson 재계산 또는 운영비 소급 채우기, DB 마이그레이션 스크립트
- 상태 변경(changeStatus)·로그인·항로 입력 등 인접 로직 리팩토링, 무관 화면 문구 정리
- docs/INTENT.md 수정

### 사람 확인 필요
- 기존 운영 DB 의 Factory 행(새 컬럼 null)을 기본 1억으로 간주하는 방침(currentMaster 에서 null → 1억). 이 값은 이후 주문 계산에 쓰이므로 확인 필요
- 운영비 입력 허용 범위(0 허용 여부). 계획은 0 이상 허용(요청에 명시 없음)
- anim.js 의 비용 애니메이션 누적에 운영비를 포함할지(요청은 "화면의 비용 표시"로 포함으로 해석, 구현 시 확인)

## 시도 1/3 — 수정 1 (범위 변경)
- 리뷰 수준: Red (checklist.md 기준, 변동 없음)
- 반영한 입력: approval.md 의 "수정 요청 2026-10-22 14:39" 전부. verify_feedback.md 없음
- 범위 변경: 있음 — E2E 6개 파일, docs/INTENT.md 가 변경 대상에 추가됨(이전 목록에 없던 파일), anim.js 가 "필요 시"에서 확정 대상으로 바뀜

### 반영한 의견과 이전 계획과 달라진 점
| 의견 | 반영 |
|---|---|
| (1) null 운영비는 기본 1억 간주, 기준 정보 화면에도 1 표시 | 사람 확인 사항 해소. 5·6번 작업 그대로 확정: currentMaster 와 toForm 모두 null → 1억(폼 "1") |
| (2) 운영비 0 허용 | 검증은 0 이상 허용으로 확정. CK-2 테스트에 "0" 저장 성공 케이스 추가 |
| (3) anim.js 비용 애니메이션에 운영비 포함, 끝 값 = 계획안 총비용 | 10번 작업의 anim.js 를 확정 대상으로 변경. 증명: E2E 또는 코드 확인으로 애니메이션 끝 값이 plan.totalCost 표시와 같음 단언 추가 |
| (a) E2E 갱신 | 아래 13번 작업 추가 |
| (b) JsonApiTests, OrderFlowTests 마스터 저장 호출에 새 키 | 12번 작업에 명시: PUT/POST 마스터 저장 폼에 operatingCost_{국가} 추가(필수 키이므로 없으면 400) |
| (c) INTENT.md 수정 | 14번 작업 추가. 범위 밖에서 INTENT.md 항목 삭제 |
| (d) Planner.java 16행 주석 | 3번 작업에 포함(주석과 76행 label 모두 "비용 최소") |

### 1'. 추가·변경되는 작업
| 순서 | 파일 | 변경 | 이유 |
|---|---|---|---|
| 3 (보완) | src/main/java/com/example/demo/simulation/Planner.java | 16행 설명 주석 "운항비 최소" → "비용 최소" | 의견 (d) |
| 10 (확정) | src/main/resources/static/js/anim.js | 비용 누적에 운영비 반영, 끝 값이 계획안 총비용과 일치 | 의견 (3) |
| 12 (보완) | src/test/java/com/example/demo/JsonApiTests.java, OrderFlowTests.java | 마스터 저장 호출에 operatingCost_{국가} 키 추가, 라벨 단언 갱신 | 의견 (b) |
| 13 | e2e/tests/order-detail.spec.ts, sim.spec.ts | "운항비 최소" 단언(27행, 18·215행)을 "비용 최소"로 변경. 단언 의미 약화 금지 | 의견 (a) |
| 13 | e2e/tests/hg.helpers.ts, api.spec.ts, mst.spec.ts, sim.spec.ts | 마스터 폼 키 구성(defaultMasterForm 등)에 operatingCost_* 추가, 기본값 "1" | 의견 (a). 필수 키라 없으면 저장 400 |
| 14 | docs/INTENT.md | 설계 의도 10번(35행) 계획안 이름 "비용 최소"로, 5장 "조정될 수 있는 값" 표에 공장 하루 운영비(0 이상, 원 단위, 억 원 입력, 기본 1억) 행 추가 | 의견 (c) |

### 2'. 체크리스트 증명 방법 보완
| 항목 | 증명할 것 | 테스트 또는 확인 방법 |
|---|---|---|
| CK-2 | 운영비 0 허용, null 은 폼에 "1" | `기준정보_운영비_저장시_버전증가` 에 "0" 저장 성공 단언, 신규 `운영비_null이면_기본_1억` (Factory 컬럼 null 로 저장 후 GET master 가 "1") |
| CK-2 | 기존 JsonApiTests/OrderFlowTests 가 새 키로 통과 | `gradlew.bat test` |
| CK-1/3 | 라벨·애니메이션 | E2E: `npx playwright test` 중 order-detail.spec.ts, sim.spec.ts(라벨 "비용 최소"), mst.spec.ts, api.spec.ts. anim.js 끝 값 = 총비용 표시를 sim.spec.ts 에 단언 추가(또는 코드 위치 확인). E2E 실행 환경(앱 기동)이 없으면 그 사실을 보고 |

### 3'. 6원칙 "주의" 항목 대응
변동 없음(시도 1 의 표 그대로, 주의 5개 유지).

### 4'. 이번 작업에서 하지 않을 것 (보완)
- 시도 1 목록 중 "docs/INTENT.md 수정" 은 삭제(이번에 대상 포함). INTENT.md 는 위 두 곳만 수정하고 열린 질문·6원칙은 건드리지 않음
- 위에 적은 E2E 파일 외 다른 E2E(lst, auth, flow, pages, helpers.ts)는 새 키로 깨지지 않는 한 수정하지 않음. 깨지면 같은 방식으로 최소 수정
- E2E 단언 약화·삭제, 운영비 외 E2E 시나리오 추가

### 사람 확인 필요
- 없음(시도 1 의 세 질문은 모두 답변 반영됨). 다만 범위가 늘었으므로 Red 수준 그대로 사람이 변경 파일 목록을 다시 확인
