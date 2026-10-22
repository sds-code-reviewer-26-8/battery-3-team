import { expect, test } from '@playwright/test';
import { createOrderViaApi, login } from './helpers';

/**
 * 정적 화면 + JSON API 전환(리팩토링 2) 확인 (PGE-01, 03 ~ 06)
 *
 * 화면 주소는 그대로이고, 화면 데이터는 JSON API 로 받아 온다.
 * 기존 시나리오(주문·마스터·인증)가 이미 화면 동작을 덮으므로, 여기서는 전환으로 새로 정해진 동작만 확인한다.
 * DB 변경: PGE-06 이 주문 1건을 만든다. 나머지는 DB 를 바꾸지 않는다.
 */
test.describe('정적 화면 전환', () => {
  test('PGE-01 존재하지 않는 주문을 API 로 조회하면 404 와 이유(JSON)가 온다', async ({ page }) => {
    // 화면 주소(/orders/{id})는 없는 주문이면 아직 서버 오류가 난다(알려진 결함, order-detail.spec DTL-05·08).
    await login(page);
    const api = await page.request.get('/api/orders/999999');
    expect(api.status()).toBe(404);
    expect(await api.json()).toEqual({ error: '주문을 찾을 수 없습니다', conflict: false });
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
});
