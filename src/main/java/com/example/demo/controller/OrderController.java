package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.dto.OrderDetail;
import com.example.demo.controller.dto.OrderList;
import com.example.demo.controller.dto.StatusChange;
import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.service.OrderService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	public record ConfirmRequest(Map<Country, Integer> orders, String routeType, long masterVersion, String memo) {
	}

	private final OrderService orderService;
	private final ObjectMapper json;

	public OrderController(OrderService orderService, ObjectMapper json) {
		this.orderService = orderService;
		this.json = json;
	}

	@GetMapping
	public OrderList list(@RequestParam(required = false) OrderStatus status) {
		return OrderList.of(orderService.listOrders(status), orderService.countByStatus());
	}

	@GetMapping("/{id}")
	public OrderDetail detail(@PathVariable Long id) {
		return detailOf(id);
	}

	@PostMapping
	public Map<String, Long> confirm(@RequestBody ConfirmRequest request, HttpSession session) {
		Order order = orderService.confirm(request.orders(), request.routeType(), request.masterVersion(),
				request.memo(), (String) session.getAttribute(LoginInterceptor.SESSION_USER));
		return Map.of("id", order.getId());
	}

	@PostMapping("/{id}/status")
	public StatusChange.Result changeStatus(@PathVariable Long id, @RequestBody StatusChange.Request request,
			HttpSession session) {
		orderService.changeStatus(id, request.status(), request.version(),
				(String) session.getAttribute(LoginInterceptor.SESSION_USER));
		if (request.status() == OrderStatus.CANCELED) {
			return new StatusChange.Result(true, null);
		}
		return new StatusChange.Result(false, detailOf(id));
	}

	private OrderDetail detailOf(Long id) {
		return OrderDetail.of(orderService.getOrder(id), orderService.history(id), json);
	}
}
