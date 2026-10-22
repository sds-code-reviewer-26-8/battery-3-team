import { APIRequestContext, Page, expect, request } from '@playwright/test';

export const COUNTRIES = ['CHINA', 'JAPAN', 'VIETNAM', 'INDONESIA'] as const;
export type Country = (typeof COUNTRIES)[number];

/** 기본 주문: 네 나라에 3,000K 씩 (총 12,000K) */
export const DEFAULT_ORDERS: Record<Country, number> = {
  CHINA: 3000,
  JAPAN: 3000,
  VIETNAM: 3000,
  INDONESIA: 3000,
};

/** 앱 시작 시 들어가는 기본 마스터 값 (BatteryService.initDefaults) */
const MAX_DAILY: Record<Country, number> = { CHINA: 400, JAPAN: 800, VIETNAM: 200, INDONESIA: 300 };
const STOCK_LIMIT: Record<Country, number> = { CHINA: 4000, JAPAN: 2000, VIETNAM: 10000, INDONESIA: 7000 };
const UNIT_COST: Record<Country, number> = { CHINA: 700, JAPAN: 900, VIETNAM: 550, INDONESIA: 450 };

/** 항로 입력칸 이름 = days_<앞선 나라>_<뒤 나라> (Country 선언 순서) */
const DAYS: Record<string, number> = {
  KOREA_CHINA: 1,
  KOREA_JAPAN: 2,
  KOREA_VIETNAM: 5,
  KOREA_INDONESIA: 7,
  CHINA_JAPAN: 2,
  CHINA_VIETNAM: 4,
  CHINA_INDONESIA: 6,
  JAPAN_VIETNAM: 5,
  JAPAN_INDONESIA: 7,
  VIETNAM_INDONESIA: 2,
};

/** 마스터 화면 폼의 기본값 (입력칸 name → 값) */
export function defaultMasterForm(): Record<string, string> {
  const form: Record<string, string> = { shipCount: '3', shipDailyCost: '1' };
  for (const c of COUNTRIES) {
    form[`maxDaily_${c}`] = String(MAX_DAILY[c]);
    form[`stockLimit_${c}`] = String(STOCK_LIMIT[c]);
    form[`unitCost_${c}`] = String(UNIT_COST[c]);
  }
  for (const [pair, days] of Object.entries(DAYS)) {
    form[`days_${pair}`] = String(days);
  }
  return form;
}

export const ROUTE_KEYS = Object.keys(DAYS).map((pair) => `days_${pair}`);

// ---------------------------------------------------------------- 화면(UI)

export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('input[name=username]').fill('admin');
  await page.locator('input[name=password]').fill('admin');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page).toHaveURL(/\/orders$/);
}

export async function gotoMaster(page: Page): Promise<void> {
  await page.goto('/master');
  await expect(page.locator('input[name=shipCount]')).toBeVisible();
}

/** 브라우저 기본 검증(min/max)을 꺼서 서버 검증 메시지까지 도달하게 한다. */
export async function disableBrowserValidation(page: Page): Promise<void> {
  await page.evaluate(() => {
    const form = document.querySelector('form[action="/master"]') as HTMLFormElement;
    form.noValidate = true;
  });
}

export async function fillMaster(page: Page, values: Record<string, string>): Promise<void> {
  for (const [name, value] of Object.entries(values)) {
    await page.locator(`[name="${name}"]`).fill(value);
  }
}

export async function saveMaster(page: Page): Promise<void> {
  await page.getByRole('button', { name: '저장', exact: true }).click();
}

export const SAVED_MESSAGE = '마스터 데이터를 저장했습니다';
export const SAVE_REFUSED_MESSAGE = '저장하지 않았습니다. 아래 값을 확인하세요.';

// ---------------------------------------------------------------- API (로그인 세션)

/** admin 으로 로그인한 API 컨텍스트. 쿠키(세션)를 유지한다. */
export async function newApi(baseURL: string): Promise<APIRequestContext> {
  const api = await request.newContext({ baseURL });
  const res = await api.post('/login', { form: { username: 'admin', password: 'admin' } });
  expect(res.ok()).toBeTruthy();
  return api;
}

/** 마스터 값을 기본값으로 되돌린다. 변경 번호(masterVersion)는 되돌릴 수 없고 계속 올라간다. */
export async function restoreMaster(api: APIRequestContext): Promise<void> {
  const res = await api.put('/api/master', { data: defaultMasterForm() });
  expect(res.ok(), '마스터 원복 실패').toBeTruthy();
}

export async function currentMasterVersion(api: APIRequestContext): Promise<number> {
  const res = await api.get('/api/master');
  expect(res.ok()).toBeTruthy();
  return (await res.json()).version as number;
}

/** 확정 주문 하나를 만들고 주문 번호를 돌려준다 (FAST 경로, 기본 주문). */
export async function createOrder(api: APIRequestContext, memo = 'E2E'): Promise<number> {
  const sim = await api.post('/api/simulate', { data: { orders: DEFAULT_ORDERS } });
  expect(sim.ok()).toBeTruthy();
  const masterVersion = (await sim.json()).master.version as number;
  const res = await api.post('/api/orders', {
    data: { orders: DEFAULT_ORDERS, routeType: 'FAST', masterVersion, memo },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).id as number;
}

/** 전체 주문 건수 (주문 목록 API) */
export async function countOrders(api: APIRequestContext): Promise<number> {
  const res = await api.get('/api/orders');
  expect(res.ok()).toBeTruthy();
  return (await res.json()).orders.length as number;
}
