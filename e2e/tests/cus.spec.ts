import { test, expect, type Page } from '@playwright/test';
import {
  advanceViaApi,
  createOrderViaApi,
  login,
  metaValue,
  newLoggedInPage,
  runSimulation,
} from './helpers';

// 담당: 고객사 이름 입력·수정·표시 (CUS-01 ~ CUS-08, TASK-006)
// 주문은 API 로 만들어 시나리오마다 독립적으로 실행한다. 모두 인메모리 서버(기본 18080)에서만 실행한다.

const XSS = '<img src=x onerror="window.__xss=1">';

/** 목록에서 해당 주문번호 행의 고객사 칸(맨 끝 열) */
function customerCell(page: Page, id: number) {
  return page.locator(`tbody tr[data-id="${id}"] td`).last();
}

test.describe('고객사 이름', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('CUS-01 주문 추가에서 고객사를 입력해 확정하면 상세와 목록에 보인다', async ({ page }) => {
    await runSimulation(page);
    await expect(page.locator('#customerName')).toHaveAttribute('maxlength', '100');
    await page.locator('#customerName').fill('  ABC전자  ');
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const id = Number(page.url().split('/').pop());
    await expect(metaValue(page, '고객사')).toHaveText('ABC전자');

    await page.goto('/orders');
    await expect(customerCell(page, id)).toHaveText('ABC전자');
  });

  test('CUS-02 고객사를 비워도 확정되고 상세와 목록에 - 로 보인다', async ({ page }) => {
    await runSimulation(page);
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const id = Number(page.url().split('/').pop());
    await expect(metaValue(page, '고객사')).toHaveText('-');

    await page.goto('/orders');
    await expect(customerCell(page, id)).toHaveText('-');
  });

  test('CUS-03 확정 상태에서 고객사를 수정해 저장한다', async ({ page }) => {
    const id = await createOrderViaApi(page, { customerName: '처음 고객사' });
    await page.goto(`/orders/${id}`);

    const input = page.locator('#customerForm input[name="customerName"]');
    await expect(input).toHaveValue('처음 고객사');
    await input.fill('바뀐 고객사');
    await page.getByRole('button', { name: '고객사 저장' }).click();

    await expect(page.locator('.alert.ok')).toHaveText('고객사 이름을 저장했습니다');
    await expect(metaValue(page, '고객사')).toHaveText('바뀐 고객사');
    await expect(page.locator('#customerForm input[name="customerName"]')).toHaveValue('바뀐 고객사');
  });

  test('CUS-04 진행 상태에서도 고객사를 수정할 수 있다', async ({ page }) => {
    const id = await createOrderViaApi(page);
    await advanceViaApi(page, id, ['IN_PROGRESS']);
    await page.goto(`/orders/${id}`);
    await expect(page.locator('h1 .badge')).toHaveText('진행');

    await page.locator('#customerForm input[name="customerName"]').fill('진행 중 고객사');
    await page.getByRole('button', { name: '고객사 저장' }).click();

    await expect(page.locator('.alert.ok')).toHaveText('고객사 이름을 저장했습니다');
    await expect(metaValue(page, '고객사')).toHaveText('진행 중 고객사');
    // 상태 변경 버튼은 새 version 으로 계속 동작한다
    await expect(page.getByRole('button', { name: '완료(으)로 변경' })).toBeVisible();
  });

  test('CUS-05 완료 상태 상세에는 수정 입력과 저장 버튼이 없다', async ({ page }) => {
    const id = await createOrderViaApi(page, { customerName: '완료 고객사' });
    await advanceViaApi(page, id, ['IN_PROGRESS', 'COMPLETED']);
    await page.goto(`/orders/${id}`);

    await expect(page.locator('h1 .badge')).toHaveText('완료');
    await expect(metaValue(page, '고객사')).toHaveText('완료 고객사');
    await expect(page.locator('#customerForm')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '고객사 저장' })).toHaveCount(0);
  });

  test('CUS-06 다른 세션이 먼저 수정하면 충돌 안내가 나오고 최신 값으로 다시 불러온다', async ({ page, browser, baseURL }) => {
    const id = await createOrderViaApi(page, { customerName: '원래 고객사' });
    await page.goto(`/orders/${id}`);
    await expect(page.locator('#customerForm')).toBeVisible();

    // 두 번째 세션이 먼저 수정한다
    const other = await newLoggedInPage(browser, baseURL);
    await other.page.goto(`/orders/${id}`);
    await other.page.locator('#customerForm input[name="customerName"]').fill('다른 사람이 수정');
    await other.page.getByRole('button', { name: '고객사 저장' }).click();
    await expect(other.page.locator('.alert.ok')).toHaveText('고객사 이름을 저장했습니다');
    await other.context.close();

    // 첫 번째 세션은 옛 version 으로 저장을 시도한다
    await page.locator('#customerForm input[name="customerName"]').fill('내가 수정');
    await page.getByRole('button', { name: '고객사 저장' }).click();

    await expect(page.locator('.alert.error')).toContainText('다른 사용자가 먼저 이 주문을 변경했습니다');
    await expect(metaValue(page, '고객사')).toHaveText('다른 사람이 수정');
    await expect(page.locator('#customerForm input[name="customerName"]')).toHaveValue('다른 사람이 수정');
  });

  test('CUS-07 HTML 문자열 고객사는 목록과 상세에서 텍스트로만 보이고 실행되지 않는다', async ({ page }) => {
    const id = await createOrderViaApi(page, { customerName: XSS });

    await page.goto('/orders');
    await expect(customerCell(page, id)).toHaveText(XSS);
    await expect(customerCell(page, id).locator('img')).toHaveCount(0);
    await expect(page.locator('tbody img')).toHaveCount(0);
    expect(await page.evaluate(() => (window as any).__xss)).toBeUndefined();

    await page.goto(`/orders/${id}`);
    await expect(metaValue(page, '고객사')).toHaveText(XSS);
    await expect(page.locator('dl.meta img')).toHaveCount(0);
    await expect(page.locator('#customerForm input[name="customerName"]')).toHaveValue(XSS);
    await expect(page.locator('main img[src="x"]')).toHaveCount(0);
    expect(await page.evaluate(() => (window as any).__xss)).toBeUndefined();
  });

  test('CUS-08 목록 머리글에 고객사가 있고 빈 행·오류 행 colspan 이 9 이다', async ({ page }) => {
    const id = await createOrderViaApi(page); // 고객사 없는 주문
    await page.goto('/orders');
    await expect(page.locator('table.grid th').last()).toHaveText('고객사');
    await expect(page.locator('table.grid th')).toHaveCount(9);
    await expect(customerCell(page, id)).toHaveText('-');

    // 빈 목록
    await page.route('**/api/orders', (route) =>
      route.fulfill({
        json: { orders: [], counts: { CONFIRMED: 0, IN_PROGRESS: 0, COMPLETED: 0, CANCELED: 0 }, total: 0, statuses: [] },
      }),
    );
    await page.goto('/orders');
    await expect(page.locator('td.empty')).toHaveText('주문이 없습니다.');
    await expect(page.locator('td.empty')).toHaveAttribute('colspan', '9');

    // 오류 행
    await page.unroute('**/api/orders');
    await page.route('**/api/orders', (route) => route.fulfill({ status: 500, json: { error: '목록 오류' } }));
    await page.goto('/orders');
    await expect(page.locator('td.empty')).toHaveText('목록 오류');
    await expect(page.locator('td.empty')).toHaveAttribute('colspan', '9');
  });
});
