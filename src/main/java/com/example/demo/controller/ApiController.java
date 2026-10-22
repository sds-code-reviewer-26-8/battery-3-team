package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.service.BusinessException;
import com.example.demo.service.MasterService;
import com.example.demo.service.OrderService;
import com.example.demo.service.SimulationService;
import com.example.demo.simulation.Master;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ApiController {

	public record SimulateRequest(Map<Country, Integer> orders) {
	}

	public record ConfirmRequest(Map<Country, Integer> orders, String routeType, long masterVersion, String memo) {
	}

	private final MasterService masterService;
	private final SimulationService simulationService;
	private final OrderService orderService;

	public ApiController(MasterService masterService, SimulationService simulationService,
			OrderService orderService) {
		this.masterService = masterService;
		this.simulationService = simulationService;
		this.orderService = orderService;
	}

	@GetMapping("/api/master")
	@ResponseBody
	public Master masterJson() {
		return masterService.currentMaster();
	}

	@PostMapping("/api/simulate")
	@ResponseBody
	public ResponseEntity<?> simulate(@RequestBody SimulateRequest request) {
		try {
			return ResponseEntity.ok(simulationService.simulate(request.orders()));
		} catch (BusinessException e) {
			return apiError(e);
		}
	}

	@PostMapping("/api/orders")
	@ResponseBody
	public ResponseEntity<?> confirm(@RequestBody ConfirmRequest request, HttpSession session) {
		try {
			Order order = orderService.confirm(request.orders(), request.routeType(), request.masterVersion(),
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
