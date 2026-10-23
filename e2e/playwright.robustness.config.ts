import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// 견고성(반복 실행 안정성·동시 요청) 테스트용 설정. 서버·브라우저 설정은 기존 config 를 그대로 쓰고 아래만 바꾼다.
// - testDir: ./robustness (기존 npm test 는 ./tests 만 보므로 견고성 테스트는 섞이지 않는다)
// - timeout: 반복 실행이 길어질 수 있어 기존(30초 / SLOW_MO 시 120초)보다 넉넉하게
// - 결과 폴더: 기존 테스트의 playwright-report / test-results 를 덮어쓰지 않도록 따로 둔다
export default defineConfig({
  ...base,
  testDir: './robustness',
  timeout: process.env.SLOW_MO ? 300_000 : 120_000,
  outputDir: './test-results-robust',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-robust' }]],
});
