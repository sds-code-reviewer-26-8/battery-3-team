# self-check — TASK-007-add-factory-operating-cost

## 시도 1/3
### 테스트 실행 (직접 실행한 결과)
- `./gradlew test` (전체): 49개 중 49개 통과, 실패 0 (PlannerTest, OrderFlowTests, JsonApiTests, DemoApplicationTests).
  - 신규: PlannerTest `운영비는_주문있는_공장수x일운영비xtotalDays`, `비용최소는_운항비와_운영비_합이_최소`, `FAST는_totalDays_최소_운영비무관`
  - 신규: OrderFlowTests `기준정보_운영비_저장시_버전증가`, `기준정보_운영비_잘못된값이면_전부_저장안됨`, `운영비_null이면_기본_1억`, `확정시_운영비_저장`, `운영비없는_과거주문은_빈값으로_열림`
- E2E `cd e2e && npx playwright test` (인메모리 서버 자동 기동): 83개 중 82 통과, 1 skipped(LST-04, 원래 조건부 건너뜀), 실패 0. SIM-16 에 애니메이션 끝 값 = 총 비용 단언 추가.

### 6원칙 점검
1. 서버 재계산: 지킴 — OrderService.confirm 이 simulate() 결과 plan.operatingCost() 만 Order 에 저장, 요청 DTO 에 운영비 입력 없음. (`확정시_운영비_저장`)
2. 확정 주문 불변: 지킴 — 기존 주문 재계산·planJson 재작성 없음. Order.operatingCost null 허용, 화면 "-". (`운영비없는_과거주문은_빈값으로_열림`)
3. masterVersion: 지킴 — 운영비 저장은 MasterService.updateMaster 의 기존 bumpMasterVersion 경로 안. (`기준정보_운영비_저장시_버전증가`)
4. Order.version: 해당 없음 — changeStatus 미수정.
5. 전부 맞아야 저장: 지킴 — 운영비 검증을 기존 errors 수집 단계에 추가. (`기준정보_운영비_잘못된값이면_전부_저장안됨`)
6. 검산: 지킴 — simulateFactory·schedule 미수정(Planner diff 는 선택 비교식, 운영비 합산, 라벨뿐).

### PRD/INTENT 점검
- 건드린 FR: FR-2, FR-3, FR-5, FR-17 (+ 수용 기준 1개 교체, 1개 추가, 테스트 현황 문구의 이름 변경). INTENT: 설계 의도 10번 이름, 용어표, 5장 표·기본값 문장. 열린 질문(Q1~Q4)은 건드리지 않음.

### 통과 기준 행별 자가 점검 (참고용)
- CK-1: 충족 — PlannerTest 신규 3개 + 기존 단언(totalCost 식 갱신) 통과.
- CK-2: 충족 — OrderFlowTests 신규 3개 통과.
- CK-3: 충족 — OrderFlowTests 신규 2개 통과, JS 표시는 E2E(상세·목록·SIM-16) 통과.
- AC-T: 충족 — 전체 49/49.
- AC-P: 충족 — 위 6원칙 점검 참조.
