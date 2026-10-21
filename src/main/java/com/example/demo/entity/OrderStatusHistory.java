package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long orderId;

	/** 최초 확정 시에는 null */
	@Enumerated(EnumType.STRING)
	private OrderStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus toStatus;

	@Column(nullable = false)
	private String changedBy;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	protected OrderStatusHistory() {
	}

	public OrderStatusHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus, String changedBy,
			LocalDateTime changedAt) {
		this.orderId = orderId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public OrderStatus getFromStatus() {
		return fromStatus;
	}

	public OrderStatus getToStatus() {
		return toStatus;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}
}
