package com.example.demo.performance;

import java.util.Arrays;

/** 성능 테스트가 같이 쓰는 시간 측정 도우미. 워밍업 후 여러 번 재서 중앙값과 최댓값을 돌려준다. */
final class PerformanceSupport {

	private PerformanceSupport() {
	}

	/** 측정 한 건의 결과(밀리초) */
	record Timing(String name, long medianMs, long maxMs, int runs) {

		@Override
		public String toString() {
			return "[성능] %s: 중앙값 %dms, 최대 %dms (%d회)".formatted(name, medianMs, maxMs, runs);
		}
	}

	interface Action {
		void run() throws Exception;
	}

	/** warmups 번 먼저 실행해 JIT·캐시를 데운 뒤, runs 번 재서 중앙값과 최댓값을 구한다. 결과는 표준 출력에도 남긴다. */
	static Timing measure(String name, int warmups, int runs, Action action) throws Exception {
		for (int i = 0; i < warmups; i++) {
			action.run();
		}
		long[] ms = new long[runs];
		for (int i = 0; i < runs; i++) {
			long start = System.nanoTime();
			action.run();
			ms[i] = (System.nanoTime() - start) / 1_000_000;
		}
		long[] sorted = ms.clone();
		Arrays.sort(sorted);
		Timing timing = new Timing(name, sorted[runs / 2], sorted[runs - 1], runs);
		System.out.println(timing);
		return timing;
	}
}
