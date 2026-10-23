import { expect, test, type APIRequestContext } from '@playwright/test';
import { SMALL_ORDER, cancelViaApi, confirmSmallOrder, login, newLoggedInPage, orderSummary } from './support';

// 동시 요청. 로그인한 두 세션(서로 다른 브라우저 컨텍스트)이 같은 순간에 요청을 보내도 한 건만 반영되고 나머지는 안내를 받는지 본다.
// 한 번의 경합은 우연히 순서대로 처리될 수 있으므로 새 주문으로 ROUNDS 번 되풀이한다.
const ROUNDS = Number(process.env.ROBUST_ROUNDS ?? 10);
const CONCURRENT_CONFIRMS = 10;

type Reply = { status: number; body: any };

async function post(request: APIRequestContext, url: string, data: unknown): Promise<Reply> {
  const res = await request.post(url, { data });
  return { status: res.status(), body: await res.json().catch(() => null) };
}

async function detail(request: APIRequestContext, id: number): Promise<Reply> {
  const res = await request.get(`/api/orders/${id}`);
  return { status: res.status(), body: await res.json().catch(() => null) };
}

test.describe('동시 요청', () => {
  test('CON-01 같은 주문을 두 세션이 동시에 진행으로 바꾸면 한 건만 반영된다', async ({ browser, baseURL }) => {
    const a = await newLoggedInPage(browser, baseURL);
    const b = await newLoggedInPage(browser, baseURL);
    try {
      for (let round = 1; round <= ROUNDS; round++) {
        const id = await confirmSmallOrder(a.page);
        const { version } = (await detail(a.page.request, id)).body;
        const body = { status: 'IN_PROGRESS', version };

        const replies = await Promise.all([
          post(a.page.request, `/api/orders/${id}/status`, body),
          post(b.page.request, `/api/orders/${id}/status`, body),
        ]);

        const statuses = replies.map((r) => r.status).sort();
        expect(statuses, `${round}회차 응답 코드(한 건 성공, 한 건 충돌)`).toEqual([200, 409]);
        const loser = replies.find((r) => r.status === 409)!;
        expect(loser.body.conflict, `${round}회차 충돌 표시`).toBe(true);
        expect(loser.body.error, `${round}회차 충돌 안내`).toContain('다른 사용자가 먼저');

        const after = (await detail(a.page.request, id)).body;
        expect(after.status.name, `${round}회차 최종 상태`).toBe('IN_PROGRESS');
        const toInProgress = after.history.filter((h: any) => h.toStatus.name === 'IN_PROGRESS');
        expect(toInProgress, `${round}회차 '확정→진행' 이력은 정확히 1줄`).toHaveLength(1);

        await cancelViaApi(a.page, id);
      }
    } finally {
      await a.context.close();
      await b.context.close();
    }
  });

  test('CON-02 진행과 취소를 동시에 보내면 한쪽 결과만 반영되고 서버 오류가 없다', async ({ browser, baseURL }) => {
    const a = await newLoggedInPage(browser, baseURL);
    const b = await newLoggedInPage(browser, baseURL);
    try {
      for (let round = 1; round <= ROUNDS; round++) {
        const id = await confirmSmallOrder(a.page);
        const { version } = (await detail(a.page.request, id)).body;

        const [progress, cancel] = await Promise.all([
          post(a.page.request, `/api/orders/${id}/status`, { status: 'IN_PROGRESS', version }),
          post(b.page.request, `/api/orders/${id}/status`, { status: 'CANCELED', version }),
        ]);

        const all = [progress.status, cancel.status];
        expect(Math.max(...all), `${round}회차 서버 오류(5xx) 없음: 진행 ${progress.status}, 취소 ${cancel.status}`).toBeLessThan(500);
        expect(all.filter((s) => s === 200), `${round}회차 성공은 정확히 한 건: 진행 ${progress.status}, 취소 ${cancel.status}`).toHaveLength(1);
        const failed = progress.status === 200 ? cancel : progress;
        expect([404, 409], `${round}회차 나머지 한 건은 404 또는 409`).toContain(failed.status);

        const after = await detail(a.page.request, id);
        if (cancel.status === 200) {
          expect(after.status, `${round}회차 취소가 성공했으면 주문은 삭제됨`).toBe(404);
        } else {
          expect(after.body.status.name, `${round}회차 진행이 성공했으면 상태는 진행`).toBe('IN_PROGRESS');
          const last = after.body.history.at(-1);
          expect(last.toStatus.name, `${round}회차 마지막 이력`).toBe('IN_PROGRESS');
          await cancelViaApi(a.page, id);
        }
      }
    } finally {
      await a.context.close();
      await b.context.close();
    }
  });

  test('CON-03 같은 입력으로 주문을 동시에 확정해도 모두 저장되고 번호·이력이 섞이지 않는다', async ({ page }) => {
    await login(page);
    const master = await (await page.request.get('/api/master')).json();
    const before = await orderSummary(page);
    const ids: number[] = [];
    try {
      const replies = await Promise.all(
        Array.from({ length: CONCURRENT_CONFIRMS }, () =>
          post(page.request, '/api/orders', { orders: SMALL_ORDER, routeType: 'COST', masterVersion: master.version, memo: '' }),
        ),
      );
      for (const r of replies) if (r.status === 200) ids.push(r.body.id);

      expect(replies.map((r) => r.status), '동시 확정 응답 코드').toEqual(Array(CONCURRENT_CONFIRMS).fill(200));
      expect(new Set(ids).size, '주문 번호 중복 없음').toBe(CONCURRENT_CONFIRMS);
      expect((await orderSummary(page)).total, '전체 건수').toBe(before.total + CONCURRENT_CONFIRMS);

      for (const id of ids) {
        const d = (await detail(page.request, id)).body;
        expect(d.history, `주문 #${id} 이력은 '없음→확정' 한 줄`).toHaveLength(1);
        expect(d.history[0].fromStatus ?? null, `주문 #${id} 이력의 이전 상태`).toBeNull();
        expect(d.history[0].toStatus.name, `주문 #${id} 이력의 새 상태`).toBe('CONFIRMED');
      }
    } finally {
      for (const id of ids) {
        await cancelViaApi(page, id);
      }
    }
  });
});
