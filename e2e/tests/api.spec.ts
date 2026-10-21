import { APIRequestContext, expect, test } from '@playwright/test';
import { DEFAULT_ORDERS, countOrders, currentMasterVersion, newApi } from './hg.helpers';

/**
 * API (API-01 ~ API-07)
 * 기준: docs/e2e/e2e-test-scenarios.xlsx
 *
 * 로그인한 세션(쿠키)으로 JSON API 를 직접 호출한다.
 * 확정이 거부되는 케이스는 주문이 만들어지지 않았는지도 목록 건수로 확인한다.
 */
let api: APIRequestContext;

test.beforeAll(async () => {
  api = await newApi(test.info().project.use.baseURL!);
});

test.afterAll(async () => {
  await api.dispose();
});

test.describe('API', () => {
  test('API-01 GET /api/master 응답 구조', async () => {
    const res = await api.get('/api/master');
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body).toEqual(
      expect.objectContaining({
        version: expect.any(Number),
        shipCount: expect.any(Number),
        shipDailyCostWon: expect.any(Number),
      }),
    );
    expect(body.factories).toHaveLength(4);
    expect(body.routes).toHaveLength(10);
  });

  test('API-02 수량이 모두 0 인 시뮬레이션은 400', async () => {
    const res = await api.post('/api/simulate', { data: { orders: {} } });
    expect(res.status()).toBe(400);
    expect(await res.json()).toEqual({
      error: '총 생산 필요 수는 1K 이상이어야 합니다',
      conflict: false,
    });
  });

  test('API-03 음수 수량은 400', async () => {
    const res = await api.post('/api/simulate', { data: { orders: { CHINA: -1 } } });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('중국 주문 수량은 0 이상이어야 합니다');
    expect(body.conflict).toBe(false);
  });

  test('API-04 잘못된 routeType 으로 확정하면 400, 주문은 만들어지지 않는다', async () => {
    const before = await countOrders(api);
    const masterVersion = await currentMasterVersion(api);

    const res = await api.post('/api/orders', {
      data: { orders: DEFAULT_ORDERS, routeType: 'XXX', masterVersion },
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).error).toBe('선택한 경로를 찾을 수 없습니다');

    expect(await countOrders(api)).toBe(before);
  });

  test('API-05 오래된 masterVersion 으로 확정하면 409', async () => {
    const before = await countOrders(api);
    const masterVersion = (await currentMasterVersion(api)) - 1;

    const res = await api.post('/api/orders', {
      data: { orders: DEFAULT_ORDERS, routeType: 'COST', masterVersion },
    });
    expect(res.status()).toBe(409);
    const body = await res.json();
    expect(body.conflict).toBe(true);
    expect(body.error).toContain('시뮬레이션 이후 마스터 데이터가 바뀌었습니다');

    expect(await countOrders(api)).toBe(before);
  });

  test('API-06 메모가 1000자를 넘으면 400, 주문은 만들어지지 않는다', async () => {
    const before = await countOrders(api);
    const masterVersion = await currentMasterVersion(api);

    const res = await api.post('/api/orders', {
      data: { orders: DEFAULT_ORDERS, routeType: 'COST', masterVersion, memo: 'a'.repeat(1001) },
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).error).toBe('메모는 1000자 이하로 입력하세요');

    expect(await countOrders(api)).toBe(before);
  });

  test('API-07 [탐색] 알 수 없는 국가 키는 서버 오류(5xx)가 아니어야 한다', async () => {
    // 현재 동작을 모르는 탐색 시나리오. 기대: 4xx. 5xx 이면 결함 후보로 올린다.
    const res = await api.post('/api/simulate', { data: { orders: { MARS: 100 } } });
    test.info().annotations.push({ type: '탐색 결과', description: `HTTP ${res.status()}` });

    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });
});
