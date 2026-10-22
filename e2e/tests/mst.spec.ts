import { APIRequestContext, expect, test } from '@playwright/test';
import {
  COUNTRIES,
  ROUTE_KEYS,
  SAVED_MESSAGE,
  SAVE_REFUSED_MESSAGE,
  createOrder,
  defaultMasterForm,
  disableBrowserValidation,
  fillMaster,
  gotoMaster,
  login,
  newApi,
  restoreMaster,
  saveMaster,
} from './hg.helpers';

/**
 * 마스터 데이터 (MST-01 ~ MST-10)
 * 기준: docs/e2e/e2e-test-scenarios.xlsx
 *
 * 마스터를 바꾸는 테스트가 많아서 직렬로 실행하고, 매 테스트가 끝나면 값을 기본값으로 원복한다.
 * (masterVersion 은 올라가면 되돌릴 수 없다. 값만 원복한다.)
 */
test.describe.configure({ mode: 'serial' });

let api: APIRequestContext;

test.beforeAll(async () => {
  api = await newApi(test.info().project.use.baseURL!);
});

test.afterAll(async () => {
  await api.dispose();
});

test.beforeEach(async ({ page }) => {
  await login(page);
});

test.afterEach(async () => {
  await restoreMaster(api);
});

test.describe('마스터 데이터', () => {
  test('MST-01 마스터 화면에 기본값이 채워져 있다', async ({ page }) => {
    // 이 테스트는 서버가 처음 뜬 기본 상태를 전제로 한다.
    await gotoMaster(page);

    const defaults = defaultMasterForm();
    for (const [name, value] of Object.entries(defaults)) {
      await expect(page.locator(`[name="${name}"]`), name).toHaveValue(value);
    }
    // 공장 4개 × 3칸 + 배 2칸 + 항로 10칸
    expect(Object.keys(defaults)).toHaveLength(COUNTRIES.length * 3 + 2 + ROUTE_KEYS.length);
  });

  test('MST-02 값 수정 후 저장하면 유지된다', async ({ page }) => {
    await gotoMaster(page);
    await fillMaster(page, { maxDaily_CHINA: '500' });
    await saveMaster(page);

    await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);

    await gotoMaster(page); // 새로 GET (저장 직후 화면을 새로고침 대신 다시 연다)
    await expect(page.locator('[name=maxDaily_CHINA]')).toHaveValue('500');
  });

  test('MST-03 0 입력 시 저장이 거부되고 DB 는 변하지 않는다', async ({ page }) => {
    await gotoMaster(page);
    await disableBrowserValidation(page);
    await fillMaster(page, { stockLimit_CHINA: '0' });
    await saveMaster(page);

    const error = page.locator('.alert.error');
    await expect(error).toContainText(SAVE_REFUSED_MESSAGE);
    await expect(error.locator('li')).toContainText(['중국 재고 상한은(는) 1 이상 1000000 이하의 정수여야 합니다']);
    // 입력한 값은 화면에 유지된다
    await expect(page.locator('[name=stockLimit_CHINA]')).toHaveValue('0');

    // 저장되지 않았다
    await gotoMaster(page);
    await expect(page.locator('[name=stockLimit_CHINA]')).toHaveValue('4000');
  });

  test('MST-04 여러 오류를 한 번에 목록으로 보여 준다', async ({ page }) => {
    await gotoMaster(page);
    await disableBrowserValidation(page);
    await fillMaster(page, {
      shipCount: '0',
      stockLimit_CHINA: '',
      days_KOREA_CHINA: '366',
    });
    await saveMaster(page);

    const errors = page.locator('.alert.error li');
    await expect(page.locator('.alert.error')).toContainText(SAVE_REFUSED_MESSAGE);
    await expect(errors).toHaveCount(3);
    await expect(errors).toContainText([
      '배 척수은(는) 1 이상 4 이하의 정수여야 합니다',
      '중국 재고 상한은(는) 정수로 입력하세요',
      '한국-중국 항해 일수은(는) 1 이상 365 이하의 정수여야 합니다',
    ]);
  });

  test('MST-05 배 척수 경계: 1, 4 는 저장되고 0, 5 는 거부된다', async ({ page }) => {
    for (const ok of ['1', '4']) {
      await gotoMaster(page);
      await fillMaster(page, { shipCount: ok });
      await saveMaster(page);
      await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);
      await gotoMaster(page);
      await expect(page.locator('[name=shipCount]')).toHaveValue(ok);
    }

    for (const bad of ['0', '5']) {
      await gotoMaster(page);
      await disableBrowserValidation(page);
      await fillMaster(page, { shipCount: bad });
      await saveMaster(page);
      await expect(page.locator('.alert.error li')).toContainText(['배 척수은(는) 1 이상 4 이하의 정수여야 합니다']);
      // 거부된 값은 저장되지 않아 마지막으로 저장된 4 가 남아 있다
      await gotoMaster(page);
      await expect(page.locator('[name=shipCount]')).toHaveValue('4');
    }
  });

  test('MST-06 항로 일수 경계: 365 는 저장되고 366 은 거부된다', async ({ page }) => {
    await gotoMaster(page);
    await fillMaster(page, { days_KOREA_CHINA: '365' });
    await saveMaster(page);
    await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);
    await gotoMaster(page);
    await expect(page.locator('[name=days_KOREA_CHINA]')).toHaveValue('365');

    await disableBrowserValidation(page);
    await fillMaster(page, { days_KOREA_CHINA: '366' });
    await saveMaster(page);
    await expect(page.locator('.alert.error li')).toContainText([
      '한국-중국 항해 일수은(는) 1 이상 365 이하의 정수여야 합니다',
    ]);
    await gotoMaster(page);
    await expect(page.locator('[name=days_KOREA_CHINA]')).toHaveValue('365');
  });

  test('MST-07 척당 일 운항비 형식 검증', async ({ page }) => {
    // 운항비 칸은 type=text 라 브라우저 검증에 걸리지 않는다.
    const cases: Array<[string, string]> = [
      ['abc', '척당 일 운항비(억 원)는 숫자로 입력하세요'],
      ['0', '척당 일 운항비는 0보다 커야 합니다'],
      ['0.000000001', '척당 일 운항비는 원 단위로 떨어지는 값이어야 합니다'],
    ];
    for (const [input, message] of cases) {
      await gotoMaster(page);
      await fillMaster(page, { shipDailyCost: input });
      await saveMaster(page);
      await expect(page.locator('.alert.error li'), input).toContainText([message]);
    }

    // 0.5억(= 50,000,000원)은 저장된다. 시나리오 시트는 DB 변경 N 이지만 이 부분은 값을 바꾼다 (afterEach 에서 원복).
    await gotoMaster(page);
    await fillMaster(page, { shipDailyCost: '0.5' });
    await saveMaster(page);
    await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);
    await gotoMaster(page);
    await expect(page.locator('[name=shipDailyCost]')).toHaveValue('0.5');
  });

  test('MST-08 마스터 변경이 이후 시뮬레이션에 반영된다', async ({ page }) => {
    await gotoMaster(page);
    await fillMaster(page, { shipCount: '1' });
    await saveMaster(page);
    await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);

    await page.goto('/orders/new');
    await page.getByRole('button', { name: '시뮬레이션 실행' }).click();

    const options = page.locator('#options .option');
    await expect(options).toHaveCount(3);
    for (let i = 0; i < 3; i++) {
      await expect(options.nth(i)).toContainText('배 1척');
    }
  });

  test('MST-09 마스터 변경이 이미 확정한 주문의 계획에 영향을 주지 않는다', async ({ page }) => {
    const orderId = await createOrder(api, 'MST-09');

    const snapshot = async () => {
      await page.goto(`/orders/${orderId}`);
      await expect(page.locator('#summary .stats')).toBeVisible();
      await expect(page.locator('#details table').first()).toBeVisible();
      return {
        summary: (await page.locator('#summary').textContent()) ?? '',
        details: (await page.locator('#details').textContent()) ?? '',
      };
    };

    const before = await snapshot();
    expect(before.summary).toContain('총 비용');

    // 원가와 항로를 바꾼다
    await gotoMaster(page);
    await fillMaster(page, { unitCost_CHINA: '800', days_KOREA_CHINA: '3' });
    await saveMaster(page);
    await expect(page.locator('.alert.ok')).toContainText(SAVED_MESSAGE);

    const after = await snapshot();
    expect(after.summary).toBe(before.summary);
    expect(after.details).toBe(before.details);
  });

  test('MST-10 [탐색] 두 사람이 같은 마스터 화면에서 차례로 저장한다', async ({ browser }) => {
    // 기대 동작이 아직 정해지지 않은 탐색 시나리오다 (docs/PRD.md 리스크, docs/INTENT.md Q4 참고).
    // 코드상 마스터 폼에는 버전 값이 없어서, 옛 화면으로 저장해도 오류 없이 덮어쓸 가능성이 있다.
    // 그래서 관찰한 결과를 주석(annotation)으로 남기고, 서버 오류(5xx)만 실패로 본다.
    const baseURL = test.info().project.use.baseURL!;
    const ctxA = await browser.newContext({ baseURL });
    const ctxB = await browser.newContext({ baseURL });
    try {
      const a = await ctxA.newPage();
      const b = await ctxB.newPage();
      await login(a);
      await login(b);
      await gotoMaster(a);
      await gotoMaster(b);

      // A 가 먼저 배 척수를 2 로 저장
      await fillMaster(a, { shipCount: '2' });
      await saveMaster(a);
      await expect(a.locator('.alert.ok')).toContainText(SAVED_MESSAGE);

      // B 는 새로 고치지 않은 옛 화면(배 척수 3)에서 운항비만 바꿔 저장
      await fillMaster(b, { shipDailyCost: '2' });
      const [response] = await Promise.all([
        b.waitForResponse((r) => new URL(r.url()).pathname === '/api/master' && r.request().method() === 'PUT'),
        saveMaster(b),
      ]);

      await gotoMaster(b);
      const finalShipCount = await b.locator('[name=shipCount]').inputValue();
      const finalDailyCost = await b.locator('[name=shipDailyCost]').inputValue();
      test.info().annotations.push({
        type: '탐색 결과',
        description:
          `B 저장 응답 ${response.status()}, 최종 배 척수=${finalShipCount}(A 가 2로 저장), 운항비=${finalDailyCost}(B 가 2로 저장). ` +
          (finalShipCount === '2' ? 'A 의 변경이 유지됨' : 'A 의 변경이 B 의 옛 값으로 덮어써짐(결함 의심)'),
      });

      expect(response.status()).toBeLessThan(500);
      await expect(b.locator('[name=shipCount]')).toBeVisible(); // 서버가 정상적으로 응답한다
    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });
});
