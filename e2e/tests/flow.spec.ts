import { test, expect, type Page } from '@playwright/test';

/**
 * 종합 흐름 (FLOW-01 ~ FLOW-02)
 * 시나리오 출처: docs/e2e/e2e-test-scenarios.xlsx '시나리오' 시트
 *
 * 전제: 실행 중인 서버를 사용한다. 여러 화면을 이어서 사용자의 실제 사용 순서대로 확인한다.
 * DB 변경: FLOW-01 은 주문 1건을 만들고 상태를 완료까지 바꾼다.
 *          FLOW-02 는 주문 1건을 만들고 배 척수를 바꿨다가 원래 값으로 되돌린다(기준 정보 변경 번호는 되돌릴 수 없다).
 */

const ADMIN = { username: 'admin', password: 'admin' };

// ---------------------------------------------------------------- 도우미

/** 화면으로 로그인한다. 끝나면 주문 목록(/orders)에 있다. */
async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('아이디').fill(ADMIN.username);
  await page.getByLabel('비밀번호').fill(ADMIN.password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/orders$/);
}

/** 주문 상세의 '상태 이력' 표 본문 행 */
function historyRows(page: Page) {
  return page.locator('section.card', { has: page.getByRole('heading', { name: '상태 이력' }) }).locator('tbody tr');
}

/** 마스터 데이터 화면에서 배 척수를 저장하고 성공 메시지를 확인한다 */
async function saveShipCount(page: Page, count: string) {
  await page.goto('/master');
  await page.locator('input[name="shipCount"]').fill(count);
  await page.getByRole('button', { name: '저장' }).click();
  await expect(page.locator('.alert.ok')).toHaveText('마스터 데이터를 저장했습니다');
}

// ---------------------------------------------------------------- 테스트

test.describe('종합 흐름', () => {
  test('FLOW-01 해피패스: 로그인부터 완료·로그아웃까지', async ({ page }) => {
    page.on('dialog', (dialog) => dialog.accept()); // 상태 변경 때마다 뜨는 확인 창을 자동 수락
    const nav = page.locator('header nav');

    // 1. 로그인
    await login(page);

    // 2. 주문 추가 → 시뮬레이션 → '가장 빠르게' 선택 → 메모 → 확정
    await nav.getByRole('link', { name: '주문 추가' }).click();
    await expect(page).toHaveURL(/\/orders\/new$/);
    await page.locator('#runBtn').click();
    await expect(page.locator('#result')).toBeVisible();
    await page.locator('.option[data-type="FAST"]').click();
    await expect(page.locator('.option[data-type="FAST"]')).toHaveClass(/selected/);
    await page.locator('#memo').fill('FLOW-01');
    await page.locator('#confirmBtn').click();

    // 3. 상세 화면: 상태 '확정', 선택한 경로·메모·작성자, 이력 1건
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const orderId = page.url().match(/\/orders\/(\d+)$/)![1];
    await expect(page.locator('h1')).toContainText(`#${orderId}`);
    await expect(page.locator('h1 .badge')).toHaveText('확정');
    await expect(page.locator('dl.meta')).toContainText('가장 빠르게');
    await expect(page.locator('dl.meta')).toContainText('FLOW-01');
    await expect(page.locator('dl.meta')).toContainText('admin');
    await expect(historyRows(page)).toHaveCount(1);

    // 4. 확정 → 진행
    await page.getByRole('button', { name: '진행(으)로 변경' }).click();
    await expect(page.locator('.alert.ok')).toHaveText("상태를 '진행'(으)로 바꿨습니다");
    await expect(page.locator('h1 .badge')).toHaveText('진행');

    // 5. 진행 → 완료 (최종 상태)
    await page.getByRole('button', { name: '완료(으)로 변경' }).click();
    await expect(page.locator('.alert.ok')).toHaveText("상태를 '완료'(으)로 바꿨습니다");
    await expect(page.locator('h1 .badge')).toHaveText('완료');
    await expect(page.getByText('최종 상태라 더 바꿀 수 없습니다.')).toBeVisible();
    await expect(historyRows(page)).toHaveCount(3);

    // 6. 목록의 '완료' 필터에 이 주문이 보인다
    await nav.getByRole('link', { name: '주문 목록' }).click();
    await page.locator('.filters .chip').filter({ hasText: '완료' }).click();
    await expect(page).toHaveURL(/\?status=COMPLETED$/);
    await expect(page.getByRole('link', { name: `#${orderId}`, exact: true })).toBeVisible();

    // 7. 로그아웃 후에는 보호된 화면에 접근할 수 없다
    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('FLOW-02 마스터 변경 → 시뮬레이션 → 확정 → 원복 → 기존 주문 불변', async ({ page }) => {
    await login(page);

    // 원복할 값을 먼저 기억한다 (기본은 3척이지만 다른 값일 수 있으므로 하드코딩하지 않는다)
    await page.goto('/master');
    const originalShips = await page.locator('input[name="shipCount"]').inputValue();

    let orderId = '';
    let summaryBefore: string[] = [];
    try {
      // 1. 배 척수를 2척으로 저장
      await saveShipCount(page, '2');

      // 2. 시뮬레이션: 모든 경로가 배 2척 이하로 계산된다
      await page.goto('/orders/new');
      await page.locator('#runBtn').click();
      await expect(page.locator('#result')).toBeVisible();
      const cards = page.locator('#options .option');
      await expect(cards).toHaveCount(3);
      for (let i = 0; i < 3; i++) {
        await expect(cards.nth(i)).toContainText(/배 [12]척/);
      }

      // 3. 확정하고, 확정된 주문의 요약(소요 일수·비용)을 기록한다
      await page.locator('#confirmBtn').click();
      await expect(page).toHaveURL(/\/orders\/\d+$/);
      orderId = page.url().match(/\/orders\/(\d+)$/)![1];
      // 요약의 값(총 수량·소요 일수·생산 원가·운항비·총 비용)만 목록으로 읽는다. 읽는 방식을 같게 해야 비교가 맞는다.
      summaryBefore = await page.locator('#summary .stats b').allTextContents();
    } finally {
      // 4. 테스트가 중간에 실패해도 기준 정보는 반드시 원래대로 돌려놓는다
      await saveShipCount(page, originalShips);
    }

    // 5. 원복 후에도 이미 확정한 주문의 계획은 그대로다
    await page.goto(`/orders/${orderId}`);
    await expect(page.locator('#summary .stats b')).toHaveText(summaryBefore);
  });
});
