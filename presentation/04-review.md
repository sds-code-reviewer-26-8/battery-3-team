# 4. PR & 코드리뷰

Action과 Gate 규칙을 적용하여 PR & 코드 리뷰를 진행했습니다.

기능 추가를 담당한 팀원은 Action의 역할로서 기능을 개발하는 동시에 Code Smell을 추가했습니다.

다른 팀원은 Gate 역할로서 코드 리뷰 활동을 하며 Agent와 함께 개선안을 도출하는 프로세스로 진행했습니다.

미션별 PR 현황을 정리하고, Best Practice를 선정하여 좋았던 점을 적어 봅니다.

<br>

## 미션별 PR 현황

<!-- 각 미션은 Action과 Gate를 1명씩 담당합니다. 누가 무엇을 담당했는지 아래 표에 적으세요. -->

| 미션 | Action 담당자 | Gate 담당자 | PR 링크 | 리뷰 코멘트 수 | Commit 수 |
|---|---|---|---|:-:|:-:|
| TASK-004 회원 가입 | joosang425 | GlennHKim | [#22](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/22) | 2개 | 9개 |
| TASK-005 필요 납기일 | anglermoon | Jaesuk-0307 | [#23](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/23) | 7개 | 4개 |
| TASK-006 고객사 이름 입력 | Jaesuk-0307 | joosang425 | [#24](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/24) | 9개 | 8개 |
| TASK-007 공장 운영비 | GlennHKim | anglermoon | [#25](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/25) | 8개 | 9개 |

<br>

## Best Practice: 코드 리뷰

팀원이 각자 효과를 본 리뷰를 한 건씩 골라 작성했습니다. 각 블록의 **작성자**를 제목에 표시했고, 팀원 글은 원문 그대로 모았습니다. (PR 번호 순)

#### Best Practice 1 — 작성: 김현곤

| 항목 | 내용 |
|---|---|
| 구현 내용 | Thymeleaf를 제거하고 정적 HTML + JSON API로 프런트엔드 전환 (리팩토링 2) |
| PR 링크 | [#19](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/19) (리뷰: anglermoon) |
| 문제 상황 | Evidence가 "flaky 0"이라고 적었지만 한 번 실행한 결과였습니다. 화면이 JS로 그려지도록 바뀌면서, 주문 목록의 칩 건수를 읽는 `chipCounts`가 `goto` 직후 칩이 그려지기 전에 읽는 경우가 생겼습니다(`Expected: NaN`). 서버 렌더링이던 `main`에서는 결정적으로 통과하던 테스트입니다. 작성자는 "최상위 `await`로 로딩 시점을 맞췄다"고 설명했지만 이 방식은 안정적으로 보장되지 않았습니다. |
| 적용한 방법 | CNF-07·CNF-08·DTL-06을 12회씩 반복 실행해 36회 중 2회 실패를 재현했고, 전체 실행에서도 CNF-08이 한 번 실패했습니다. 수정 방향은 앱 코드를 테스트 타이밍에 맞추지 말고 테스트 쪽을 고치자는 것이었습니다(첫 칩이 보일 때까지 기다림). 해당 도우미는 리뷰어 본인이 만든 것이라 책임을 지고 후속 수정하겠다고 밝혔습니다. 함께 짚은 것은 결과 파일에 남은 Windows 사용자 경로, DTL-05·DTL-08이 약해졌는데 표에 "아니오"로 적힌 점, 취소 후 안내 문구가 정책(Q2)에 묶여 있다는 점입니다. |
| 좋았던 점 | 작성자의 "flaky 0" 주장을 그대로 믿지 않고 반복 실행이라는 방법으로 반증했습니다. 원인과 해결 위치(테스트 쪽)까지 구체적으로 제시했습니다. 작성자가 본문의 잘못된 설명을 정정하고 직접 재현(30회 중 1회 실패)한 뒤, `chipCounts`와 같은 종류인 LST-04·LST-06까지 넓혀 고쳤습니다(커밋 `7d7044f`, `1c8cea6`, `fd8060b`, `0543a9c`). 확인하지 못한 것(Firefox·Safari, 린트, 부하)도 리뷰에 명시했습니다. |
| Evidence | [리뷰](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/19#pullrequestreview-5608345846), [작성자 답변 코멘트](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/19#issuecomment-6331040743) |

#### Best Practice 2 — 작성: 우주상

| 항목 | 내용 |
|---|---|
| 구현 내용 | 회원 가입 시 비밀번호가 UTF-8 72바이트를 넘으면 가입시키지 않고 이유를 안내하도록 검증을 추가했습니다. 이 한도(영문·숫자 64자, 한글 24자)를 가입 화면 안내 문구와 PRD FR-22에 적었고, PRD FR-20·INTENT 원칙 8·`LoginInterceptor` 주석은 "로그인·회원가입 화면"으로 고쳤습니다. (TASK-004) |
| PR 링크 | [#22](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/22) (리뷰: GlennHKim) |
| 문제 상황 | 한글 비밀번호가 길면 가입 중 서버 오류(500)가 났습니다. 검증은 글자 수(8~64자)만 보는데 BCrypt는 72바이트까지만 받기 때문입니다. 직접 재현해 보니 한글 24자는 가입되고 25자와 30자는 `password cannot be more than 72 bytes`로 500이 났습니다. 또 가입 화면은 로그인 없이 열리는데 PRD와 INTENT는 "로그인 화면만 볼 수 있다"고 적혀 있어 FR-22와 어긋났습니다. |
| 적용한 방법 | 글자 수 규칙을 통과한 비밀번호가 72바이트를 넘으면 `BusinessException`으로 "비밀번호가 너무 깁니다. UTF-8 기준 72바이트까지 가능합니다(영문·숫자 64자, 한글 24자)"라고 안내했습니다. 글자 수 오류가 이미 있으면 같은 이유를 두 번 알리지 않습니다. JUnit `passwordLongerThanBcryptLimitIsRejectedWithReasonNotServerError`와 E2E `SGN-09`를 추가했고, 수정 전 코드(`aa440d4`)에서 새 테스트가 실제로 실패하는 것도 확인했습니다. |
| 좋았던 점 | 리뷰가 추상적인 의견이 아니라 재현 방법, 예외 위치, 원인, 수정 방향, 우선순위(`[필수]`, `[권장]`)를 갖춰서 바로 고칠 수 있었습니다. 이 결함은 기존 테스트로는 잡히지 않았고 사전 검토도 글자 수와 바이트 수를 구분하지 못했는데, 리뷰가 그 빈틈을 정확히 짚었습니다. 수정 후에도 리뷰어가 경계값(24자, 25자, 이모지, 혼합 문자열)과 실제 로그인까지 다시 확인하고 승인했습니다. |
| Evidence | PR #22 리뷰와 반영 답변 코멘트. 수정 커밋 `c4e9dfa`, `eaed74c`, `b136356`, `a4c9a8b`. 재검증은 JUnit 54/54, E2E 91 통과, 전체 3회 반복 273 통과(실패 0, flaky 0). 수정 전 실패 기록은 `docs/evidence/우주상/TASK-004-signup/results/before-review-*`. |

#### Best Practice 3 — 작성: 문형석

| 항목 | 내용 |
|---|---|
| 구현 내용 | 필요 납기일 기입 기능: 주문 시 '가능한 빨리' 또는 5~50일을 고르고, 납기를 넘는 경로는 빨간색으로 표시 (TASK-005, Yellow 영역) |
| PR 링크 | [#23](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/23) (리뷰: Jaesuk-0307) |
| 문제 상황 | 화면은 소수 입력을 막지만 서버는 막지 않았습니다. `POST /api/orders`에 `requiredDays: 5.5`를 보내면 200으로 확정되고 5로 저장됐고 `"10"`은 10으로 저장됐습니다. precheck의 위험표는 "소수는 서버가 검증"이라고 적혀 있어 실제와 달랐고, 기존 JUnit은 구현식(`late == (totalDays > 5)`)을 그대로 복제해 비교하고 있었습니다. |
| 적용한 방법 | 리뷰어가 로컬에서 직접 재현해 입력값과 결과(5.5 → 5, "10" → 10)를 제시했고, 요청을 **요청 사항(권장) 2건**과 **참고(선택) 5건**으로 나눠 적었습니다. 작성자는 납기일을 `JsonNode`로 받아 정수만 허용(소수·문자열은 400)하고, 납기일 = 소요 일수일 때와 하루 모자랄 때의 경계 테스트 등 JUnit 3개와 E2E 케이스를 추가했습니다(`8c81545`). 수정 전 컨트롤러로 되돌려 실행하면 새 정수 검증 테스트가 실패하는 것을 확인했고, 리뷰어가 못 했다고 밝힌 "기존 DB 파일 기동"을 대신 확인해 report에 추가했습니다. 근거를 대고 보류한 항목(5와 50의 중복)은 이유를 답글로 설명했습니다. |
| 좋았던 점 | 막연한 "검증 보강"이 아니라 **재현 입력과 기대 결과**를 줬습니다. 필수·참고를 나눠서 무엇부터 해야 하는지 분명했고, 리뷰어가 화면을 직접 띄워 확인한 것과 못 한 것(기존 DB 시나리오)을 구분해 적어서 작성자가 빈 곳을 채울 수 있었습니다. 반영 후 "1·2번이 반영되면 승인하겠다"는 약속대로 승인이 이어졌고, 리뷰에서 지적된 precheck 문구의 오류까지 문서로 정정됐습니다. |
| Evidence | 리뷰 2건(Comment [2026-10-22 06:21Z](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/23#pullrequestreview-5610038296), Approve [06:50Z](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/23#pullrequestreview-5610274964)). 반영 커밋 `8c81545`(코드·테스트)와 `cf30d57`(증거 갱신), 증거 폴더 [`docs/evidence/문형석/TASK-005-required-delivery-date/`](../docs/evidence/문형석/TASK-005-required-delivery-date/)의 report AC-5·AC-9와 스크린샷. |

#### Best Practice 4 — 작성: 오재석

| 항목 | 내용 |
|---|---|
| 구현 내용 | 공장별 하루 운영비를 추가하고, 비용 최소 안을 "운항비 + 운영비" 합이 가장 작은 안으로 고르도록 변경 (TASK-007, Red 영역) |
| PR 링크 | [#25](https://github.com/sds-code-reviewer-26-8/battery-3-team/pull/25) (리뷰: anglermoon) |
| 문제 상황 | 기본값(운영비 1억)에서는 "비용 최소"와 "가장 빠르게"가 같은 안이 됩니다. 그래서 기존 테스트는 운영비 30억일 때만 비교해서, 선택식이 항상 FAST를 고르게 잘못 바뀌어도 통과할 수 있었습니다. |
| 적용한 방법 | 리뷰어가 운영비를 바꿔 가며 직접 계산했습니다. 하루 0.5억일 때 세 안이 갈라지는 값을 표로 제시했습니다(비용 최소 24일·70억, 가장 빠르게 20일·72억, 운항비만 최소 26일·72억). 이 값을 고정하는 `PlannerTest` 코드를 그대로 가져갈 수 있게 제안했고, 로컬에서 50/50 통과를 확인했습니다. 작성자는 선택식을 일부러 두 가지로 망가뜨려(운영비 무시, 소요 일수만 비교) 새 테스트가 둘 다 실패하는 것까지 확인했습니다. 같은 리뷰에서 `Planner`의 `bestCost` 재계산과 운영비 합산 중복을 리팩토링으로 제안했고 `costBest`와 `dailyOperatingCost` 한 곳으로 정리됐습니다. |
| 좋았던 점 | 막연히 "테스트 보강"이 아니라 정확한 입력과 기대값을 줬고, 작성자가 변이 테스트로 그 테스트가 실제로 잡는지 증명했습니다. 승인 전에 #22~#24가 병합된 최신 커밋에서 세 기능(고객사·필요 납기일·운영비)을 한 주문으로 함께 확인해서, 병합 후 충돌도 같이 검증했습니다. "확인하지 못한 것"과 제품 판단 질문 3가지를 따로 남겨 머지를 막지 않는 의견을 분리했습니다. |
| Evidence | 리뷰 2건(Comment 2026-10-22 06:41Z, Approve 07:22Z)은 PR #25 페이지에서 볼 수 있습니다. 작성자 답변 코멘트와 반영 커밋 `5e90309`(TC), `e45d127`(리팩토링)도 같은 페이지에 있습니다. |

---

[← 이전: 기능 추가 미션 동작 영상](03-missions.md) · [목차](README.md) · [다음: 회고 →](05-retrospective.md)
