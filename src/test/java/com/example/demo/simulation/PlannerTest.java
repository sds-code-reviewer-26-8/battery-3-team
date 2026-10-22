package com.example.demo.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entity.Country;
import com.example.demo.simulation.Master.FactoryParam;
import com.example.demo.simulation.Master.RouteParam;
import com.example.demo.simulation.Plan.Stop;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlannerTest {

	private static Master master(int ships) {
		return master(ships, 100_000_000L, 100_000_000L, 100_000_000L, 100_000_000L);
	}

	/** 공장별 하루 운영비(원: 중국, 일본, 베트남, 인도네시아 순)를 지정한 마스터 */
	private static Master master(int ships, long chinaOp, long japanOp, long vietnamOp, long indonesiaOp) {
		return new Master(1, ships, 100_000_000L,
				List.of(new FactoryParam(Country.CHINA, 400, 4000, 700, chinaOp),
						new FactoryParam(Country.JAPAN, 800, 2000, 900, japanOp),
						new FactoryParam(Country.VIETNAM, 200, 10000, 550, vietnamOp),
						new FactoryParam(Country.INDONESIA, 300, 7000, 450, indonesiaOp)),
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

	private static Map<Country, Integer> orders(int china, int japan, int vietnam, int indonesia) {
		Map<Country, Integer> m = new EnumMap<>(Country.class);
		m.put(Country.CHINA, china);
		m.put(Country.JAPAN, japan);
		m.put(Country.VIETNAM, vietnam);
		m.put(Country.INDONESIA, indonesia);
		return m;
	}

	@Test
	void visitsAreCeilOfOrderOverStockLimit() {
		Map<Country, Integer> v = Planner.visits(master(3), orders(4001, 2000, 0, 14000));
		assertThat(v.get(Country.CHINA)).isEqualTo(2);
		assertThat(v.get(Country.JAPAN)).isEqualTo(1);
		assertThat(v.get(Country.VIETNAM)).isEqualTo(0);
		assertThat(v.get(Country.INDONESIA)).isEqualTo(2);
	}

	@Test
	void everyOptionCollectsExactlyWhatWasOrdered() {
		Map<Country, Integer> orders = orders(3000, 3000, 3000, 3000);
		for (Plan plan : Planner.plan(master(3), orders)) {
			Map<Country, Integer> loaded = new EnumMap<>(Country.class);
			plan.ships().forEach(s -> s.trips().forEach(t -> t.stops()
					.forEach(st -> loaded.merge(st.country(), st.loadK(), Integer::sum))));
			assertThat(loaded).as(plan.label()).isEqualTo(orders);
			assertThat(plan.totalCost())
					.isEqualTo(plan.productionCost() + plan.shippingCost() + plan.operatingCost());
		}
	}

	@Test
	void eachCountryIsVisitedByOneShipOnly() {
		for (Plan plan : Planner.plan(master(4), orders(5000, 5000, 5000, 5000))) {
			Map<Country, Integer> owner = new EnumMap<>(Country.class);
			plan.ships().forEach(s -> s.trips().forEach(t -> t.stops().forEach(st -> {
				Integer prev = owner.put(st.country(), s.no());
				assertThat(prev == null || prev == s.no()).as(plan.label() + " " + st.country()).isTrue();
			})));
		}
	}

	@Test
	void productionCostIsCellsTimesUnitCost() {
		Plan plan = Planner.plan(master(3), orders(1000, 0, 0, 0)).get(0);
		assertThat(plan.productionCost()).isEqualTo(1000L * 1000 * 700);
	}

	@Test
	void singleSmallOrderTakesProductionPlusRoundTrip() {
		// 중국 400K = 하루 최대 400K → 1일 생산, 한국-중국 1일 → 왕복 2일
		Plan plan = Planner.plan(master(1), orders(400, 0, 0, 0)).get(0);
		assertThat(plan.ships()).hasSize(1);
		Stop stop = plan.ships().get(0).trips().get(0).stops().get(0);
		assertThat(stop.arriveDay()).isEqualTo(1);
		assertThat(plan.totalDays()).isEqualTo(2);
		assertThat(plan.shippingCost()).isEqualTo(2 * 100_000_000L);
	}

	@Test
	void shipDepartsLateSoItDoesNotWaitAtPort() {
		// 베트남 2000K / 200K일 = 10일 생산. 한국-베트남 5일 → 5일차 출항, 10일차 도착, 15일차 귀환
		Plan plan = Planner.plan(master(1), orders(0, 0, 2000, 0)).get(0);
		var trip = plan.ships().get(0).trips().get(0);
		assertThat(trip.departDay()).isEqualTo(5);
		assertThat(trip.stops().get(0).arriveDay()).isEqualTo(10);
		assertThat(plan.totalDays()).isEqualTo(15);
		assertThat(plan.shippingCost()).isEqualTo(10 * 100_000_000L);
	}

	@Test
	void overStockLimitOrderIsCollectedInSeveralRoundTrips() {
		// 일본 4500K, 재고 상한 2000K → 3번 방문(2000, 2000, 500)
		Plan plan = Planner.plan(master(1), orders(0, 4500, 0, 0)).get(0);
		List<Integer> loads = plan.ships().get(0).trips().stream().map(t -> t.stops().get(0).loadK()).toList();
		assertThat(loads).containsExactly(2000, 2000, 500);
		assertThat(plan.stock().get(Country.JAPAN)[plan.totalDays()]).isZero();
	}

	@Test
	void productionStopsAtStockLimitUntilPickup() {
		Plan plan = Planner.plan(master(1), orders(0, 4500, 0, 0)).get(0);
		int limit = 2000;
		for (int s : plan.stock().get(Country.JAPAN)) {
			assertThat(s).isLessThanOrEqualTo(limit);
		}
	}

	@Test
	void costOptionIsNotMoreExpensiveAndFastOptionIsNotSlower() {
		List<Plan> plans = Planner.plan(master(3), orders(3000, 3000, 3000, 3000));
		Plan cost = plans.get(0);
		Plan fast = plans.get(1);
		Plan near = plans.get(2);
		assertThat(cost.shippingCost()).isLessThanOrEqualTo(fast.shippingCost()).isLessThanOrEqualTo(near.shippingCost());
		assertThat(fast.totalDays()).isLessThanOrEqualTo(cost.totalDays()).isLessThanOrEqualTo(near.totalDays());
	}

	@Test
	void usesAtMostAvailableShips() {
		for (Plan plan : Planner.plan(master(2), orders(3000, 3000, 3000, 3000))) {
			assertThat(plan.ships().size()).isLessThanOrEqualTo(2);
		}
	}

	@Test
	void rejectsEmptyOrder() {
		assertThatThrownBy(() -> Planner.plan(master(3), orders(0, 0, 0, 0)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void defaultOrderSummary() {
		for (Plan plan : Planner.plan(master(3), orders(3000, 3000, 3000, 3000))) {
			System.out.printf("%s: %d일, 생산 %.2f억, 운항 %.2f억, 합계 %.2f억, 배 %d척%n", plan.label(), plan.totalDays(),
					plan.productionCost() / 1e8, plan.shippingCost() / 1e8, plan.totalCost() / 1e8,
					plan.ships().size());
			plan.ships().forEach(s -> s.trips().forEach(t -> System.out.println("  " + s.no() + "호 출항 "
					+ t.departDay() + " → " + t.stops().stream()
							.map(st -> st.country().getLabel() + "(" + st.arriveDay() + "일," + st.loadK() + ")").toList()
					+ " → 귀환 " + t.returnDay())));
		}
	}

	private static Plan byType(List<Plan> plans, String type) {
		return plans.stream().filter(p -> p.type().equals(type)).findFirst().orElseThrow();
	}

	@Test
	void 운영비는_주문있는_공장수x일운영비xtotalDays() {
		// 중국·일본만 주문: 운영비는 두 공장의 하루 운영비 합 x 총 소요 일수, 주문 없는 공장은 0원
		Master m = master(3, 100_000_000L, 250_000_000L, 700_000_000L, 900_000_000L);
		for (Plan plan : Planner.plan(m, orders(3000, 3000, 0, 0))) {
			assertThat(plan.operatingCost()).as(plan.label()).isEqualTo(350_000_000L * plan.totalDays());
			assertThat(plan.totalCost()).as(plan.label())
					.isEqualTo(plan.productionCost() + plan.shippingCost() + plan.operatingCost());
		}
		// 한 공장만 주문하면 그 공장 운영비만
		for (Plan plan : Planner.plan(m, orders(0, 0, 0, 1000))) {
			assertThat(plan.operatingCost()).as(plan.label()).isEqualTo(900_000_000L * plan.totalDays());
		}
		// 운영비 0 이면 운영비도 0
		for (Plan plan : Planner.plan(master(3, 0, 0, 0, 0), orders(3000, 3000, 3000, 3000))) {
			assertThat(plan.operatingCost()).isZero();
		}
	}

	@Test
	void 비용최소는_운항비와_운영비_합이_최소() {
		Map<Country, Integer> orders = orders(3000, 3000, 3000, 3000);
		// 운영비를 크게 잡은 마스터: 운항비+운영비 합이 다른 두 안보다 크지 않다
		List<Plan> heavy = Planner.plan(master(3, 3_000_000_000L, 3_000_000_000L, 3_000_000_000L, 3_000_000_000L),
				orders);
		Plan cost = byType(heavy, Planner.COST);
		assertThat(cost.label()).isEqualTo("비용 최소");
		for (String other : new String[] {Planner.FAST, Planner.NEAR}) {
			Plan p = byType(heavy, other);
			assertThat(cost.shippingCost() + cost.operatingCost()).as(other)
					.isLessThanOrEqualTo(p.shippingCost() + p.operatingCost());
		}
		// 운영비가 0 이면 기존 "운항비 최소" 와 같은 기준(운항비 최소)이다
		List<Plan> none = Planner.plan(master(3, 0, 0, 0, 0), orders);
		Plan costNone = byType(none, Planner.COST);
		for (Plan p : none) {
			assertThat(costNone.shippingCost()).isLessThanOrEqualTo(p.shippingCost());
		}
	}

	@Test
	void FAST는_totalDays_최소_운영비무관() {
		Map<Country, Integer> orders = orders(3000, 3000, 3000, 3000);
		List<Plan> cheap = Planner.plan(master(3, 0, 0, 0, 0), orders);
		List<Plan> heavy = Planner.plan(master(3, 5_000_000_000L, 5_000_000_000L, 5_000_000_000L, 5_000_000_000L),
				orders);
		for (List<Plan> plans : List.of(cheap, heavy)) {
			Plan fast = byType(plans, Planner.FAST);
			for (Plan p : plans) {
				assertThat(fast.totalDays()).isLessThanOrEqualTo(p.totalDays());
			}
		}
		// 운영비가 달라져도 FAST·NEAR 의 일정과 운항비는 그대로
		for (String type : new String[] {Planner.FAST, Planner.NEAR}) {
			assertThat(byType(heavy, type).totalDays()).isEqualTo(byType(cheap, type).totalDays());
			assertThat(byType(heavy, type).shippingCost()).isEqualTo(byType(cheap, type).shippingCost());
		}
	}
}
