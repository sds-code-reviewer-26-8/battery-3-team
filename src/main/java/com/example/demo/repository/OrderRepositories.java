package com.example.demo.repository;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public final class OrderRepositories {

	private OrderRepositories() {
	}

	public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

		List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
	}

	public interface OrderRepository extends JpaRepository<Order, Long> {

		long countByStatus(OrderStatus status);

		List<Order> findByStatusOrderByIdDesc(OrderStatus status);

		List<Order> findAllByOrderByIdDesc();
	}
}
