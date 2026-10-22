import { test, expect, type Page } from '@playwright/test';

/**
 * 주문 추가·시뮬레이션 (SIM-01 ~ SIM-22)
 * 시나리오 출처: docs/e2e/e2e-test-scenarios.xlsx '시나리오' 시트
 *
 * 전제: 실행 중인 서버의 마스터 데이터가 기본값이다(openNewOrder 에서 확인한다).
 * 이 파일의 테스트는 DB 를 바꾸지 않는다(시뮬레이션만 하고 확정하지 않는다).
 */

const FACTORIES = ['CHINA', 'JAPAN', 'VIETNAM', 'INDONESIA'] as const;
type Factory = (typeof FACTORIES)[number];
const LABEL: Record<Factory, string> = { CHINA: '중국', JAPAN: '일본', VIETNAM: '베트남', INDONESIA: '인도네시아' };
const FROM_LABEL: Record<string, Factory> = Object.fromEntries(FACTORIES.map((c) => [LABEL[c], c]));

const OPTION_TYPES = ['COST', 'FAST', 'NEAR'] as const;
type OptionType = (typeof OPTION_TYPES)[number];
const OPTION_LABEL: Record<OptionType, string> = { COST: '비용 최소', FAST: '가장 빠르게', NEAR: '가까운 곳부터' };

// 기본 마스터 값 (BatteryService.initDefaults)
const DEFAULT_MASTER = {
  shipCount: 3,
  shipDailyCostWon: 100_000_000,
  factories: {
    CHINA: [400, 4000, 700, 100_000_000],
    JAPAN: [800, 2000, 900, 100_000_000],
    VIETNAM: [200, 10000, 550, 100_000_000],
    INDONESIA: [300, 7000, 450, 100_000_000],
  },
};

// ---------------------------------------------------------------- 도우미

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('admin');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/orders$/);
}

async function expectDefaultMaster(page: Page) {
  const res = await page.request.get('/api/master');
  expect(res.ok(), '/api/master 응답').toBeTruthy();
  const m = await res.json();
  const actual = {
    shipCount: m.shipCount,
    shipDailyCostWon: m.shipDailyCostWon,
    factories: Object.fromEntries(
      m.factories.map((f: { country: string; maxDaily: number; stockLimit: number; unitCost: number; operatingCostWon: number }) => [
        f.country,
        [f.maxDaily, f.stockLimit, f.unitCost, f.operatingCostWon],
      ]),
    ),
  };
  expect(actual, '마스터가 기본값이 아니면 기대값이 달라진다').toEqual(DEFAULT_MASTER);
}

/** 로그인 → 기본 마스터 확인 → 주문 추가 화면(방문 횟수 라벨까지 채워진 상태) */
async function openNewOrder(page: Page) {
  await login(page);
  await expectDefaultMaster(page);
  await page.goto('/orders/new');
  // 방문 횟수 라벨은 /api/master 응답 뒤에 채워진다
  await expect(gauge(page, 'CHINA').locator('.gvisits')).toHaveText('방문 1회');
}

const gauge = (page: Page, c: Factory) => page.locator(`.gauge[data-c="${c}"]`);

async function gaugeValues(page: Page): Promise<Record<Factory, number>> {
  const out = {} as Record<Factory, number>;
  for (const c of FACTORIES) {
    out[c] = parseInt(await gauge(page, c).locator('.gnum').inputValue(), 10);
  }
  return out;
}

async function setGauge(page: Page, c: Factory, value: number) {
  const input = gauge(page, c).locator('.gnum');
  await input.fill(String(value));
  await input.dispatchEvent('change');
}

async function setTotal(page: Page, value: number) {
  const input = page.locator('#total');
  await input.fill(String(value));
  await input.dispatchEvent('change');
}

async function expectGauges(page: Page, expected: Record<Factory, number>) {
  for (const c of FACTORIES) {
    await expect(gauge(page, c).locator('.gnum'), `${LABEL[c]} 게이지`).toHaveValue(String(expected[c]));
  }
}

async function runSimulation(page: Page) {
  await page.locator('#runBtn').click();
  await expect(page.locator('#result')).toBeVisible();
  await expect(page.locator('#options .option')).toHaveCount(3);
}

const optionCard = (page: Page, type: OptionType) => page.locator(`#options .option[data-type="${type}"]`);

