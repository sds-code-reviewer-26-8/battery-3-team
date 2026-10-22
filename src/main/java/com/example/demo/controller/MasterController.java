package com.example.demo.controller;

import com.example.demo.service.MasterService;
import com.example.demo.simulation.Master;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master")
public class MasterController {

	private final MasterService masterService;

	public MasterController(MasterService masterService) {
		this.masterService = masterService;
	}

	@GetMapping
	public Master master() {
		return masterService.currentMaster();
	}

	/** 마스터 화면 입력칸 이름 → 값 (운항비는 억 원) */
	@GetMapping("/form")
	public Map<String, String> form() {
		return toForm(masterService.currentMaster());
	}

	/** 입력칸 이름 → 값 을 검증하고 저장한다. 틀린 값이 있으면 아무것도 저장하지 않고 400 과 이유 목록을 돌려준다. */
	@PutMapping
	public Map<String, String> save(@RequestBody Map<String, String> form) {
		masterService.updateMaster(form);
		return toForm(masterService.currentMaster());
	}

	private static Map<String, String> toForm(Master m) {
		Map<String, String> form = new HashMap<>();
		for (Master.FactoryParam f : m.factories()) {
			form.put("maxDaily_" + f.country(), String.valueOf(f.maxDaily()));
			form.put("stockLimit_" + f.country(), String.valueOf(f.stockLimit()));
			form.put("unitCost_" + f.country(), String.valueOf(f.unitCost()));
			form.put("operatingCost_" + f.country(), new BigDecimal(f.operatingCostWon())
					.divide(BigDecimal.valueOf(MasterService.WON_PER_EOK)).stripTrailingZeros().toPlainString());
		}
		for (Master.RouteParam r : m.routes()) {
			form.put(MasterService.routeKey(r.from(), r.to()), String.valueOf(r.days()));
		}
		form.put("shipCount", String.valueOf(m.shipCount()));
		form.put("shipDailyCost", new BigDecimal(m.shipDailyCostWon())
				.divide(BigDecimal.valueOf(MasterService.WON_PER_EOK)).stripTrailingZeros().toPlainString());
		return form;
	}
}
