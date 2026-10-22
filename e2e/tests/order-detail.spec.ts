import { test, expect } from '@playwright/test';
import {
  changeStatus,
  chipCounts,
  createOrderViaApi,
  historyRows,
  login,
  metaValue,
  newLoggedInPage,
} from './helpers';

// 담당: 주문 상세·상태 (DTL-01 ~ DTL-09). 시나리오 문서: docs/3team_e2e-test-scenarios.xlsx
// 사전조건의 주문은 화면이 아니라 API 로 만들어 시나리오마다 독립적으로 실행되게 한다.
test.describe('주문 상세·상태', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('DTL-01 상세 화면 정보와 저장된 계획 표시', async ({ page }) => {
    const id = await createOrderViaApi(page, { memo: '상세 확인용 메모' });
    await page.goto(`/orders/${id}`);

    await expect(page.locator('h1')).toContainText(`#${id}`);
    await expect(page.locator('h1 .badge')).toHaveText('확정');
    await expect(metaValue(page, '확정 일시')).toHaveText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
    await expect(metaValue(page, '작성자')).toHaveText('admin');
    await expect(metaValue(page, '경로')).toHaveText('운항비 최소');
    await expect(metaValue(page, '메모')).toHaveText('상세 확인용 메모');

    await expect(page.locator('#summary .stats')).toContainText('12,000K'); // 총 수량
    expect(await page.locator('#details table').count()).toBeGreaterThanOrEqual(4);
    await expect(page.locator('#anim .anim-map')).toBeVisible();
  });

  test('DTL-02 확정 → 진행 변경', async ({ page }) => {
    const id = await createOrderViaApi(page);
    await page.goto(`/orders/${id}`);

    const message = await changeStatus(page, '진행(으)로 변경');
    expect(message).toBe('진행(으)로 바꿉니다. 계속할까요?');

    await expect(page.locator('.alert.ok')).toHaveText("상태를 '진행'(으)로 바꿨습니다");
    await expect(page.locator('h1 .badge')).toHaveText('진행');
    const rows = historyRows(page);
    await expect(rows).toHaveCount(2);
    const last = await rows.nth(1).locator('td').allTextContents();
    expect(last.slice(1)).toEqual(['확정', '진행', 'admin']);
    await expect(page.getByRole('button', { name: '완료(으)로 변경' })).toBeVisible();
    await expect(page.getByRole('button', { name: '취소(으)로 변경' })).toBeVisible();
  });

  test('DTL-03 진행 → 완료 후 최종 상태', async ({ page }) => {
    const id = await createOrderViaApi(page);
    await page.goto(`/orders/${id}`);
    await changeStatus(page, '진행(으)로 변경');
    await expect(page.locator('h1 .badge')).toHaveText('진행');

    await changeStatus(page, '완료(으)로 변경');
    await expect(page.locator('h1 .badge')).toHaveText('완료');
    await expect(page.locator('p.muted')).toContainText('최종 상태라 더 바꿀 수 없습니다.');
    await expect(page.locator('.status-form')).toHaveCount(0);
  });

  test('DTL-04 확인 대화상자를 취소하면 상태가 유지된다', async ({ page }) => {
    const id = await createOrderViaApi(page);
    await page.goto(`/orders/${id}`);

    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('button', { name: '진행(으)로 변경' }).click();

    await expect(page.locator('h1 .badge')).toHaveText('확정');
    await expect(historyRows(page)).toHaveCount(1);
    await expect(page.locator('.alert.ok')).toHaveCount(0);
  });

  test('DTL-05 확정 주문 취소 직후에도 오류 화면이 나오지 않는다', async ({ page }) => {
    // 예전 결함: 취소는 주문을 삭제한 뒤 삭제된 주문의 상세로 이동해 HTTP 500 이 났다.
    // 이제 취소하면 삭제 결과를 받고 같은 화면에 안내를 보여 주므로, 취소 요청의 응답 코드를 확인한다.
    const id = await createOrderViaApi(page);
    await page.goto(`/orders/${id}`);

    const redirected = page.waitForResponse(
      (r) => new URL(r.url()).pathname === `/api/orders/${id}/status` && r.request().method() === 'POST',
    );
    await changeStatus(page, '취소(으)로 변경');
    const response = await redirected;

    expect(response.status(), '취소 직후 화면이 서버 오류(5xx)면 안 된다').toBeLessThan(500);
  });

  test('DTL-06 취소된 주문은 목록에서 사라지고 취소 칩 건수는 0 이다', async ({ page }) => {
    const id = await createOrderViaApi(page);
    const before = await chipCounts(page); // 방금 만든 주문이 포함된 건수
    await page.goto(`/orders/${id}`);
    await changeStatus(page, '취소(으)로 변경');

    // 취소 직후 화면과 상관없이 목록 상태만 확인한다
    const after = await chipCounts(page);
    expect(after[0]).toBe(before[0] - 1); // 전체
    expect(after[1]).toBe(before[1] - 1); // 확정
    expect(after[4], "'취소' 칩 건수는 계속 0 (주문 행이 삭제되므로)").toBe(0);
    await expect(page.locator('tbody a', { hasText: new RegExp(`^#${id}$`) })).toHaveCount(0);
  });

  test('DTL-07 다른 사용자가 먼저 바꾸면 옛 화면의 변경은 충돌로 거부된다', async ({
    page,
    browser,
    baseURL,
  }) => {
    const id = await createOrderViaApi(page);
    const { context, page: other } = await newLoggedInPage(browser, baseURL);
    try {
      await page.goto(`/orders/${id}`); // 사용자 A
      await other.goto(`/orders/${id}`); // 사용자 B (이 화면은 곧 옛 화면이 된다)

      await changeStatus(page, '진행(으)로 변경'); // A 가 먼저 '진행' 으로 변경
      await expect(page.locator('h1 .badge')).toHaveText('진행');

      await changeStatus(other, '취소(으)로 변경'); // B(옛 화면)가 '취소' 시도
      await expect(other.locator('.alert.error')).toContainText('다른 사용자가 먼저 이 주문을 변경했습니다');
      await expect(other.locator('h1 .badge')).toHaveText('진행'); // 취소되지 않고 A 의 변경이 유지
    } finally {
      await context.close();
    }
  });

  test('DTL-08 존재하지 않는 주문을 열면 안내가 나온다(404 또는 안내 문구)', async ({ page }) => {
    // 예전 결함: 없는 주문 번호를 열면 HTTP 500 이 났다. 이제 화면은 열리고 안내 문구가 나온다(API 는 404).
    const response = await page.goto('/orders/999999');
    expect(response!.status(), '존재하지 않는 주문이 서버 오류(5xx)면 안 된다').toBeLessThan(500);
  });

  test('DTL-09 상태 이력 표(확정 → 진행 → 완료)', async ({ page }) => {
    const id = await createOrderViaApi(page);
    await page.goto(`/orders/${id}`);
    await changeStatus(page, '진행(으)로 변경');
    await expect(page.locator('h1 .badge')).toHaveText('진행');
    await changeStatus(page, '완료(으)로 변경');
    await expect(page.locator('h1 .badge')).toHaveText('완료');

    const rows = historyRows(page);
    await expect(rows).toHaveCount(3);
    const table = [];
    for (let i = 0; i < 3; i++) table.push(await rows.nth(i).locator('td').allTextContents());
    expect(table.map((r) => r.slice(1))).toEqual([
      ['-', '확정', 'admin'],
      ['확정', '진행', 'admin'],
      ['진행', '완료', 'admin'],
    ]);
    const times = table.map((r) => r[0]);
    expect([...times].sort()).toEqual(times); // 시각 오름차순
  });
});
