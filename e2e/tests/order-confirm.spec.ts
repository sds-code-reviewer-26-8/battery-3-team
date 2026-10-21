import { test, expect } from '@playwright/test';
import {
  chipCounts,
  currentShipCount,
  historyRows,
  login,
  metaValue,
  newLoggedInPage,
  runSimulation,
  setShipCount,
} from './helpers';

// 담당: 주문 확정 (CNF-01 ~ CNF-08). 시나리오 문서: docs/3team_e2e-test-scenarios.xlsx
test.describe('주문 확정', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('CNF-01 메모 없이 주문 확정', async ({ page }) => {
    await runSimulation(page);
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const id = page.url().match(/\/orders\/(\d+)$/)![1];
    await expect(page.locator('h1')).toContainText(`#${id}`);
    await expect(page.locator('h1 .badge')).toHaveText('확정');
    await expect(metaValue(page, '작성자')).toHaveText('admin');
    await expect(metaValue(page, '메모')).toHaveText('-');

    const rows = historyRows(page);
    await expect(rows).toHaveCount(1);
    const cells = await rows.first().locator('td').allTextContents();
    expect(cells.slice(1)).toEqual(['-', '확정', 'admin']); // [일시, 이전 상태, 새 상태, 변경한 사람]
  });

  test('CNF-02 메모 입력 후 확정', async ({ page }) => {
    await runSimulation(page);
    await page.locator('#memo').fill('테스트 메모');
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(metaValue(page, '메모')).toHaveText('테스트 메모');
  });

  test('CNF-03 선택한 경로(가장 빠르게)로 확정', async ({ page }) => {
    await runSimulation(page);
    const card = page.locator('#options .option[data-type="FAST"]');
    const cardText = await card.innerText();
    const days = cardText.match(/소요\s*(\d+)일/)![1];
    const cost = cardText.match(/총 비용\s*([\d.]+)억원/)![1];

    await card.click();
    await expect(card).toHaveClass(/selected/);
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(metaValue(page, '경로')).toHaveText('가장 빠르게');
    const stat = (label: string) =>
      page.locator('#summary .stats > div').filter({ hasText: label }).locator('b');
    await expect(stat('소요 일수')).toHaveText(`${days}일`);
    await expect(stat('총 비용')).toHaveText(`${cost}억원`);
  });

  test('CNF-04 시뮬레이션 전에는 확정 영역이 보이지 않음', async ({ page }) => {
    await page.goto('/orders/new');
    await expect(page.locator('#result')).toBeHidden();
    await expect(page.locator('#confirmBtn')).toBeHidden();
  });

  test('CNF-05 메모는 1000자까지만 입력된다', async ({ page }) => {
    await runSimulation(page);
    await expect(page.locator('#memo')).toHaveAttribute('maxlength', '1000');
    await page.locator('#memo').focus();
    await page.keyboard.insertText('가'.repeat(1001));
    await expect(page.locator('#memo')).toHaveJSProperty('value', '가'.repeat(1000));
  });

  test('CNF-06 시뮬레이션 후 마스터가 바뀌면 확정이 거부된다(그 뒤 재시뮬레이션하면 확정됨)', async ({
    page,
    browser,
    baseURL,
  }) => {
    const original = await currentShipCount(page);
    const { context, page: other } = await newLoggedInPage(browser, baseURL);
    try {
      const before = await chipCounts(other);
      await runSimulation(page); // 사용자 A: 시뮬레이션만 해 둔다

      await setShipCount(other, original > 1 ? original - 1 : original + 1); // 사용자 B: 마스터 변경

      await page.locator('#confirmBtn').click(); // A: 옛 시뮬레이션으로 확정 시도
      await expect(page.locator('#confirmError')).toContainText(
        '시뮬레이션 이후 마스터 데이터가 바뀌었습니다. 다시 시뮬레이션해 주세요',
      );
      await expect(page).toHaveURL(/\/orders\/new$/);
      expect(await chipCounts(other), '거부된 확정으로 주문이 생기면 안 된다').toEqual(before);

      // 다시 시뮬레이션하면 확정된다
      await page.locator('#runBtn').click();
      await expect(page.locator('#confirmError')).toBeHidden();
      await page.locator('#confirmBtn').click();
      await expect(page).toHaveURL(/\/orders\/\d+$/);
    } finally {
      await setShipCount(other, original); // 마스터 값 원복(버전 번호는 되돌릴 수 없다)
      await context.close();
    }
  });

  test('CNF-07 확정한 주문이 목록 맨 위에 나타나고 건수가 늘어난다', async ({ page }) => {
    const before = await chipCounts(page);
    await runSimulation(page);
    await page.locator('#confirmBtn').click();
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const id = page.url().match(/\/orders\/(\d+)$/)![1];

    const after = await chipCounts(page);
    expect(after[0]).toBe(before[0] + 1); // 전체
    expect(after[1]).toBe(before[1] + 1); // 확정
    await expect(page.locator('tbody tr').first().locator('td').first()).toHaveText(`#${id}`);
  });

  test('CNF-08 확정 버튼을 더블 클릭해도 주문은 1건만 생성된다', async ({ page }) => {
    const before = await chipCounts(page);
    await runSimulation(page);
    await page.locator('#confirmBtn').dblclick();
    await expect(page).toHaveURL(/\/orders\/\d+$/);

    const after = await chipCounts(page);
    expect(after[0]).toBe(before[0] + 1);
    expect(after[1]).toBe(before[1] + 1);
  });
});
