package com.example.demo.simulation;

import com.example.demo.entity.Country;
import java.util.List;
import java.util.Map;

/**
 * 경로 하나에 대한 계획. 모든 시각은 주문일(0일차)부터 센 일수이다.
 *
 * @param stock      국가별 재고(K). index t 는 t일 시점(그 시각의 수거 반영 후)의 재고
 * @param production 국가별 생산량(K). index t 는 t일~t+1일 동안의 생산량
 */
public record Plan(String type, String label, int totalDays, long productionCost, long shippingCost, long totalCost,
		List<ShipPlan> ships, Map<Country, int[]> stock, Map<Country, int[]> production) {

	public record ShipPlan(int no, List<Trip> trips, int sailDays) {
	}

	/** 한국 출항 → 여러 나라 방문 → 한국 귀환 한 번의 왕복 */
	public record Trip(int departDay, int returnDay, List<Stop> stops) {
	}

	public record Stop(Country country, int arriveDay, int leaveDay, int loadK) {
	}
}
