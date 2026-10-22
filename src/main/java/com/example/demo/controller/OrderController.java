package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.dto.OrderDetail;
import com.example.demo.controller.dto.OrderList;
import com.example.demo.controller.dto.StatusChange;
import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.service.BusinessException;
import com.example.demo.service.OrderService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	/** requiredDays 는 일부러 JsonNode 로 받는다. Integer 로 받으면 5.5 나 "10" 을 조용히 5, 10 으로 바꿔 받아들인다. */
	public record ConfirmRequest(Map<Country, Integer> orders, String routeType, long masterVersion, String memo,
			JsonNode requiredDays, String customerName) {
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
				request.memo(), requiredDaysOf(request.requiredDays()), request.customerName(),
				(String) session.getAttribute(LoginInterceptor.SESSION_USER));
		return Map.of("id", order.getId());
	}

	/** 없거나 null 이면 '가능한 빨리'(null). 있으면 정수여야 한다(소수·문자열은 거부). 범위(5~50)는 서비스가 확인한다. */
	private static Integer requiredDaysOf(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber() || !node.canConvertToInt()) {
			throw new BusinessException("필요 납기일은 정수로 입력하세요");
		}
		return node.intValue();
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

	@PutMapping("/{id}/customer")
	public OrderDetail updateCustomer(@PathVariable Long id, @RequestBody JsonNode body) {
		// version 은 화면에서 본 시점의 주문 version. customerName 키가 없으면 실수로 이름이 지워지지 않도록 거부한다
		// (지우려면 빈 문자열이나 null 을 명시해서 보낸다).
		JsonNode version = body.get("version");
		if (version == null || !version.isIntegralNumber()) {
			throw new BusinessException("주문 version 이 필요합니다");
		}
		if (!body.has("customerName")) {
			throw new BusinessException("customerName 이 필요합니다. 지우려면 빈 문자열을 보내세요");
		}
		JsonNode name = body.get("customerName");
		if (!name.isNull() && !name.isString()) {
			throw new BusinessException("customerName 은 문자열이어야 합니다");
		}
		orderService.updateCustomer(id, name.isNull() ? null : name.asString(), version.asLong());
		return detailOf(id);
	}

	private OrderDetail detailOf(Long id) {
		return OrderDetail.of(orderService.getOrder(id), orderService.history(id), json);
	}
}
