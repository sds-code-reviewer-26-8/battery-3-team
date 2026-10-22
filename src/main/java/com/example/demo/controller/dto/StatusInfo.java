package com.example.demo.controller.dto;

import com.example.demo.entity.OrderStatus;

/** 화면에 보여 줄 주문 상태 (코드와 한글 이름) */
public record StatusInfo(String name, String label) {

	public static StatusInfo of(OrderStatus status) {
		return status == null ? null : new StatusInfo(status.name(), status.getLabel());
	}
}
