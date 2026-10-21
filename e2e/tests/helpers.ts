import { expect, type Browser, type Locator, type Page } from '@playwright/test';

/** 기본 주문: 네 나라 3,000K 씩 (총 12,000K) */
export const DEFAULT_ORDERS = { CHINA: 3000, JAPAN: 3000, VIETNAM: 3000, INDONESIA: 3000 };

export async function login(page: Page) {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('admin');
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/orders$/);
}

/** 두 번째 사용자(별도 세션)가 필요한 시나리오용: 새 브라우저 컨텍스트로 로그인한 페이지를 만든다. */
export async function newLoggedInPage(browser: Browser, baseURL: string | undefined) {
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await login(page);
  return { context, page };
}

/** 주문 추가 화면에서 기본 수량으로 시뮬레이션을 실행해 결과 영역이 보이게 한다. */
export async function runSimulation(page: Page) {
  await page.goto('/orders/new');
  await page.locator('#runBtn').click();
  await expect(page.locator('#result')).toBeVisible();
}

/** 사전조건용: API 로 주문을 만들고 주문 번호를 돌려준다. 로그인된 page 가 필요하다. */
export async function createOrderViaApi(
  page: Page,
  opts: { routeType?: 'COST' | 'FAST' | 'NEAR'; memo?: string } = {},
): Promise<number> {
  const master = await (await page.request.get('/api/master')).json();
  const res = await page.request.post('/api/orders', {
    data: {
      orders: DEFAULT_ORDERS,
      routeType: opts.routeType ?? 'COST',
      masterVersion: master.version,
      memo: opts.memo ?? '',
    },
  });
  expect(res.ok(), '주문 생성 API 가 성공해야 한다').toBeTruthy();
  return (await res.json()).id;
}

/** 주문 상세에서 상태 변경 버튼을 누르고 확인 대화상자를 수락한다. 대화상자 문구를 돌려준다. */
export async function changeStatus(page: Page, buttonLabel: string): Promise<string> {
  let message = '';
  page.once('dialog', async (dialog) => {
    message = dialog.message();
    await dialog.accept();
  });
  await page.getByRole('button', { name: buttonLabel }).click();
  return message;
}

/** 주문 상세의 메타 정보(확정 일시·작성자·경로·메모) 값 */
export function metaValue(page: Page, label: string): Locator {
  return page
    .locator('dl.meta > div')
    .filter({ has: page.locator('dt', { hasText: label }) })
    .locator('dd');
}

/** 주문 상세의 '상태 이력' 표 행들 */
export function historyRows(page: Page): Locator {
  return page
    .locator('section.card')
    .filter({ has: page.getByRole('heading', { name: '상태 이력' }) })
    .locator('tbody tr');
}

/** 주문 목록 상단 칩의 건수 [전체, 확정, 진행, 완료, 취소] */
export async function chipCounts(page: Page): Promise<number[]> {
  await page.goto('/orders');
  const texts = await page.locator('.filters .chip .count').allTextContents();
  return texts.map((t) => Number(t.replace(/,/g, '').trim()));
}

export async function currentShipCount(page: Page): Promise<number> {
  return (await (await page.request.get('/api/master')).json()).shipCount;
}

/** 마스터 화면에서 배 척수를 바꿔 저장한다(마스터 버전이 올라간다). */
export async function setShipCount(page: Page, count: number) {
  await page.goto('/master');
  await page.locator('input[name="shipCount"]').fill(String(count));
  await page.getByRole('button', { name: '저장' }).click();
  await expect(page.locator('.alert.ok')).toContainText('마스터 데이터를 저장했습니다');
}
