import { expect, test } from '@playwright/test';
import {
  DEFAULT_ORDERS,
  REPEAT,
  SMALL_ORDER,
  cancelViaApi,
  confirmSmallOrder,
  login,
  orderSummary,
} from './support';

// 반복 실행 안정성. 같은 동작을 REPEAT 번(기본 20, ROBUST_N) 되풀이해도 결과가 변하거나 쌓이지 않는지 본다.
// 서버를 다른 테스트와 같이 쓸 수 있으므로 "비어 있다"고 가정하지 않고 시작 시점과 비교한다.
test.describe('반복 실행 안정성', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('RPT-01 확정→목록→상세→취소를 반복해도 목록 건수가 원래대로 돌아온다', async ({ page }) => {
    const before = await orderSummary(page);

    for (let i = 1; i <= REPEAT; i++) {
      const id = await confirmSmallOrder(page);

      const listed = await orderSummary(page);
      expect(listed.ids, `${i}회차 확정 후 목록에 #${id} 가 있어야 한다`).toContain(id);
      expect(listed.total, `${i}회차 확정 후 전체 건수`).toBe(before.total + 1);

      const detail = await page.request.get(`/api/orders/${id}`);
      expect(detail.status(), `${i}회차 상세 조회`).toBe(200);
      expect((await detail.json()).status.name, `${i}회차 상태`).toBe('CONFIRMED');

      expect(await cancelViaApi(page, id), `${i}회차 취소는 삭제 응답`).toBe(true);

      const gone = await page.request.get(`/api/orders/${id}`);
      expect(gone.status(), `${i}회차 취소 후 상세는 404`).toBe(404);
      const after = await orderSummary(page);
      expect(after.ids, `${i}회차 취소 후 목록에 #${id} 가 없어야 한다`).not.toContain(id);
      expect(after.total, `${i}회차 취소 후 전체 건수`).toBe(before.total);
    }

    expect((await orderSummary(page)).counts, '반복 후 상태별 건수').toEqual(before.counts);
  });

  test('RPT-02 확정을 반복하면 주문 번호가 겹치지 않고 계속 커진다', async ({ page }) => {
    const ids: number[] = [];
    try {
      for (let i = 0; i < REPEAT; i++) {
        ids.push(await confirmSmallOrder(page));
      }
      expect(new Set(ids).size, '주문 번호 중복 없음').toBe(ids.length);
      for (let i = 1; i < ids.length; i++) {
        expect(ids[i], `${i + 1}번째 번호는 앞보다 커야 한다`).toBeGreaterThan(ids[i - 1]);
      }
    } finally {
      for (const id of ids) {
        await cancelViaApi(page, id); // 만든 주문은 모두 취소(삭제)해 정리한다
      }
    }
  });

  test('RPT-03 같은 입력으로 시뮬레이션을 반복하면 결과가 매번 같다', async ({ page }) => {
    for (const [name, orders] of [
      ['작은 주문(중국 1K)', SMALL_ORDER],
      ['기본 주문(12,000K)', DEFAULT_ORDERS],
    ] as const) {
      let first: string | undefined;
      for (let i = 1; i <= REPEAT; i++) {
        const res = await page.request.post('/api/simulate', { data: { orders } });
        expect(res.status(), `${name} ${i}회차 응답`).toBe(200);
        const body = JSON.stringify((await res.json()).options);
        if (first === undefined) first = body;
        expect(body, `${name} ${i}회차 결과가 1회차와 같아야 한다`).toBe(first);
      }
    }
  });
});
