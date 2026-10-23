import { expect, type Page } from '@playwright/test';

// 견고성 테스트가 쓰는 공통 함수 모음. tests/helpers.ts 를 직접 import 하지 않고 여기서 필요한 것만 다시 내보낸다.
// tests/helpers.ts 가 바뀌어도 이 파일만 고치면 되도록 하기 위함이다.
export {
  DEFAULT_ORDERS,
  login,
  newLoggedInPage,
  runSimulation,
  createOrderViaApi,
  advanceViaApi,
  chipCounts,
} from '../tests/helpers';

/** 반복 횟수. ROBUST_N 으로 바꿀 수 있다(기본 20). */
export const REPEAT = Number(process.env.ROBUST_N ?? 20);

/** 전수 탐색이 가장 가벼운 주문: 중국 1K 한 나라만(배 1척, 방문 1번). */
export const SMALL_ORDER = { CHINA: 1, JAPAN: 0, VIETNAM: 0, INDONESIA: 0 };

type RouteType = 'COST' | 'FAST' | 'NEAR';

/** 목록 API 의 건수 요약: 상태별 건수와 전체 건수. */
export async function orderSummary(page: Page): Promise<{ counts: Record<string, number>; total: number; ids: number[] }> {
  const res = await page.request.get('/api/orders');
  expect(res.status(), '주문 목록 API').toBe(200);
  const body = await res.json();
  return { counts: body.counts, total: body.total, ids: body.orders.map((o: { id: number }) => o.id) };
}

/** 작은 주문(중국 1K)을 API 로 확정하고 주문 번호를 돌려준다. 로그인된 page 가 필요하다. */
export async function confirmSmallOrder(page: Page, routeType: RouteType = 'COST'): Promise<number> {
  const master = await (await page.request.get('/api/master')).json();
  const res = await page.request.post('/api/orders', {
    data: { orders: SMALL_ORDER, routeType, masterVersion: master.version, memo: '' },
  });
  expect(res.status(), '주문 확정 API').toBe(200);
  return (await res.json()).id;
}

/** API 로 주문을 취소한다(주문은 삭제된다). 응답의 deleted 값을 돌려준다. */
export async function cancelViaApi(page: Page, id: number): Promise<boolean> {
  const detail = await page.request.get(`/api/orders/${id}`);
  expect(detail.status(), `취소 전 주문 #${id} 조회`).toBe(200);
  const res = await page.request.post(`/api/orders/${id}/status`, {
    data: { status: 'CANCELED', version: (await detail.json()).version },
  });
  expect(res.status(), `주문 #${id} 취소 API`).toBe(200);
  return (await res.json()).deleted;
}
