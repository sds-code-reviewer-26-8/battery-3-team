package com.example.demo.service;

import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.service.SimulationService.SimulationResult;
import com.example.demo.simulation.Plan;
import com.example.demo.simulation.StoredPlan;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {

	private static final int MAX_MEMO = 1000;
	private static final int MAX_CUSTOMER = 100;

	private final SimulationService simulationService;
	private final OrderRepository orderRepository;
	private final OrderStatusHistoryRepository orderStatusHistoryRepository;
	private final ObjectMapper json;

	public OrderService(SimulationService simulationService, OrderRepository orderRepository,
			OrderStatusHistoryRepository orderStatusHistoryRepository, ObjectMapper json) {
		this.simulationService = simulationService;
		this.orderRepository = orderRepository;
		this.orderStatusHistoryRepository = orderStatusHistoryRepository;
		this.json = json;
	}

	/**
	 * 주문을 확정한다. 서버가 같은 입력으로 다시 계산해 계획을 만들고(클라이언트 값은 믿지 않는다),
	 * 시뮬레이션 이후 마스터 데이터가 바뀌었으면 확정을 거부한다.
	 */
	@Transactional
	public Order confirm(Map<Country, Integer> requested, String routeType, long masterVersion, String memo,
			String customerName, String user) {
		SimulationResult result = simulationService.simulate(requested);
		if (result.master().version() != masterVersion) {
			throw new BusinessException("시뮬레이션 이후 마스터 데이터가 바뀌었습니다. 다시 시뮬레이션해 주세요", true);
		}
		Plan plan = result.options().stream().filter(p -> p.type().equals(routeType)).findFirst()
				.orElseThrow(() -> new BusinessException("선택한 경로를 찾을 수 없습니다"));
		String note = memo == null ? null : memo.strip();
		if (note != null && note.length() > MAX_MEMO) {
			throw new BusinessException("메모는 %d자 이하로 입력하세요".formatted(MAX_MEMO));
		}
		if (note != null && note.isEmpty()) {
			note = null;
		}
		String customer = normalizeCustomer(customerName);

		StoredPlan stored = new StoredPlan(result.master(), result.orders(), result.visits(), plan);
		int totalK = result.orders().values().stream().mapToInt(Integer::intValue).sum();
		LocalDateTime now = LocalDateTime.now();
		Order newOrder = new Order(now, user, totalK, plan.type(), plan.label(), plan.totalDays(),
				plan.productionCost(), plan.shippingCost(), plan.totalCost(), note, json.writeValueAsString(stored));
		newOrder.setCustomerName(customer);
		Order order = orderRepository.save(newOrder);
		orderStatusHistoryRepository.save(new OrderStatusHistory(order.getId(), null, OrderStatus.CONFIRMED, user, now));
		return order;
	}

	@Transactional(readOnly = true)
	public List<Order> listOrders(OrderStatus status) {
		return status == null ? orderRepository.findAllByOrderByIdDesc() : orderRepository.findByStatusOrderByIdDesc(status);
	}

	@Transactional(readOnly = true)
	public Map<OrderStatus, Long> countByStatus() {
		Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
		for (OrderStatus s : OrderStatus.values()) {
			counts.put(s, orderRepository.countByStatus(s));
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public Order getOrder(Long id) {
		return orderRepository.findById(id).orElseThrow(() -> BusinessException.notFound("주문을 찾을 수 없습니다"));
	}

	@Transactional(readOnly = true)
	public List<OrderStatusHistory> history(Long orderId) {
		return orderStatusHistoryRepository.findByOrderIdOrderByIdAsc(orderId);
	}

	/**
	 * 상태를 바꾼다. 화면에서 본 시점의 version 과 다르면(= 그 사이 다른 사람이 바꿈) 거부한다.
	 */
	@Transactional
	public void changeStatus(Long id, OrderStatus target, long expectedVersion, String user) {
		Order order = getOrder(id);
		if (order.getVersion() != expectedVersion) {
			throw conflict();
		}
		if (!order.getStatus().canChangeTo(target)) {
			throw new BusinessException("%s 상태에서는 %s(으)로 바꿀 수 없습니다"
					.formatted(order.getStatus().getLabel(), target.getLabel()));
		}
		OrderStatus from = order.getStatus();
		order.setStatus(target);
		try {
			orderRepository.saveAndFlush(order);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw conflict();
		}
		orderStatusHistoryRepository.save(new OrderStatusHistory(id, from, target, user, LocalDateTime.now()));
		if (target == OrderStatus.CANCELED) {
			orderRepository.delete(order);
		}
	}

	/**
	 * 고객사 이름을 고친다. 확정·진행 상태에서만 가능하고(완료는 거부, 취소로 삭제된 주문은 404),
	 * version 이 다르면 충돌로 거부한다. 수정 이력은 남기지 않는다.
	 */
	@Transactional
	public void updateCustomer(Long id, String customerName, long expectedVersion) {
		Order order = getOrder(id);
		if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.IN_PROGRESS) {
			throw new BusinessException("완료된 주문은 고객사 이름을 수정할 수 없습니다");
		}
		if (order.getVersion() != expectedVersion) {
			throw conflict();
		}
		order.setCustomerName(normalizeCustomer(customerName));
		try {
			orderRepository.saveAndFlush(order);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw conflict();
		}
	}

	/** 앞뒤 공백을 없애고, 비었으면 null, 100자를 넘으면 거부한다. */
	private static String normalizeCustomer(String name) {
		String v = name == null ? null : name.strip();
		if (v != null && v.length() > MAX_CUSTOMER) {
			throw new BusinessException("고객사 이름은 %d자 이하로 입력하세요".formatted(MAX_CUSTOMER));
		}
		return v == null || v.isEmpty() ? null : v;
	}

	private static BusinessException conflict() {
		return new BusinessException("다른 사용자가 먼저 이 주문을 변경했습니다. 화면을 새로 고쳐 현재 상태를 확인한 뒤 다시 시도해 주세요", true);
	}
}
