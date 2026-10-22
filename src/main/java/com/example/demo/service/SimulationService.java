package com.example.demo.service;

import com.example.demo.entity.Country;
import com.example.demo.simulation.Master;
import com.example.demo.simulation.Plan;
import com.example.demo.simulation.Planner;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

	/** 시뮬레이션 결과. master 는 이 계산에 쓴 마스터 값(확정 시 비교·저장에 쓴다). */
	public record SimulationResult(Master master, Map<Country, Integer> orders, Map<Country, Integer> visits,
			List<Plan> options) {
	}

	private final MasterService masterService;

	public SimulationService(MasterService masterService) {
		this.masterService = masterService;
	}

	public SimulationResult simulate(Map<Country, Integer> rawOrders) {
		Map<Country, Integer> validated = validateOrders(rawOrders);
		Master master = masterService.currentMaster();
		return new SimulationResult(master, validated, Planner.visits(master, validated),
				Planner.plan(master, validated));
	}

	private static Map<Country, Integer> validateOrders(Map<Country, Integer> raw) {
		Map<Country, Integer> validated = new EnumMap<>(Country.class);
		long total = 0;
		for (Country c : Country.factories()) {
			Integer q = raw == null ? null : raw.get(c);
			int v = q == null ? 0 : q;
			if (v < 0) {
				throw new BusinessException(c.getLabel() + " 주문 수량은 0 이상이어야 합니다");
			}
			validated.put(c, v);
			total += v;
		}
		if (total <= 0) {
			throw new BusinessException("총 생산 필요 수는 1K 이상이어야 합니다");
		}
		if (total > Planner.MAX_TOTAL_K) {
			throw new BusinessException("총 생산 필요 수는 %,dK 이하여야 합니다".formatted(Planner.MAX_TOTAL_K));
		}
		return validated;
	}
}
