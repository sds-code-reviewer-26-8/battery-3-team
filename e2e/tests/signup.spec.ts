import { expect, test, type Page } from '@playwright/test';
import { changeStatus, login, metaValue, runSimulation } from './helpers';

/**
 * 회원 가입 (SGN-01 ~ SGN-08)
 *
 * 로그인 화면에서 가입하고, 가입한 계정으로 로그인해 admin 과 같은 기능을 쓸 수 있는지 확인한다.
 * DB 변경: 가입 테스트마다 사용자 1명이 만들어진다(삭제 기능이 없다). 반복 실행에서도 겹치지 않게
 * 아이디에 무작위 값을 붙인다. SGN-04 는 주문 1건을 만들고 마스터 데이터를 같은 값으로 한 번 저장한다.
 */
const PASSWORD = 'password123';

/** 실행마다 겹치지 않는 아이디 (영문 소문자·숫자, 20자 이하) */
function newUsername(): string {
  return `u${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

async function fillSignup(page: Page, username: string, password: string, confirm: string) {
  await page.getByLabel('아이디').fill(username);
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  await page.getByLabel('비밀번호 확인').fill(confirm);
}

/** 가입 화면에서 가입을 끝낸다. 끝나면 가입 완료 안내가 보인다. */
async function signUp(page: Page, username: string, password = PASSWORD) {
  await page.goto('/signup');
  await fillSignup(page, username, password, password);
  await page.getByRole('button', { name: '가입하기' }).click();
  await expect(page.locator('.alert.ok')).toContainText('가입이 완료되었습니다');
}

async function logInAs(page: Page, username: string, password = PASSWORD) {
  await page.goto('/login');
  await page.getByLabel('아이디').fill(username);
  await page.getByLabel('비밀번호').fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
}

test.describe('회원 가입', () => {
  test('SGN-01 로그인 화면의 회원가입 링크로 가입 화면에 간다', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: '회원가입' }).click();

    await expect(page).toHaveURL(/\/signup$/);
    await expect(page.getByRole('heading', { name: '회원가입' })).toBeVisible();
    await expect(page.getByRole('button', { name: '가입하기' })).toBeVisible();
  });

  test('SGN-02 가입하면 완료 안내가 나오고 자동으로 로그인되지는 않는다', async ({ page }) => {
    const username = newUsername();
    await signUp(page, username);

    await expect(page.locator('.alert.ok')).toContainText(username);
    await expect(page.getByRole('link', { name: '로그인하러 가기' })).toBeVisible();
    await expect(page.getByRole('button', { name: '가입하기' })).toBeHidden(); // 가입 양식은 감춘다

    await page.goto('/orders'); // 로그인하지 않았으므로 로그인 화면으로 간다
    await expect(page).toHaveURL(/\/login$/);
  });

  test('SGN-03 가입한 계정으로 로그인하면 주문 목록으로 가고 상단에 아이디가 보인다', async ({ page }) => {
    const username = newUsername();
    await signUp(page, username);
    await page.getByRole('link', { name: '로그인하러 가기' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await logInAs(page, username);

    await expect(page).toHaveURL(/\/orders$/);
    await expect(page.locator('header .user span')).toHaveText(username);
  });

  test('SGN-04 가입한 계정도 admin 과 같은 기능을 쓸 수 있고 기록에 아이디가 남는다', async ({ page }) => {
    const username = newUsername();
    await signUp(page, username);
    await logInAs(page, username);
    await expect(page).toHaveURL(/\/orders$/);

    // 주문 추가·확정
    await runSimulation(page);
    await page.locator('#confirmBtn').click();
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(metaValue(page, '작성자')).toHaveText(username);

    // 상태 변경 (기록에도 아이디가 남는다)
    await changeStatus(page, '진행(으)로 변경');
    await expect(page.locator('.alert.ok')).toContainText("'진행'");
    await expect(page.locator('section.card', { has: page.getByRole('heading', { name: '상태 이력' }) })
      .locator('tbody tr').last().locator('td').last()).toHaveText(username);

    // 마스터 데이터 저장 (값은 바꾸지 않고 그대로 저장)
    await page.goto('/master');
    await expect(page.locator('input[name="shipCount"]')).toBeVisible();
    await page.getByRole('button', { name: '저장', exact: true }).click();
    await expect(page.locator('.alert.ok')).toContainText('마스터 데이터를 저장했습니다');
  });

  test('SGN-05 규칙에 맞지 않으면 가입되지 않고 이유를 모두 보여 준다', async ({ page }) => {
    await page.goto('/signup');
    await fillSignup(page, 'ab', 'short', 'different');
    await page.getByRole('button', { name: '가입하기' }).click();

    await expect(page.locator('.alert.error')).toContainText('가입하지 않았습니다');
    await expect(page.locator('.alert.error li')).toHaveText([
      '아이디는 영문·숫자·밑줄(_) 4~20자여야 합니다',
      '비밀번호는 8자 이상 64자 이하여야 합니다',
      '비밀번호와 비밀번호 확인이 같지 않습니다',
    ]);
    await expect(page.getByLabel('아이디')).toHaveValue('ab'); // 아이디는 남긴다
    await expect(page.getByLabel('비밀번호', { exact: true })).toHaveValue(''); // 비밀번호는 비운다
    await expect(page.getByLabel('비밀번호 확인')).toHaveValue('');

    await logInAs(page, 'ab', 'short'); // 가입되지 않았으므로 로그인도 안 된다
    await expect(page.locator('.alert.error')).toHaveText('아이디 또는 비밀번호가 올바르지 않습니다');
  });

  test('SGN-06 이미 있는 아이디는 대소문자 구분 없이 가입할 수 없다', async ({ page }) => {
    const username = newUsername();
    await signUp(page, username);

    for (const taken of ['admin', 'ADMIN', username.toUpperCase()]) {
      await page.goto('/signup');
      await fillSignup(page, taken, PASSWORD, PASSWORD);
      await page.getByRole('button', { name: '가입하기' }).click();
      await expect(page.locator('.alert.error li'), taken).toHaveText(['이미 사용 중인 아이디입니다']);
    }
  });

  test('SGN-07 로그인한 상태에서 가입 화면을 열면 주문 목록으로 간다', async ({ page }) => {
    await login(page);
    await page.goto('/signup');
    await expect(page).toHaveURL(/\/orders$/);
  });

  test('SGN-08 로그인하지 않아도 가입 화면은 열리지만 다른 화면은 여전히 막혀 있다', async ({ page, request }) => {
    const response = await page.goto('/signup');
    expect(response!.status()).toBe(200);
    await expect(page).toHaveURL(/\/signup$/);

    for (const path of ['/orders', '/orders/new', '/master']) {
      await page.goto(path);
      await expect(page, path).toHaveURL(/\/login$/);
    }
    expect((await request.get('/api/orders')).status()).toBe(401);
    expect((await request.get('/api/master')).status()).toBe(401);
  });
});
