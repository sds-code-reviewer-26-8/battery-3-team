package com.example.demo.controller;

import com.example.demo.entity.Country;
import com.example.demo.service.BusinessException;
import com.example.demo.service.MasterService;
import com.example.demo.simulation.Master;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MasterController {

	private final MasterService service;

	public MasterController(MasterService service) {
		this.service = service;
	}

	@GetMapping("/master")
	public String master(Model model) {
		if (!model.containsAttribute("form")) {
			model.addAttribute("form", toForm(service.currentMaster()));
		}
		model.addAttribute("factories", Country.factories());
		model.addAttribute("countries", Country.values());
		return "master";
	}

	@PostMapping("/master")
	public String saveMaster(@RequestParam Map<String, String> form, Model model, RedirectAttributes redirect) {
		try {
			service.updateMaster(form);
		} catch (BusinessException e) {
			model.addAttribute("errors", e.getMessages());
			model.addAttribute("form", form);
			model.addAttribute("factories", Country.factories());
			model.addAttribute("countries", Country.values());
			return "master";
		}
		redirect.addFlashAttribute("message", "마스터 데이터를 저장했습니다");
		return "redirect:/master";
	}

	/** 화면 입력칸 이름 → 값 */
	private static Map<String, String> toForm(Master m) {
		Map<String, String> form = new HashMap<>();
		for (Master.FactoryParam f : m.factories()) {
			form.put("maxDaily_" + f.country(), String.valueOf(f.maxDaily()));
			form.put("stockLimit_" + f.country(), String.valueOf(f.stockLimit()));
			form.put("unitCost_" + f.country(), String.valueOf(f.unitCost()));
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
