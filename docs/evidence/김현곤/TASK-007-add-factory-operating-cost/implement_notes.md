# implement_notes — TASK-007-add-factory-operating-cost

## 시도 1/3
### 변경 파일
- `simulation/Master.java` — FactoryParam 에 operatingCostWon(하루 운영비, 원) 추가
- `simulation/Plan.java` — operatingCost 필드 추가
- `simulation/Planner.java` — 비용 최소 선택을 (운항비+운영비, 동률이면 소요 일수)로 변경, 라벨·주석 "비용 최소", toPlan 에서 운영비·총비용 계산
- `entity/Factory.java` — dailyOperatingCostWon(nullable Long) 컬럼·접근자
- `service/MasterService.java` — 기본값 1억, currentMaster 에서 null→1억, updateMaster 에 operatingCost_{국가} 검증·저장
- `controller/MasterController.java` — 폼에 operatingCost_{국가}(억 원 문자열) 추가
- `entity/Order.java` — operatingCost(nullable) 컬럼, 생성자 인자, getOperatingCostEok
- `service/OrderService.java` — 확정 시 서버 재계산 plan.operatingCost() 저장
- `controller/dto/OrderList.java`, `OrderDetail.java` — operatingCostEok(null 허용)
- `static/js/planview.js`(요약 운영비, 마스터 표 운영비), `order-new.js`(카드), `orders.js`+`orders.html`(목록 컬럼, 없으면 "-"), `anim.js`(비용 누적에 운영비), `master.html`+`master.js`(입력칸)
- `docs/PRD.md`, `docs/INTENT.md` — 이름·FR·수용 기준·값 범위 갱신
- 테스트: `PlannerTest.java`, `OrderFlowTests.java` (신규 테스트 + 마스터 저장 폼에 새 키)
- E2E: `hg.helpers.ts`, `mst.spec.ts`, `lst.spec.ts`, `order-detail.spec.ts`, `sim.spec.ts`

### 선택한 방식과 이유
- (선택) 비용 최소 비교를 후보 탐색 루프 안에서 `shipping + (주문 공장 하루 운영비 합) x totalDays` 로 계산. (대안) 후보를 Plan 으로 만든 뒤 비교 — 모든 후보에 simulateFactory 를 돌려야 해 느려지고 검산부를 건드림. 운영비는 totalDays 와 주문 공장 집합만으로 결정되므로 탐색 안에서 계산해도 같은 값이다.
- (선택) 기존 DB 의 null 운영비는 Factory 컬럼 nullable + currentMaster 에서 1억 간주(승인 의견 1). (대안) 시작 시 마이그레이션 — 범위 밖.
- (선택) 과거 주문은 Order.operatingCost null + 저장 JSON 에 필드 없음으로 두고 화면에서 "-". 저장 JSON 은 수정하지 않음.
- anim.js: 운영비 = 경과 일수(총 소요 일수까지) x 주문 있는 공장 하루 운영비 합. 과거 계획은 값이 없어 0 으로 처리해 끝 값이 저장된 총비용과 같다.

### checklist.md 만족 방식
- CK-1: 만족한다고 생각한다 — 근거: PlannerTest `운영비는_주문있는_공장수x일운영비xtotalDays`, `비용최소는_운항비와_운영비_합이_최소`, `FAST는_totalDays_최소_운영비무관`, 기존 totalCost 단언 갱신. simulateFactory/schedule 미수정.
- CK-2: 만족한다고 생각한다 — 근거: OrderFlowTests `기준정보_운영비_저장시_버전증가`(기본 "1", "0.5"·"0" 저장, 버전 +1), `기준정보_운영비_잘못된값이면_전부_저장안됨`, `운영비_null이면_기본_1억`.
- CK-3: 만족한다고 생각한다 — 근거: `확정시_운영비_저장`(Order·planJson·상세·목록), `운영비없는_과거주문은_빈값으로_열림`(null 운영비, 금액·확정일시 그대로), E2E 상세·목록·SIM-16.
- 6원칙 주의 항목: 1(simulate 결과만 저장), 2(과거 주문 재작성 없음), 3(updateMaster 기존 버전 증가 경로), 5(errors 수집 단계에 검증 추가), 6(선택식·합산만 수정) — self-check.md 참조.

### 계획과 다르게 구현한 부분
- (계획) 파일명 MasterService/OrderService/MasterController 등 → (실제) 계획과 같은 파일 사용. 변경 파일은 계획 목록과 같고 추가 파일 없음. orders.html(목록 컬럼 헤더)은 계획 10번의 "orders.js" 와 한 묶음으로 수정.
- (계획) JsonApiTests 수정/추가 → (실제) JsonApiTests 는 수정하지 않음. 새 통합 테스트는 모두 OrderFlowTests 에 추가(계획에 "JsonApiTests 또는 OrderFlowTests" 로 허용), JsonApiTests 는 폼을 읽어 그대로 저장하는 방식이라 새 키 추가 없이 통과. 
- (계획) lst.spec.ts 는 "깨지면 최소 수정" → (실제) 목록 컬럼 헤더 단언에 '운영비(억원)' 추가(목록 컬럼을 추가했으므로).
- (계획) sim.spec.ts 에 anim 끝 값 단언 → (실제) SIM-16 에 추가, SIM-21 의 총 비용 21 → 36 과 운영비 15.00 단언(운영비 포함으로 기대값이 바뀜), expectDefaultMaster 에 운영비 원 값 포함.
- 그 외 계획과 같음.

### 사람 확인 필요
- E2E 를 실행해 인메모리 서버로 82 통과 확인함. PRD.md 의 CRLF 줄바꿈은 기존 방식 유지.
