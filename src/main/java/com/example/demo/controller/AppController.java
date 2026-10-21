package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.service.BatteryService;
import com.example.demo.service.BusinessException;
import com.example.demo.simulation.Master;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AppController {

	private static final String ACCOUNT = "admin";

	public record SimulateRequest(Map<Country, Integer> orders) {
	}

	public record ConfirmRequest(Map<Country, Integer> orders, String routeType, long masterVersion, String memo) {
	}

	private final BatteryService service;

	public AppController(BatteryService service) {
		this.service = service;
	}

	/** 모든 화면 상단에 로그인한 아이디를 보여 주기 위한 공통 모델 */
	@ModelAttribute("loginUser")
	public String loginUser(HttpSession session) {
		return (String) session.getAttribute(LoginInterceptor.SESSION_USER);
	}

	// ---------------------------------------------------------------- 로그인

	@GetMapping("/")
	public String home() {
		return "redirect:/orders";
	}

	@GetMapping("/login")
	public String loginForm(HttpSession session) {
		if (session.getAttribute(LoginInterceptor.SESSION_USER) != null) {
			return "redirect:/orders";
		}
		return "login";
	}

	@PostMapping("/login")
	public String login(@RequestParam String username, @RequestParam String password, HttpSession session,
			Model model) {
		if (ACCOUNT.equals(username) && ACCOUNT.equals(password)) {
			session.setAttribute(LoginInterceptor.SESSION_USER, username);
			return "redirect:/orders";
		}
		model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다");
		return "login";
	}

	@PostMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/login";
	}

	// ---------------------------------------------------------------- 주문 화면

	@GetMapping("/orders")
	public String list(@RequestParam(required = false) OrderStatus status, Model model) {
		model.addAttribute("orders", service.listOrders(status));
		model.addAttribute("counts", service.countByStatus());
		model.addAttribute("status", status);
		model.addAttribute("statuses", OrderStatus.values());
		model.addAttribute("total", service.listOrders(null).size());
		return "orders";
	}

	@GetMapping("/orders/new")
	public String newOrder(Model model) {
		model.addAttribute("master", service.currentMaster());
		return "order-new";
	}

	@GetMapping("/orders/{id}")
	public String detail(@PathVariable Long id, Model model) {
		Order order = service.getOrder(id);
		model.addAttribute("order", order);
		model.addAttribute("history", service.history(id));
		return "order-detail";
	}

	@PostMapping("/orders/{id}/status")
	public String changeStatus(@PathVariable Long id, @RequestParam OrderStatus status, @RequestParam long version,
			HttpSession session, RedirectAttributes redirect) {
		try {
			service.changeStatus(id, status, version, (String) session.getAttribute(LoginInterceptor.SESSION_USER));
			redirect.addFlashAttribute("message", "상태를 '" + status.getLabel() + "'(으)로 바꿨습니다");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/orders/" + id;
	}

	// ---------------------------------------------------------------- 마스터 데이터 화면

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
			form.put(BatteryService.routeKey(r.from(), r.to()), String.valueOf(r.days()));
		}
		form.put("shipCount", String.valueOf(m.shipCount()));
		form.put("shipDailyCost", new BigDecimal(m.shipDailyCostWon())
				.divide(BigDecimal.valueOf(BatteryService.WON_PER_EOK)).stripTrailingZeros().toPlainString());
		return form;
	}

	// ---------------------------------------------------------------- JSON API

	@GetMapping("/api/master")
	@ResponseBody
	public Master masterJson() {
		return service.currentMaster();
	}

	@PostMapping("/api/simulate")
	@ResponseBody
	public ResponseEntity<?> simulate(@RequestBody SimulateRequest request) {
		try {
			return ResponseEntity.ok(service.simulate(request.orders()));
		} catch (BusinessException e) {
			return apiError(e);
		}
	}

	@PostMapping("/api/orders")
	@ResponseBody
	public ResponseEntity<?> confirm(@RequestBody ConfirmRequest request, HttpSession session) {
		try {
			Order order = service.confirm(request.orders(), request.routeType(), request.masterVersion(),
					request.memo(), (String) session.getAttribute(LoginInterceptor.SESSION_USER));
			return ResponseEntity.ok(Map.of("id", order.getId()));
		} catch (BusinessException e) {
			return apiError(e);
		}
	}

	private static ResponseEntity<Map<String, Object>> apiError(BusinessException e) {
		HttpStatus status = e.isConflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status).body(Map.of("error", e.getMessage(), "conflict", e.isConflict()));
	}
}
