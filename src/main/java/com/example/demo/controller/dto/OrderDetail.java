package com.example.demo.controller.dto;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatusHistory;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 주문 상세 화면용 응답. version 은 상태를 바꿀 때 그대로 돌려보내(동시 변경 확인),
 * plan 은 확정 당시 저장한 계획(Master 와 경로)이다.
 */
public record OrderDetail(Long id, StatusInfo status, LocalDateTime confirmedAt, String createdBy, String routeType,
		String routeLabel, String memo, String customerName, int totalK, int totalDays, String totalCostEok, long version,
		List<StatusInfo> next, List<HistoryItem> history, JsonNode plan) {

	public record HistoryItem(LocalDateTime changedAt, StatusInfo fromStatus, StatusInfo toStatus,
			String changedBy) {

		static HistoryItem of(OrderStatusHistory h) {
			return new HistoryItem(h.getChangedAt(), StatusInfo.of(h.getFromStatus()), StatusInfo.of(h.getToStatus()),
					h.getChangedBy());
		}
	}

	public static OrderDetail of(Order o, List<OrderStatusHistory> history, ObjectMapper json) {
		return new OrderDetail(o.getId(), StatusInfo.of(o.getStatus()), o.getConfirmedAt(), o.getCreatedBy(),
				o.getRouteType(), o.getRouteLabel(), o.getMemo(), o.getCustomerName(), o.getTotalK(), o.getTotalDays(),
				o.getTotalCostEok(), o.getVersion(), o.getStatus().next().stream().map(StatusInfo::of).toList(),
				history.stream().map(HistoryItem::of).toList(), json.readTree(o.getPlanJson()));
	}
}
