import { defineConfig, devices } from '@playwright/test';

// BASE_URL 을 주면 이미 떠 있는 서버(예: IDEA 로 띄운 localhost:8080)에 대고 실행한다. 서버는 직접 띄우지 않는다.
// 주지 않으면 별도 포트(E2E_PORT, 기본 18080)에 인메모리 DB 서버를 띄워서 개발용 파일 DB(./data/battery)를 건드리지 않는다.
const EXTERNAL = process.env.BASE_URL;
const PORT = Number(process.env.E2E_PORT ?? 18080);

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  // SLOW_MO 로 느리게 볼 때는 한 테스트가 오래 걸리므로 제한 시간도 늘린다.
  timeout: process.env.SLOW_MO ? 120_000 : 30_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: EXTERNAL ?? `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // 눈으로 따라가며 볼 때: SLOW_MO=400 (밀리초) 를 주면 동작 사이에 쉰다.
    launchOptions: { slowMo: Number(process.env.SLOW_MO ?? 0) },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: EXTERNAL
    ? undefined
    : {
        command: process.platform === 'win32' ? '.\\gradlew.bat bootRun' : './gradlew bootRun',
        cwd: '..',
        url: `http://localhost:${PORT}/login`,
        reuseExistingServer: true,
        timeout: 180_000,
        env: {
          SERVER_PORT: String(PORT),
          SPRING_DATASOURCE_URL: 'jdbc:h2:mem:battery-e2e;DB_CLOSE_DELAY=-1',
        },
      },
});
