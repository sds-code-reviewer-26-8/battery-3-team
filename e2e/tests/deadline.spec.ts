import { expect, test, type Page } from '@playwright/test';
import { DEFAULT_ORDERS, login, metaValue, runSimulation } from './helpers';

/**
 * 필요 납기일 (DLN-01 ~ DLN-06)
 *
 * 주문 추가 화면에서 '가능한 빨리'(기본) 또는 5~50일을 고른다. 소요 일수가 납기일을 넘는 경로 카드는 빨간색(.late)으로
 * 표시되고, 납기를 넘어도 확정은 할 수 있다. 주문 상세는 선택한 납기일과 초과 여부를 보여 준다.
 * DB 변경: DLN-01, 03, 04(일부) 가 주문을 만든다(취소하지 않는다). 나머지는 DB 를 바꾸지 않는다.
 */

const TYPES = ['COST', 'FAST', 'NEAR'] as const;

/** 경로 카드별 소요 일수 */
async function optionDays(page: Page): Promise<Record<string, number>> {
  const days: Record<string, number> = {};
  for (const type of TYPES) {
    const text = await page.locator(`#options .option[data-type="${type}"]`).innerText();
    days[type] = Number(text.match(/소요\s*(\d+)일/)![1]);
  }
  return days;
}

async function setDeadline(page: Page, value: string) {
  await page.locator('#asap').uncheck();
  await page.locator('#requiredDays').fill(value);
}

test.describe('필요 납기일', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await runSimulation(page);
  });

  test('DLN-01 기본은 가능한 빨리이고 빨간 경로가 없으며 확정하면 상세에 그대로 보인다', async ({ page }) => {
    await expect(page.locator('#asap')).toBeChecked();
    await expect(page.locator('#requiredDays')).toBeDisabled();
    await expect(page.locator('#options .option.late')).toHaveCount(0);

    await page.locator('#confirmBtn').click();
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(metaValue(page, '필요 납기일')).toHaveText('가능한 빨리');
    await expect(metaValue(page, '필요 납기일')).not.toHaveClass(/late/);
  });

  test('DLN-02 체크를 풀면 5~50일 입력이 켜지고 다시 체크하면 꺼진다', async ({ page }) => {
    await page.locator('#asap').uncheck();
    const input = page.locator('#requiredDays');
    await expect(input).toBeEnabled();
    await expect(input).toHaveAttribute('min', '5');
    await expect(input).toHaveAttribute('max', '50');

    await page.locator('#asap').check();
    await expect(input).toBeDisabled();
  });

  test('DLN-03 소요 일수가 납기일을 넘는 경로만 빨간색이고 일수를 바꾸면 바로 바뀐다', async ({ page }) => {
    const days = await optionDays(page);
    const fastest = Math.min(...Object.values(days));
    const slowest = Math.max(...Object.values(days));

    for (const limit of [5, fastest, slowest, 50]) {
      await setDeadline(page, String(limit));
      for (const type of TYPES) {
        const card = page.locator(`#options .option[data-type="${type}"]`);
        if (days[type] > limit) {
          await expect(card, `${type}(${days[type]}일) > ${limit}일`).toHaveClass(/late/);
          await expect(card.locator('.late-note')).toHaveText(`납기 ${days[type] - limit}일 초과`);
        } else {
          await expect(card, `${type}(${days[type]}일) <= ${limit}일`).not.toHaveClass(/late/);
          await expect(card.locator('.late-note')).toHaveCount(0);
        }
      }
    }

    // 빨간 카드는 실제로 빨간 글자색이다
    await setDeadline(page, '5');
    const color = await page.locator('#options .option.late strong').first().evaluate((el) => getComputedStyle(el).color);
    expect(color).toBe('rgb(209, 52, 47)');

    // 가능한 빨리로 되돌리면 모두 풀린다
    await page.locator('#asap').check();
    await expect(page.locator('#options .option.late')).toHaveCount(0);
  });

  test('DLN-04 납기를 넘는 경로도 확정되고 주문 상세에 초과가 빨간색으로 나온다', async ({ page }) => {
    await setDeadline(page, '5');
    await page.locator('#options .option[data-type="COST"]').click();
    const days = (await optionDays(page)).COST;
    expect(days, '이 시나리오는 비용 최소 경로가 5일보다 오래 걸린다고 전제한다').toBeGreaterThan(5);

    await page.locator('#confirmBtn').click();
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const cell = metaValue(page, '필요 납기일');
    await expect(cell).toHaveText(`5일 (소요 ${days}일, 납기 ${days - 5}일 초과)`);
    await expect(cell).toHaveClass(/late/);
  });

  test('DLN-05 납기를 넘지 않으면 상세에 일수만 나오고 빨간색이 아니다', async ({ page }) => {
    await setDeadline(page, '50');
    await page.locator('#confirmBtn').click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(metaValue(page, '필요 납기일')).toHaveText('50일');
    await expect(metaValue(page, '필요 납기일')).not.toHaveClass(/late/);
  });

  test('DLN-06 범위를 벗어나거나 정수가 아닌 납기일은 화면과 API 모두 거부한다', async ({ page }) => {
    for (const bad of ['4', '51', '']) {
      await setDeadline(page, bad);
      await expect(page.locator('#options .option.late'), `'${bad}' 이면 빨간 표시를 하지 않는다`).toHaveCount(0);
      await page.locator('#confirmBtn').click();
      await expect(page.locator('#confirmError')).toHaveText('필요 납기일은 5~50일 사이의 정수로 입력하세요');
      await expect(page).toHaveURL(/\/orders\/new$/);
    }

    const master = await (await page.request.get('/api/master')).json();
    const post = (requiredDays: unknown) =>
      page.request.post('/api/orders', {
        data: { orders: DEFAULT_ORDERS, routeType: 'COST', masterVersion: master.version, requiredDays },
      });
    for (const bad of [4, 51]) {
      const res = await post(bad);
      expect(res.status()).toBe(400);
      expect((await res.json()).error).toBe('필요 납기일은 5~50일 사이로 입력하세요');
    }
    // 소수·문자열을 조용히 정수로 바꿔 받아들이지 않는다(화면은 막지만 서버도 막는다)
    for (const bad of [5.5, '10', 'abc']) {
      const res = await post(bad);
      expect(res.status(), `requiredDays=${JSON.stringify(bad)}`).toBe(400);
      expect((await res.json()).error).toBe('필요 납기일은 정수로 입력하세요');
    }
  });
});
