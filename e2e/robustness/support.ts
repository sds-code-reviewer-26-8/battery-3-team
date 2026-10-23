// 견고성 테스트가 쓰는 공통 함수 모음. tests/helpers.ts 를 직접 import 하지 않고 여기서 필요한 것만 다시 내보낸다.
// tests/helpers.ts 가 바뀌어도 이 파일만 고치면 되도록 하기 위함이다.
export {
  DEFAULT_ORDERS,
  login,
  newLoggedInPage,
  runSimulation,
  createOrderViaApi,
  advanceViaApi,
  chipCounts,
} from '../tests/helpers';
