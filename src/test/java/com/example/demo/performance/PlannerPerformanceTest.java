package com.example.demo.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entity.Country;
import com.example.demo.performance.PerformanceSupport.Timing;
import com.example.demo.simulation.Master;
import com.example.demo.simulation.Master.FactoryParam;
import com.example.demo.simulation.Master.RouteParam;
import com.example.demo.simulation.Plan;
import com.example.demo.simulation.Planner;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 경로 계산(Planner)의 성능 테스트. 계산은 모든 경우의 수를 따지는 방식이라 공장 4곳·배 4척 이하를 전제로 한다(PRD 7장).
 * 허용 범위의 가장 무거운 입력(총 100,000K, 배 4척)까지 정해진 시간 안에 끝나는지 확인한다.
 *
 * <p>기준 시간은 개발 PC에서 잰 값(워밍업 후 1~5ms, 첫 실행은 최대 25ms 안팎)의 20~30배 정도로 잡았다.
 * 느린 PC에서도 흔들리지 않으면서, 후보 탐색이 폭발하는 것 같은 눈에 띄는 퇴보는 잡도록 했다.
 */
@Tag("performance")
class PlannerPerformanceTest {

	/** 기본 입력(12,000K)을 한 번 계산하는 데 허용하는 중앙값 */
	private static final long DEFAULT_MEDIAN_BUDGET_MS = 100;
	/** 허용 범위의 가장 무거운 입력을 한 번 계산하는 데 허용하는 중앙값 */
	private static final long HEAVY_MEDIAN_BUDGET_MS = 150;
	/** 어느 한 번도 넘으면 안 되는 상한(첫 실행의 JIT·GC 흔들림까지 포함) */
	private static final long ANY_RUN_BUDGET_MS = 1_000;

	private static Master master(int ships, int stockLimitOverride) {
		return new Master(1, ships, 100_000_000L,
				List.of(new FactoryParam(Country.CHINA, 400, stockLimitOverride > 0 ? stockLimitOverride : 4000, 700, 100_000_000L),
						new FactoryParam(Country.JAPAN, 800, stockLimitOverride > 0 ? stockLimitOverride : 2000, 900, 100_000_000L),
						new FactoryParam(Country.VIETNAM, 200, stockLimitOverride > 0 ? stockLimitOverride : 10000, 550, 100_000_000L),
						new FactoryParam(Country.INDONESIA, 300, stockLimitOverride > 0 ? stockLimitOverride : 7000, 450, 100_000_000L)),
				List.of(new RouteParam(Country.KOREA, Country.CHINA, 1),
						new RouteParam(Country.KOREA, Country.JAPAN, 2),
						new RouteParam(Country.KOREA, Country.VIETNAM, 5),
						new RouteParam(Country.KOREA, Country.INDONESIA, 7),
						new RouteParam(Country.CHINA, Country.JAPAN, 2),
						new RouteParam(Country.CHINA, Country.VIETNAM, 4),
						new RouteParam(Country.CHINA, Country.INDONESIA, 6),
						new RouteParam(Country.JAPAN, Country.VIETNAM, 5),
						new RouteParam(Country.JAPAN, Country.INDONESIA, 7),
						new RouteParam(Country.VIETNAM, Country.INDONESIA, 2)));
	}

	private static Map<Country, Integer> evenOrders(int eachK) {
		Map<Country, Integer> orders = new EnumMap<>(Country.class);
		for (Country c : Country.factories()) {
			orders.put(c, eachK);
		}
		return orders;
	}

	/** 계산이 실패해서 빨리 끝난 것을 "빠르다"로 착각하지 않도록, 세 안이 정상적으로 나왔는지 함께 확인한다. */
	private static void assertThreeValidPlans(List<Plan> plans) {
		assertThat(plans).hasSize(3);
		for (Plan plan : plans) {
			assertThat(plan.totalDays()).isPositive();
			assertThat(plan.totalCost()).isEqualTo(plan.productionCost() + plan.shippingCost() + plan.operatingCost());
		}
	}

	private static void assertWithin(Timing timing, long medianBudgetMs) {
		assertThat(timing.medianMs()).as(timing.toString()).isLessThanOrEqualTo(medianBudgetMs);
		assertThat(timing.maxMs()).as(timing.toString()).isLessThanOrEqualTo(ANY_RUN_BUDGET_MS);
	}

	@Test
	void defaultOrderPlansWithinBudget() throws Exception {
		Master master = master(3, 0);
		Map<Country, Integer> orders = evenOrders(3000); // 총 12,000K
		assertThreeValidPlans(Planner.plan(master, orders));

		Timing timing = PerformanceSupport.measure("기본 12,000K, 배 3척", 3, 15,
				() -> Planner.plan(master, orders));

		assertWithin(timing, DEFAULT_MEDIAN_BUDGET_MS);
	}

	@Test
	void maximumOrderPlansWithinBudgetForEveryShipCount() throws Exception {
		Map<Country, Integer> orders = evenOrders(25_000); // 총 100,000K (한도)
		for (int ships = 1; ships <= 4; ships++) {
			Master master = master(ships, 0);
			assertThreeValidPlans(Planner.plan(master, orders));

			Timing timing = PerformanceSupport.measure("최대 100,000K, 배 %d척".formatted(ships), 3, 10,
					() -> Planner.plan(master, orders));

			assertWithin(timing, HEAVY_MEDIAN_BUDGET_MS);
		}
	}

	@Test
	void manyVisitsPerFactoryPlansWithinBudget() throws Exception {
		// 재고 상한을 1,000K 로 낮춰 공장마다 25번씩 방문하게 만든 입력(방문 횟수가 가장 많은 쪽)
		Master master = master(4, 1000);
		Map<Country, Integer> orders = evenOrders(25_000);
		assertThreeValidPlans(Planner.plan(master, orders));

		Timing timing = PerformanceSupport.measure("최대 100,000K, 배 4척, 공장당 25번 방문", 3, 10,
				() -> Planner.plan(master, orders));

		assertWithin(timing, HEAVY_MEDIAN_BUDGET_MS);
	}
}
