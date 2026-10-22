package com.example.demo.controller.dto;

import com.example.demo.entity.OrderStatus;

/** 상태 변경 요청과 결과 */
public final class StatusChange {

	private StatusChange() {
	}

	/** version 은 화면에서 본 시점의 주문 version (그 사이 바뀌었으면 거부된다) */
	public record Request(OrderStatus status, long version) {
	}

	/** 취소하면 주문이 삭제되므로 deleted=true 이고 order 는 null 이다. */
	public record Result(boolean deleted, OrderDetail order) {
	}
}