async function selectOption(page: Page, type: OptionType) {
  await optionCard(page, type).click();
  await expect(optionCard(page, type)).toHaveClass(/selected/);
  await expect(page.locator('#summary h2')).toContainText(OPTION_LABEL[type]);
}

/** 세 경로를 차례로 선택하며 검사한다 */
async function forEachOption(page: Page, fn: (type: OptionType) => Promise<void>) {
  for (const type of OPTION_TYPES) {
    await selectOption(page, type);
    await fn(type);
  }
}

interface Card {
  label: string;
  days: number;
  total: number;
  shipping: number;
  ships: number;
}

async function readCard(page: Page, type: OptionType): Promise<Card> {
  const text = await optionCard(page, type).innerText();
  const pick = (re: RegExp) => {
    const m = text.match(re);
    if (!m) throw new Error(`카드 문구를 읽지 못했습니다: ${re} / ${text}`);
    return m;
  };
  return {
    label: (await optionCard(page, type).locator('strong').innerText()).trim(),
    days: parseInt(pick(/소요\s+(\d+)일/)[1], 10),
    total: parseFloat(pick(/총 비용\s+([\d.]+)억원/)[1]),
    shipping: parseFloat(pick(/운항비\s+([\d.]+)억원/)[1]),
    ships: parseInt(pick(/배\s+(\d+)척/)[1], 10),
  };
}

/** 요약(#summary)의 항목 값. 예: stat(page, '생산 원가') → '78.00억원' */
async function stat(page: Page, label: string): Promise<string> {
  const box = page.locator('#summary .stats > div').filter({ hasText: label });
  return (await box.locator('b').innerText()).trim();
}

interface Stop {
  country: Factory;
  arrive: number;
  load: number;
}
interface Trip {
  ship: number;
  trip: number;
  depart: number;
  stops: Stop[];
  ret: number;
  sail: number;
}

/** '배 경로 (왕복별 일정)' 표를 읽는다 */
async function readTrips(page: Page): Promise<Trip[]> {
  const rows: string[][] = await page.evaluate(() => {
    const h2 = Array.from(document.querySelectorAll('#details h2')).find((e) => (e.textContent || '').includes('배 경로'));
    const table = h2?.nextElementSibling;
    if (!table) return [];
    return Array.from(table.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.children).map((td) => (td.textContent || '').replace(/\s+/g, ' ').trim()),
    );
  });
  const num = (s: string) => parseInt(s.replace(/[^\d]/g, ''), 10);
  return rows.map((c) => ({
    ship: num(c[0]),
    trip: num(c[1]),
    depart: num(c[2]),
    ret: num(c[4]),
    sail: num(c[5]),
    stops: Array.from(c[3].matchAll(/(중국|일본|베트남|인도네시아)\((\d+)일 도착 · ([\d,]+)K 적재\)/g)).map((m) => ({
      country: FROM_LABEL[m[1]],
      arrive: parseInt(m[2], 10),
      load: parseInt(m[3].replace(/,/g, ''), 10),
    })),
  }));
}

/** 나라별 적재량(도착일 순) */
function loadsByCountry(trips: Trip[]): Record<Factory, number[]> {
  const stops = trips.flatMap((t) => t.stops).sort((a, b) => a.arrive - b.arrive);
  const out = { CHINA: [], JAPAN: [], VIETNAM: [], INDONESIA: [] } as Record<Factory, number[]>;
  for (const s of stops) out[s.country].push(s.load);
  return out;
}

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0);

// ---------------------------------------------------------------- 테스트

