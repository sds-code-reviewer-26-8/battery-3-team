import { test, expect, request as playwrightRequest, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 인증 (AUTH-01 ~ AUTH-09)
 * 시나리오 출처: docs/e2e/e2e-test-scenarios.xlsx '시나리오' 시트
 *
 * 전제: 실행 중인 서버를 사용한다. 계정은 admin / admin 하나뿐이다.
 * 테스트마다 새 브라우저 컨텍스트(쿠키 없음)로 시작하므로, 로그인하지 않으면 항상 로그아웃 상태다.
 * DB 변경: AUTH-09 만 준비 단계에서 주문 1건을 만든다. 나머지는 DB 를 바꾸지 않는다.
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

// ---------------------------------------------------------------- 테스트

test.describe('인증', () => {
  test('AUTH-01 비로그인 상태에서 보호된 화면 접근 시 로그인 화면으로 이동', async ({ page }) => {
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('AUTH-02 admin/admin 로그인 성공', async ({ page }) => {
    await page.goto('/login');
    // 화면에 보이는 라벨·버튼 이름으로 요소를 찾는다 (CSS 선택자보다 화면 구조 변경에 덜 깨진다)
    await page.getByLabel('아이디').fill(ADMIN.username);
    await page.getByLabel('비밀번호').fill(ADMIN.password);
    await page.getByRole('button', { name: '로그인' }).click();

    await expect(page).toHaveURL(/\/orders$/);
    await expect(page.locator('header .user')).toContainText('admin');
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
  });

  test('AUTH-03 잘못된 비밀번호로 로그인 실패', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('아이디').fill(ADMIN.username);
    await page.getByLabel('비밀번호').fill('wrong');
    await page.getByRole('button', { name: '로그인' }).click();

    // 로그인 화면을 처음 열면 서버가 폼 주소 끝에 세션 번호(;jsessionid=...)를 붙일 수 있어 허용한다
    await expect(page).toHaveURL(/\/login(;jsessionid=[^?#]*)?$/);
    await expect(page.locator('.alert.error')).toHaveText('아이디 또는 비밀번호가 올바르지 않습니다');

    // 세션이 만들어지지 않았으므로 보호된 화면은 여전히 막혀 있다
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('AUTH-04 로그인 상태에서 /login 접근 시 주문 목록으로 이동', async ({ page }) => {
    await login(page);
    await page.goto('/login');
    await expect(page).toHaveURL(/\/orders$/);
  });

  test('AUTH-05 로그아웃', async ({ page }) => {
    await login(page);
    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(page).toHaveURL(/\/login$/);

    // 로그아웃 뒤에는 보호된 화면이 다시 막힌다
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('AUTH-06 상단에 로그인 사용자 표시', async ({ page }) => {
    await login(page);
    for (const path of ['/orders', '/orders/new', '/master']) {
      await page.goto(path);
      await expect(page.locator('header .user span')).toHaveText('admin');
      await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
    }
  });

  // 아래 3건은 화면 없이 HTTP 요청만 보낸다. `request` 는 쿠키가 없는 요청 도구라 로그아웃 상태와 같다.
  test('AUTH-07 비로그인 API 호출은 401', async ({ request }) => {
    const master = await request.get('/api/master');
    expect(master.status()).toBe(401); // 리다이렉트(302)가 아니라 401 이어야 한다

    const simulate = await request.post('/api/simulate', { data: { orders: DEFAULT_ORDERS } });
    expect(simulate.status()).toBe(401);
  });

  test('AUTH-08 정적 리소스는 로그인 없이 접근 가능', async ({ request }) => {
    for (const path of ['/css/app.css', '/js/common.js']) {
      const res = await request.get(path);
      expect(res.status(), path).toBe(200);
    }
  });

  test('AUTH-09 비로그인 상태의 상태 변경 요청은 로그인으로 이동하고 변경되지 않음', async ({ page, request, baseURL }) => {
    // 준비: 로그인한 API 로 '확정' 상태 주문을 하나 만든다
    const api = await loginApi(baseURL);
    const orderId = await createOrder(api, 'AUTH-09');
    await api.dispose();

    // 로그인하지 않은 요청으로 상태 변경을 시도한다 (리다이렉트를 따라가지 않고 응답을 그대로 본다)
    const res = await request.post(`/orders/${orderId}/status`, {
      form: { status: 'IN_PROGRESS', version: '0' },
      maxRedirects: 0,
    });
    expect(res.status()).toBe(302);
    expect(res.headers()['location']).toMatch(/\/login$/);

    // 로그인해서 확인: 주문은 '확정' 그대로이고 상태 이력도 1건뿐이다
    await login(page);
    await page.goto(`/orders/${orderId}`);
    await expect(page.locator('h1 .badge')).toHaveText('확정');
    await expect(page.locator('section.card', { has: page.getByRole('heading', { name: '상태 이력' }) }).locator('tbody tr')).toHaveCount(1);
  });
});
