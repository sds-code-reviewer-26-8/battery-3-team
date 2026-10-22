import { expect, test } from '@playwright/test';
import { changeStatus, createOrderViaApi, login, newLoggedInPage } from './helpers';

/**
 * 정적 화면 + JSON API 전환(리팩토링 2) 확인 (PGE-01 ~ PGE-10)
 *
 * 화면 주소는 그대로이고, 화면 데이터는 JSON API 로 받아 온다.
 * 기존 시나리오(주문·마스터·인증)가 이미 화면 동작을 덮으므로, 여기서는 전환으로 새로 정해진 동작만 확인한다.
 * DB 변경: PGE-02, 07 ~ 10 이 주문을 만들었다가 취소(삭제)하고, PGE-06 은 주문 1건을 만든다. 나머지는 DB 를 바꾸지 않는다.
 */
test.describe('정적 화면 전환', () => {
  test('PGE-01 존재하지 않는 주문을 열면 서버 오류 대신 안내가 나온다', async ({ page }) => {
    await login(page);
    const response = await page.goto('/orders/999999');

    expect(response!.status()).toBe(200); // 화면(정적 HTML) 자체는 열린다
    await expect(page.locator('.alert.error')).toHaveText('주문을 찾을 수 없습니다');
    await expect(page.getByRole('link', { name: '목록으로' })).toBeVisible();
    await expect(page).toHaveURL(/\/orders\/999999$/);

    // 같은 주문을 API 로 요청하면 404 와 이유(JSON)가 온다
    const api = await page.request.get('/api/orders/999999');
    expect(api.status()).toBe(404);
    expect(await api.json()).toEqual({ error: '주문을 찾을 수 없습니다', conflict: false });
  });

  test('PGE-02 취소하면 같은 화면에 안내가 나오고 주문은 목록에서 사라진다', async ({ page }) => {
    await login(page);
    const id = await createOrderViaApi(page, { memo: 'PGE-02' });
    await page.goto(`/orders/${id}`);

    await changeStatus(page, '취소(으)로 변경');

    await expect(page.locator('.alert.ok')).toContainText(`주문 #${id}을(를) 취소했습니다`);
    await expect(page.locator('h1 .badge')).toHaveText('취소');
    await expect(page.getByRole('button', { name: /\(으\)로 변경/ })).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(`/orders/${id}$`));

    await page.getByRole('link', { name: '목록으로' }).click();
    await expect(page).toHaveURL(/\/orders$/);
    await expect(page.locator('tbody a', { hasText: new RegExp(`^#${id}$`) })).toHaveCount(0);
  });

  test('PGE-03 로그인하지 않으면 모든 화면 주소가 로그인으로 이동한다', async ({ page }) => {
    for (const path of ['/orders', '/orders/new', '/orders/1', '/master']) {
      await page.goto(path);
      await expect(page, path).toHaveURL(/\/login$/);
    }
  });

  test('PGE-04 로그인 요청이 틀리면 401 과 이유가 온다 (폼 전송 계약 유지)', async ({ request }) => {
    const bad = await request.post('/login', { form: { username: 'admin', password: 'wrong' }, maxRedirects: 0 });
    expect(bad.status()).toBe(401);
    expect((await bad.json()).error).toBe('아이디 또는 비밀번호가 올바르지 않습니다');

    const ok = await request.post('/login', { form: { username: 'admin', password: 'admin' }, maxRedirects: 0 });
    expect(ok.status()).toBe(302);
    expect(ok.headers()['location']).toMatch(/\/orders$/);
  });

  test('PGE-05 화면 상단은 로그인한 아이디를 API 로 받아 보여 준다', async ({ page }) => {
    await login(page);
    const me = await page.request.get('/api/me');
    expect(await me.json()).toEqual({ username: 'admin' });
    for (const path of ['/orders', '/orders/new', '/master']) {
      await page.goto(path);
      await expect(page.locator('header .user span'), path).toHaveText('admin');
    }
  });
  test('PGE-06 메모에 태그를 넣어도 글자 그대로 보이고 실행되지 않는다', async ({ page }) => {
    // 서버가 HTML 을 만들 때는 자동으로 이스케이프됐지만, JS 가 그리게 되면서 화면 스크립트가 직접 이스케이프한다.
    await login(page);
    const memo = '<b>굵게</b><img src=x onerror="window.__xss=1">';
    const id = await createOrderViaApi(page, { memo });
    await page.goto(`/orders/${id}`);

    await expect(page.locator('dl.meta dd', { hasText: '굵게' })).toHaveText(memo); // 태그가 문자 그대로 보인다
    await expect(page.locator('dl.meta dd b, dl.meta dd img')).toHaveCount(0); // 요소로 만들어지지 않는다
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined(); // 스크립트가 돌지 않았다

    await page.goto('/orders'); // 목록에도 메모는 없지만, 같은 방식으로 그려지는 작성자·경로 칸이 깨지지 않는다
    await expect(page.locator('tbody tr.clickable').first()).toBeVisible();
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
  });
  test('PGE-07 취소한 주문 화면을 새로고침하면 서버 오류 대신 찾을 수 없다는 안내가 나온다', async ({ page }) => {
    await login(page);
    const id = await createOrderViaApi(page, { memo: 'PGE-07' });
    await page.goto(`/orders/${id}`);
    await changeStatus(page, '취소(으)로 변경');
    await expect(page.locator('.alert.ok')).toContainText('취소했습니다');

    // 주문은 이미 삭제되었으므로 새로 고치면 "없는 주문" 안내로 바뀐다(취소하면 삭제하는 정책, PRD Q2)
    const response = await page.reload();
    expect(response!.status(), '새로 고쳐도 서버 오류(5xx)면 안 된다').toBe(200);
    await expect(page.locator('.alert.error')).toHaveText('주문을 찾을 수 없습니다');
    await expect(page.getByRole('link', { name: '목록으로' })).toBeVisible();
  });

  test('PGE-08 다른 사용자가 먼저 취소하면 옛 화면의 변경은 찾을 수 없다는 안내가 나온다', async ({
    page,
    browser,
    baseURL,
  }) => {
    await login(page);
    const id = await createOrderViaApi(page, { memo: 'PGE-08' });
    const { context, page: other } = await newLoggedInPage(browser, baseURL);
    try {
      await page.goto(`/orders/${id}`); // 사용자 A (이 화면은 곧 옛 화면이 된다)
      await other.goto(`/orders/${id}`); // 사용자 B
      await changeStatus(other, '취소(으)로 변경'); // B 가 먼저 취소(주문 삭제)
      await expect(other.locator('.alert.ok')).toContainText('취소했습니다');

      await changeStatus(page, '진행(으)로 변경'); // A(옛 화면)가 변경 시도 → 서버는 404
      await expect(page.locator('.alert.error')).toHaveText('주문을 찾을 수 없습니다');
      await expect(page.getByRole('link', { name: '목록으로' })).toBeVisible();
    } finally {
      await context.close();
    }
  });

  test('PGE-09 취소하는 동안 서버 오류 응답과 화면 오류가 한 건도 없다', async ({ page }) => {
    await login(page);
    const id = await createOrderViaApi(page, { memo: 'PGE-09' });
    await page.goto(`/orders/${id}`);

    const serverErrors: string[] = [];
    const screenErrors: string[] = [];
    page.on('response', (r) => {
      if (r.status() >= 500) serverErrors.push(`${r.status()} ${r.url()}`);
    });
    page.on('pageerror', (e) => screenErrors.push(e.message));
    page.on('console', (m) => {
      if (m.type() === 'error') screenErrors.push(m.text());
    });

    await changeStatus(page, '취소(으)로 변경');
    await expect(page.locator('.alert.ok')).toContainText(`주문 #${id}을(를) 취소했습니다`);

    expect(serverErrors, '취소 중 5xx 응답이 없어야 한다').toEqual([]);
    expect(screenErrors, '취소 중 화면 오류(콘솔·스크립트)가 없어야 한다').toEqual([]);
  });

  test('PGE-10 진행 상태 주문도 취소하면 같은 화면에 안내가 나오고 주문은 삭제된다', async ({ page }) => {
    await login(page);
    const id = await createOrderViaApi(page, { memo: 'PGE-10' });
    await page.goto(`/orders/${id}`);
    await changeStatus(page, '진행(으)로 변경');
    await expect(page.locator('h1 .badge')).toHaveText('진행');

    await changeStatus(page, '취소(으)로 변경');

    await expect(page.locator('.alert.ok')).toContainText(`주문 #${id}을(를) 취소했습니다`);
    await expect(page.locator('h1 .badge')).toHaveText('취소');
    await expect(page.getByRole('button', { name: /\(으\)로 변경/ })).toHaveCount(0);
    expect((await page.request.get(`/api/orders/${id}`)).status()).toBe(404);
  });
});