test.describe('주문 추가·시뮬레이션', () => {
  test('SIM-01 주문 추가 화면 초기 상태', async ({ page }) => {
    await openNewOrder(page);

    await expect(page.locator('#total')).toHaveValue('12000');
    await expectGauges(page, { CHINA: 3000, JAPAN: 3000, VIETNAM: 3000, INDONESIA: 3000 });
    await expect(gauge(page, 'CHINA').locator('.gvisits')).toHaveText('방문 1회');
    await expect(gauge(page, 'JAPAN').locator('.gvisits')).toHaveText('방문 2회');
    await expect(gauge(page, 'VIETNAM').locator('.gvisits')).toHaveText('방문 1회');
    await expect(gauge(page, 'INDONESIA').locator('.gvisits')).toHaveText('방문 1회');
    for (const c of FACTORIES) {
      await expect(gauge(page, c).locator('.gname')).toHaveText(LABEL[c]);
    }
    await expect(page.locator('#result')).toBeHidden();
  });

  test('SIM-02 시뮬레이션 실행 시 경로 3종 표시', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    const labels = await page.locator('#options .option strong').allInnerTexts();
    expect(labels.map((s) => s.trim())).toEqual(['비용 최소', '가장 빠르게', '가까운 곳부터']);
    for (const type of OPTION_TYPES) {
      const card = await readCard(page, type);
      expect(card.days, `${OPTION_LABEL[type]} 소요 일수`).toBeGreaterThan(0);
      expect(card.total, `${OPTION_LABEL[type]} 총 비용`).toBeGreaterThan(0);
    }
    await expect(optionCard(page, 'COST')).toHaveClass(/selected/);
    await expect(optionCard(page, 'FAST')).not.toHaveClass(/selected/);
    await expect(optionCard(page, 'NEAR')).not.toHaveClass(/selected/);
  });

  test('SIM-03 옵션 카드 클릭 시 선택·요약 갱신', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    await optionCard(page, 'FAST').click();

    await expect(optionCard(page, 'FAST')).toHaveClass(/selected/);
    await expect(optionCard(page, 'COST')).not.toHaveClass(/selected/);
    await expect(optionCard(page, 'NEAR')).not.toHaveClass(/selected/);
    await expect(page.locator('#summary h2')).toContainText('가장 빠르게');
    const fast = await readCard(page, 'FAST');
    expect(await stat(page, '소요 일수')).toBe(`${fast.days}일`);
    expect(await stat(page, '총 비용')).toBe(`${fast.total.toFixed(2)}억원`);
    await expect(page.locator('#details table').first()).toBeVisible();
    await expect(page.locator('#anim .anim-stage')).toBeVisible();
  });

  test('SIM-04 FAST 소요 일수 ≤ COST 소요 일수, COST 총비용 ≤ FAST 총비용', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    const cost = await readCard(page, 'COST');
    const fast = await readCard(page, 'FAST');
    expect(fast.days).toBeLessThanOrEqual(cost.days);
    expect(cost.total).toBeLessThanOrEqual(fast.total);
  });

  test('SIM-05 총 수량 변경 시 균등 분배 + 이전 결과 무효화', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    await setTotal(page, 10000);

    await expectGauges(page, { CHINA: 2500, JAPAN: 2500, VIETNAM: 2500, INDONESIA: 2500 });
    await expect(page.locator('#result')).toBeHidden();
  });

  test('SIM-06 게이지 조정 시 오른쪽 이웃이 반대로 조정되고 합계 유지', async ({ page }) => {
    await openNewOrder(page);

    await setGauge(page, 'CHINA', 5000);

    const v = await gaugeValues(page);
    expect(v).toEqual({ CHINA: 5000, JAPAN: 1000, VIETNAM: 3000, INDONESIA: 3000 });
    expect(sum(Object.values(v))).toBe(12000);
    await expect(gauge(page, 'CHINA').locator('.gvisits')).toHaveText('방문 2회');
    await expect(gauge(page, 'JAPAN').locator('.gvisits')).toHaveText('방문 1회');
  });

  test('SIM-07 맨 오른쪽(인도네시아) 조정 시 맨 왼쪽(중국)이 조정됨', async ({ page }) => {
    await openNewOrder(page);

    await setGauge(page, 'INDONESIA', 4000);

    await expectGauges(page, { CHINA: 2000, JAPAN: 3000, VIETNAM: 3000, INDONESIA: 4000 });
    expect(sum(Object.values(await gaugeValues(page)))).toBe(12000);
  });

  test('SIM-08 이웃 합계를 넘는 값은 상한으로 보정', async ({ page }) => {
    await openNewOrder(page);

    await setGauge(page, 'CHINA', 9999);

    await expectGauges(page, { CHINA: 6000, JAPAN: 0, VIETNAM: 3000, INDONESIA: 3000 });
    expect(sum(Object.values(await gaugeValues(page)))).toBe(12000);
  });

  test('SIM-09 수량 변경 시 이전 시뮬레이션 결과 숨김', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);
    await expect(page.locator('#confirmBtn')).toBeVisible();

    await setGauge(page, 'JAPAN', 2500);

    await expect(page.locator('#result')).toBeHidden();
    await expect(page.locator('#confirmBtn')).toBeHidden();
  });

  test('SIM-10 총 수량 0 입력은 이전 값으로 복원', async ({ page }) => {
    await openNewOrder(page);

    await setTotal(page, 0);

    await expect(page.locator('#total')).toHaveValue('12000');
    await expectGauges(page, { CHINA: 3000, JAPAN: 3000, VIETNAM: 3000, INDONESIA: 3000 });
  });

  test('SIM-11 최소 수량 1K 시뮬레이션', async ({ page }) => {
    await openNewOrder(page);

    await setTotal(page, 1);
    await expectGauges(page, { CHINA: 1, JAPAN: 0, VIETNAM: 0, INDONESIA: 0 });
    await runSimulation(page);

    await expect(page.locator('#inputError')).toBeHidden();
    await forEachOption(page, async (type) => {
      const trips = await readTrips(page);
      const countries = new Set(trips.flatMap((t) => t.stops.map((s) => s.country)));
      expect(Array.from(countries), `${OPTION_LABEL[type]} 방문 나라`).toEqual(['CHINA']);
      expect(sum(trips.flatMap((t) => t.stops.map((s) => s.load)))).toBe(1);
    });
  });

  test('SIM-12 최대 수량 100,000K 시뮬레이션', async ({ page }) => {
    test.setTimeout(120_000);
    await openNewOrder(page);

    await setTotal(page, 100000);
    await expectGauges(page, { CHINA: 25000, JAPAN: 25000, VIETNAM: 25000, INDONESIA: 25000 });
    await expect(gauge(page, 'CHINA').locator('.gvisits')).toHaveText('방문 7회');
    await expect(gauge(page, 'JAPAN').locator('.gvisits')).toHaveText('방문 13회');
    await expect(gauge(page, 'VIETNAM').locator('.gvisits')).toHaveText('방문 3회');
    await expect(gauge(page, 'INDONESIA').locator('.gvisits')).toHaveText('방문 4회');

    const started = Date.now();
    await page.locator('#runBtn').click();
    await expect(page.locator('#result')).toBeVisible({ timeout: 60_000 });
    const elapsedMs = Date.now() - started;
    test.info().annotations.push({ type: '응답 시간', description: `${elapsedMs}ms` });

    await expect(page.locator('#inputError')).toBeHidden();
    await expect(page.locator('#options .option')).toHaveCount(3);
    expect(elapsedMs, '제한 시간(60초) 안에 응답').toBeLessThan(60_000);
  });

  test('SIM-13 최대 수량 초과(100,001K) 서버 검증', async ({ page }) => {
    await openNewOrder(page);

    await setTotal(page, 100001);
    await page.locator('#runBtn').click();

    await expect(page.locator('#inputError')).toBeVisible();
    await expect(page.locator('#inputError')).toHaveText('총 생산 필요 수는 100,000K 이하여야 합니다');
    await expect(page.locator('#result')).toBeHidden();
  });

  test('SIM-14 재고 상한을 넘는 주문의 방문 횟수(올림 계산)', async ({ page }) => {
    await openNewOrder(page);

    await setGauge(page, 'CHINA', 6000);
    await expectGauges(page, { CHINA: 6000, JAPAN: 0, VIETNAM: 3000, INDONESIA: 3000 });
    await expect(gauge(page, 'CHINA').locator('.gvisits')).toHaveText('방문 2회');
    await expect(gauge(page, 'JAPAN').locator('.gvisits')).toHaveText('방문 0회');
    await runSimulation(page);

    await expect(page.locator('#inputError')).toBeHidden();
    await forEachOption(page, async (type) => {
      const trips = await readTrips(page);
      const loads = loadsByCountry(trips);
      const chinaTrips = trips.filter((t) => t.stops.some((s) => s.country === 'CHINA'));
      expect(chinaTrips.length, `${OPTION_LABEL[type]} 중국 왕복 수`).toBe(2);
      expect(loads.CHINA, `${OPTION_LABEL[type]} 중국 적재량(재고 상한 4,000K 먼저)`).toEqual([4000, 2000]);
      expect(loads.JAPAN, `${OPTION_LABEL[type]} 일본 방문 없음`).toEqual([]);
    });
  });

  test('SIM-15 상세 표 렌더링(주문·배 일정·일별 재고/생산·마스터)', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    const days = (await readCard(page, 'COST')).days;
    // 나라별 주문 수량 표: 4개 나라
    await expect(page.locator('#details h2', { hasText: '나라별 주문 수량' })).toBeVisible();
    await expect(page.locator('#details table').nth(0).locator('tbody tr')).toHaveCount(4);
    // 배 경로 표: 1행 이상
    expect((await readTrips(page)).length).toBeGreaterThan(0);
    // 일별 표: 소요 일수만큼 행 (펼치기 전이라 보이지는 않지만 존재한다)
    await expect(page.locator('#details table').nth(2).locator('tbody tr')).toHaveCount(days);
    // 마스터 표: 공장 4행, 항로 10행
    await expect(page.locator('#details table').nth(3).locator('tbody tr')).toHaveCount(4);
    await expect(page.locator('#details table').nth(4).locator('tbody tr')).toHaveCount(10);
    for (const heading of ['나라별 주문 수량', '배 경로', '일별 생산·비용', '계획에 쓴 마스터 값']) {
      await expect(page.locator('#details h2', { hasText: heading })).toHaveCount(1);
    }
  });

  test('SIM-16 지도 애니메이션 렌더·재생', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    const days = (await readCard(page, 'COST')).days;
    const shipCount = new Set((await readTrips(page)).map((t) => t.ship)).size;

    await expect(page.locator('#anim .anim-map')).toBeVisible();
    await expect.poll(() => page.locator('#anim .anim-map').evaluate((img: HTMLImageElement) => img.naturalWidth), {
      message: '지도 이미지 로드',
    }).toBeGreaterThan(0);
    await expect(page.locator('#anim .ship')).toHaveCount(shipCount);
    await expect(page.locator('#anim [data-slider]')).toHaveAttribute('max', String(days));

    // 자동 재생 중: 일차 표시가 시간이 지나면 바뀐다
    await expect(page.locator('#anim [data-play]')).toHaveText('일시정지');
    const first = await page.locator('#anim [data-day]').innerText();
    await expect.poll(() => page.locator('#anim [data-day]').innerText(), { timeout: 10_000 }).not.toBe(first);

    // 일시정지 ↔ 재생
    await page.locator('#anim [data-play]').click();
    await expect(page.locator('#anim [data-play]')).toHaveText('재생');
    await page.locator('#anim [data-play]').click();
    await expect(page.locator('#anim [data-play]')).toHaveText(/일시정지|재생/);

    // 비용 애니메이션의 끝 값(마지막 날)은 선택한 계획안의 총 비용(운영비 포함)과 같다
    if ((await page.locator('#anim [data-play]').innerText()).trim() === '일시정지') {
      await page.locator('#anim [data-play]').click();
    }
    await page.locator('#anim [data-slider]').evaluate((el: HTMLInputElement) => {
      el.value = el.max;
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    const total = await stat(page, '총 비용');
    await expect(page.locator('#anim [data-cost]')).toHaveText(total.replace('억원', ''));
  });

  test('SIM-17 시뮬레이션 중 버튼 중복 클릭 방지', async ({ page }) => {
    await openNewOrder(page);
    let requests = 0;
    await page.route('**/api/simulate', async (route) => {
      requests++;
      await new Promise((r) => setTimeout(r, 1000)); // 응답을 늦춰 '요청 중' 상태를 만든다
      await route.continue();
    });

    await page.locator('#runBtn').dblclick();
    await expect(page.locator('#runBtn')).toBeDisabled();

    await expect(page.locator('#result')).toBeVisible();
    await expect(page.locator('#runBtn')).toBeEnabled();
    expect(requests, '연속 클릭해도 요청은 1번').toBe(1);
  });

  test('SIM-18 재고 상한을 넘는 일본 4,500K 단독 주문은 3번에 나눠 수거', async ({ page }) => {
    await openNewOrder(page);

    await setTotal(page, 4500);
    await expectGauges(page, { CHINA: 1125, JAPAN: 1125, VIETNAM: 1125, INDONESIA: 1125 });
    // 게이지 연동 규칙으로 일본에 몰아 준다: 베트남 → 인도네시아 → 중국 순서로 0
    await setGauge(page, 'VIETNAM', 0);
    await setGauge(page, 'INDONESIA', 0);
    await setGauge(page, 'CHINA', 0);
    await expectGauges(page, { CHINA: 0, JAPAN: 4500, VIETNAM: 0, INDONESIA: 0 });
    await expect(gauge(page, 'JAPAN').locator('.gvisits')).toHaveText('방문 3회');
    await runSimulation(page);

    await forEachOption(page, async (type) => {
      const trips = await readTrips(page);
      expect(trips.length, `${OPTION_LABEL[type]} 왕복 수(한국 복귀 후 재출항)`).toBe(3);
      expect(trips.every((t) => t.stops.length === 1 && t.stops[0].country === 'JAPAN')).toBe(true);
      expect(loadsByCountry(trips).JAPAN, `${OPTION_LABEL[type]} 일본 적재량 순서`).toEqual([2000, 2000, 500]);
    });
  });

  test('SIM-19 세 경로 모두 나라별 적재량 합이 주문 수량과 같음', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    await forEachOption(page, async (type) => {
      const loads = loadsByCountry(await readTrips(page));
      const msg = OPTION_LABEL[type];
      expect(sum(loads.CHINA), `${msg} 중국`).toBe(3000);
      expect(sum(loads.VIETNAM), `${msg} 베트남`).toBe(3000);
      expect(sum(loads.INDONESIA), `${msg} 인도네시아`).toBe(3000);
      expect(sum(loads.JAPAN), `${msg} 일본`).toBe(3000);
      expect([...loads.JAPAN].sort((a, b) => b - a), `${msg} 일본 2,000K + 1,000K`).toEqual([2000, 1000]);
    });
  });

  test('SIM-20 기본 주문(12,000K 균등)의 생산 원가는 어느 경로든 78.00억원', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    await forEachOption(page, async (type) => {
      expect(await stat(page, '생산 원가'), `${OPTION_LABEL[type]} 생산 원가`).toBe('78.00억원');
    });
  });

  test('SIM-21 베트남 2,000K 단독 주문: 늦게 출항하고 한국 대기는 운항비가 들지 않음', async ({ page }) => {
    await openNewOrder(page);

    await setTotal(page, 2000);
    await expectGauges(page, { CHINA: 500, JAPAN: 500, VIETNAM: 500, INDONESIA: 500 });
    // 중국 → 일본 → 인도네시아 → 중국 → 일본 순서로 0 을 넣어 베트남에 몰아 준다
    for (const c of ['CHINA', 'JAPAN', 'INDONESIA', 'CHINA', 'JAPAN'] as const) {
      await setGauge(page, c, 0);
    }
    await expectGauges(page, { CHINA: 0, JAPAN: 0, VIETNAM: 2000, INDONESIA: 0 });
    await runSimulation(page);

    await forEachOption(page, async (type) => {
      const msg = OPTION_LABEL[type];
      const card = await readCard(page, type);
      expect(card.ships, `${msg} 배 척수`).toBe(1);
      expect(card.days, `${msg} 소요 일수`).toBe(15);
      expect(card.shipping, `${msg} 운항비(억원)`).toBe(10);
      expect(card.total, `${msg} 총 비용(억원)`).toBe(36); // 생산 11 + 운항 10 + 운영(1개 공장 x 1억 x 15일) 15

      expect(await stat(page, '소요 일수')).toBe('15일');
      expect(await stat(page, '생산 원가')).toBe('11.00억원');
      expect(await stat(page, '운항비')).toBe('10.00억원');
      expect(await stat(page, '운영비')).toBe('15.00억원');
      expect(await stat(page, '총 비용')).toBe('36.00억원');

      const trips = await readTrips(page);
      expect(trips, `${msg} 왕복 1번`).toHaveLength(1);
      expect(trips[0]).toMatchObject({ ship: 1, depart: 5, ret: 15, sail: 10 });
      expect(trips[0].stops).toEqual([{ country: 'VIETNAM', arrive: 10, load: 2000 }]);
    });
  });

  test('SIM-22 한 나라는 한 척의 배만 방문', async ({ page }) => {
    await openNewOrder(page);
    await runSimulation(page);

    await forEachOption(page, async (type) => {
      const trips = await readTrips(page);
      const shipsByCountry = new Map<Factory, Set<number>>();
      for (const t of trips) {
        for (const s of t.stops) {
          if (!shipsByCountry.has(s.country)) shipsByCountry.set(s.country, new Set());
          shipsByCountry.get(s.country)!.add(t.ship);
        }
      }
      expect(Array.from(shipsByCountry.keys()).sort(), `${OPTION_LABEL[type]} 방문 나라`).toEqual([...FACTORIES].sort());
      for (const [country, ships] of shipsByCountry) {
        expect(ships.size, `${OPTION_LABEL[type]} ${LABEL[country]}를 방문한 배 수`).toBe(1);
      }
    });
  });
});
