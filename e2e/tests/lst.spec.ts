import { test, expect, request as playwrightRequest, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 주문 목록 (LST-01 ~ LST-06)
 * 시나리오 출처: docs/e2e/e2e-test-scenarios.xlsx '시나리오' 시트
 *
 * 전제: 실행 중인 서버를 사용한다. 서버와 DB 는 하나를 공유하므로 주문 건수는 '절대값'이 아니라
 * '내가 만든 주문이 보이는지'로 검증한다.
 * DB 변경: LST-02, 03, 06 은 준비 단계에서 주문을 만든다(LST-02 는 상태도 '진행'으로 바꾼다).
 * LST-04 는 주문이 0건인 깨끗한 DB 에서만 실행되며, 주문이 있으면 건너뛴다.
 */

const ADMIN = { username: 'admin', password: 'admin' };
const DEFAULT_ORDERS = { CHINA: 3000, JAPAN: 3000, VIETNAM: 3000, INDONESIA: 3000 };

// ---------------------------------------------------------------- 도우미

/** 화면으로 로그인한다. 끝나면 주문 목록(/orders)에 있다. */
async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('아이디').fill(ADMIN.username);
  await page.getByLabel('비밀번호').fill(ADMIN.password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/orders$/);
}

/** 로그인된 API 요청 컨텍스트 (화면 없이 서버에 직접 요청). 쓰고 나면 dispose() 한다. */
async function loginApi(baseURL: string | undefined): Promise<APIRequestContext> {
  const api = await playwrightRequest.newContext({ baseURL });
  const res = await api.post('/login', { form: ADMIN });
  expect(res.ok()).toBeTruthy();
  return api;
}

/** API 로 주문 1건을 확정하고 주문 번호를 돌려준다 (DB 에 주문이 쌓인다) */
async function createOrder(api: APIRequestContext, memo: string): Promise<number> {
  const master = await (await api.get('/api/master')).json();
  const res = await api.post('/api/orders', {
    data: { orders: DEFAULT_ORDERS, routeType: 'COST', masterVersion: master.version, memo },
  });
  expect(res.status()).toBe(200);
  return (await res.json()).id as number;
}

/** 주문 상세 화면에서 '진행(으)로 변경'을 누른다. 확인 창(confirm)은 자동으로 수락한다. */
async function moveToInProgress(page: Page, orderId: number) {
  await page.goto(`/orders/${orderId}`);
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '진행(으)로 변경' }).click();
  await expect(page.locator('.alert.ok')).toContainText("'진행'");
}

// ---------------------------------------------------------------- 테스트

test.describe('주문 목록', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // LST-04 는 주문이 0건인 깨끗한 DB 를 전제로 하므로, 다른 테스트가 주문을 만들기 전에 가장 먼저 실행되도록 맨 앞에 둔다.
  // (data/ 를 지우고 앱을 새로 시작한 뒤 이 파일을 가장 먼저 실행해야 한다. 주문이 이미 있으면 건너뛴다.)
  test('LST-04 주문이 없을 때 안내 문구', async ({ page }) => {
    await page.goto('/orders');
    const rows = await page.locator('tbody tr.clickable').count();
    test.skip(rows > 0, '주문이 이미 있어 빈 상태를 재현할 수 없음 (깨끗한 DB 에서만 실행)');

    await expect(page.locator('td.empty')).toHaveText('주문이 없습니다.');
  });

  test('LST-01 목록 컬럼과 상태별 건수 칩', async ({ page }) => {
    await page.goto('/orders');

    await expect(page.locator('table.grid th')).toHaveText([
      '주문번호', '확정 일시', '총 수량(K)', '경로', '소요 일수', '총 비용(억원)', '상태', '작성자',
    ]);

    const chips = page.locator('.filters .chip');
    await expect(chips).toHaveCount(5);
    for (const [i, label] of ['전체', '확정', '진행', '완료', '취소'].entries()) {
      await expect(chips.nth(i)).toContainText(label);
      await expect(chips.nth(i).locator('.count')).toHaveText(/^\d+$/); // 칩마다 건수(숫자)가 있다
    }
  });

  test('LST-02 상태 칩 필터', async ({ page, baseURL }) => {
    // 준비: '확정' 주문 1건과 '진행' 주문 1건
    const api = await loginApi(baseURL);
    const confirmedId = await createOrder(api, 'LST-02 확정');
    const inProgressId = await createOrder(api, 'LST-02 진행');
    await api.dispose();
    await moveToInProgress(page, inProgressId);

    const link = (id: number) => page.getByRole('link', { name: `#${id}`, exact: true });

    // '진행' 칩: 진행 주문만 보인다
    await page.goto('/orders');
    await page.locator('.filters .chip').filter({ hasText: '진행' }).click();
    await expect(page).toHaveURL(/\?status=IN_PROGRESS$/);
    await expect(page.locator('.filters .chip.active')).toContainText('진행');
    await expect(link(inProgressId)).toBeVisible();
    await expect(link(confirmedId)).toHaveCount(0);
    await expect(page.locator('tbody .badge-CONFIRMED')).toHaveCount(0);

    // '전체' 칩: 둘 다 보인다
    await page.locator('.filters .chip').filter({ hasText: '전체' }).click();
    await expect(page).toHaveURL(/\/orders$/);
    await expect(link(inProgressId)).toBeVisible();
    await expect(link(confirmedId)).toBeVisible();
  });

  test('LST-03 행 클릭 시 상세 화면으로 이동', async ({ page, baseURL }) => {
    const api = await loginApi(baseURL);
    await createOrder(api, 'LST-03');
    await api.dispose();

    await page.goto('/orders');
    const firstRow = page.locator('tbody tr.clickable').first();
    const orderNo = (await firstRow.locator('td').first().innerText()).replace('#', '');

    // 링크가 아닌 칸(확정 일시)을 눌러도 행 전체가 클릭된다
    await firstRow.locator('td').nth(1).click();
    await expect(page).toHaveURL(new RegExp(`/orders/${orderNo}$`));
    await expect(page.locator('h1')).toContainText(`#${orderNo}`);
  });

  test('LST-05 상단 내비게이션 이동', async ({ page }) => {
    await page.goto('/orders');
    const nav = page.locator('header nav');

    for (const [name, url] of [
      ['주문 추가', /\/orders\/new$/],
      ['마스터 데이터', /\/master$/],
      ['주문 목록', /\/orders$/],
    ] as const) {
      await nav.getByRole('link', { name }).click();
      await expect(page).toHaveURL(url);
      await expect(page.locator('main h1')).toHaveText(name);
      await expect(nav.locator('a.active')).toHaveText(name); // 현재 메뉴 강조
    }

    // 브랜드명을 누르면 주문 목록으로 간다
    await page.goto('/master');
    await page.locator('header .brand').click();
    await expect(page).toHaveURL(/\/orders$/);
  });

  test('LST-06 주문 목록은 최신순(번호 내림차순)', async ({ page, baseURL }) => {
    const api = await loginApi(baseURL);
    const first = await createOrder(api, 'LST-06 먼저');
    const second = await createOrder(api, 'LST-06 나중');
    await api.dispose();

    await page.goto('/orders');
    const numbers = (await page.locator('tbody tr.clickable td:first-child a').allTextContents()).map((t) => Number(t.replace('#', '')));

    expect(numbers[0]).toBe(second); // 나중에 만든 주문이 맨 위
    expect(numbers[1]).toBe(first);
    expect(numbers).toEqual([...numbers].sort((a, b) => b - a)); // 전체가 내림차순
  });
});
