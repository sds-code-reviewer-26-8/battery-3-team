package com.example.demo.controller.dto;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 주문 목록 화면용 응답: 목록, 상태별 건수, 전체 건수, 상태 칩 목록 */
public record OrderList(List<Item> orders, Map<String, Long> counts, long total, List<StatusInfo> statuses) {

	public record Item(Long id, LocalDateTime confirmedAt, int totalK, String routeLabel, int totalDays,
			String totalCostEok, StatusInfo status, String createdBy) {

		static Item of(Order o) {
			return new Item(o.getId(), o.getConfirmedAt(), o.getTotalK(), o.getRouteLabel(), o.getTotalDays(),
					o.getTotalCostEok(), StatusInfo.of(o.getStatus()), o.getCreatedBy());
		}
	}

	public static OrderList of(List<Order> orders, Map<OrderStatus, Long> countByStatus) {
		Map<String, Long> counts = new LinkedHashMap<>();
		long total = 0;
		for (OrderStatus s : OrderStatus.values()) {
			long n = countByStatus.getOrDefault(s, 0L);
			counts.put(s.name(), n);
			total += n;
		}
		return new OrderList(orders.stream().map(Item::of).toList(), counts, total,
				Arrays.stream(OrderStatus.values()).map(StatusInfo::of).toList());
	}
}
