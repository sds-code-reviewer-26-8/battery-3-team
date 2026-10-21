package com.example.demo.service;

import com.example.demo.entity.Country;
import com.example.demo.entity.Factory;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Route;
import com.example.demo.entity.Setting;
import com.example.demo.repository.MasterRepositories.FactoryRepository;
import com.example.demo.repository.MasterRepositories.RouteRepository;
import com.example.demo.repository.MasterRepositories.SettingRepository;
import com.example.demo.repository.OrderRepositories.OrderRepository;
import com.example.demo.repository.OrderRepositories.OrderStatusHistoryRepository;
import com.example.demo.simulation.Master;
import com.example.demo.simulation.Plan;
import com.example.demo.simulation.Planner;
import com.example.demo.simulation.StoredPlan;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class BatteryService {

	public static final long WON_PER_EOK = 100_000_000L;
	private static final int MAX_SHIPS = 4;
	private static final int MAX_MEMO = 1000;

	/** 시뮬레이션 결과. master 는 이 계산에 쓴 마스터 값(확정 시 비교·저장에 쓴다). */
	public record SimulationResult(Master master, Map<Country, Integer> orders, Map<Country, Integer> visits,
			List<Plan> options) {
	}

	private final FactoryRepository factories;
	private final RouteRepository routes;
	private final SettingRepository settings;
	private final OrderRepository orders;
	private final OrderStatusHistoryRepository histories;
	private final ObjectMapper json;

	public BatteryService(FactoryRepository factories, RouteRepository routes, SettingRepository settings,
			OrderRepository orders, OrderStatusHistoryRepository histories, ObjectMapper json) {
		this.factories = factories;
		this.routes = routes;
		this.settings = settings;
		this.orders = orders;
		this.histories = histories;
		this.json = json;
	}

	// ---------------------------------------------------------------- 마스터 데이터

	/** 마스터 데이터가 비어 있으면 초기값을 넣는다. */
	@Transactional
	public void initDefaults() {
		if (settings.count() > 0) {
			return;
		}
		factories.save(new Factory(Country.CHINA, 400, 4000, 700));
		factories.save(new Factory(Country.JAPAN, 800, 2000, 900));
		factories.save(new Factory(Country.VIETNAM, 200, 10000, 550));
		factories.save(new Factory(Country.INDONESIA, 300, 7000, 450));

		routes.save(new Route(Country.KOREA, Country.CHINA, 1));
		routes.save(new Route(Country.KOREA, Country.JAPAN, 2));
		routes.save(new Route(Country.KOREA, Country.VIETNAM, 5));
		routes.save(new Route(Country.KOREA, Country.INDONESIA, 7));
		routes.save(new Route(Country.CHINA, Country.JAPAN, 2));
		routes.save(new Route(Country.CHINA, Country.VIETNAM, 4));
		routes.save(new Route(Country.CHINA, Country.INDONESIA, 6));
		routes.save(new Route(Country.JAPAN, Country.VIETNAM, 5));
		routes.save(new Route(Country.JAPAN, Country.INDONESIA, 7));
		routes.save(new Route(Country.VIETNAM, Country.INDONESIA, 2));

		settings.save(new Setting(3, WON_PER_EOK));
	}

	@Transactional(readOnly = true)
	public Master currentMaster() {
		Setting s = settings.findById(Setting.SINGLETON_ID)
				.orElseThrow(() -> new IllegalStateException("마스터 데이터가 초기화되지 않았습니다"));
		List<Master.FactoryParam> fs = factories.findAll().stream()
				.sorted(Comparator.comparing(Factory::getCountry))
				.map(f -> new Master.FactoryParam(f.getCountry(), f.getMaxDaily(), f.getStockLimit(), f.getUnitCost()))
				.toList();
		List<Master.RouteParam> rs = routes.findAll().stream()
				.sorted(Comparator.comparing(Route::getFromCountry).thenComparing(Route::getToCountry))
				.map(r -> new Master.RouteParam(r.getFromCountry(), r.getToCountry(), r.getDays()))
				.toList();
		return new Master(s.getMasterVersion(), s.getShipCount(), s.getShipDailyCostWon(), fs, rs);
	}

	/** 항로 입력칸 이름 (from, to 는 ordinal 순) */
	public static String routeKey(Country a, Country b) {
		return a.ordinal() < b.ordinal() ? "days_" + a + "_" + b : "days_" + b + "_" + a;
	}

	/**
	 * 폼 값을 검증하고 저장한다. 올바르지 않은 값이 하나라도 있으면 아무것도 저장하지 않고
	 * 이유를 모아 {@link BusinessException} 으로 알린다.
	 */
	@Transactional
	public void updateMaster(Map<String, String> form) {
		List<String> errors = new ArrayList<>();

		int shipCount = parseInt(form, "shipCount", "배 척수", 1, MAX_SHIPS, errors);
		long shipCostWon = 0;
		String costText = form.getOrDefault("shipDailyCost", "").trim();
		try {
			BigDecimal eok = new BigDecimal(costText);
			BigDecimal won = eok.multiply(BigDecimal.valueOf(WON_PER_EOK));
			if (eok.signum() <= 0) {
				errors.add("척당 일 운항비는 0보다 커야 합니다");
			} else if (won.stripTrailingZeros().scale() > 0) {
				errors.add("척당 일 운항비는 원 단위로 떨어지는 값이어야 합니다");
			} else {
				shipCostWon = won.longValueExact();
			}
		} catch (NumberFormatException | ArithmeticException e) {
			errors.add("척당 일 운항비(억 원)는 숫자로 입력하세요");
		}

		Map<Country, int[]> factoryValues = new EnumMap<>(Country.class);
		Map<Country, Long> unitCosts = new EnumMap<>(Country.class);
		for (Country c : Country.factories()) {
			String name = c.getLabel();
			int max = parseInt(form, "maxDaily_" + c, name + " 하루 최대 생산량", 1, 1_000_000, errors);
			int limit = parseInt(form, "stockLimit_" + c, name + " 재고 상한", 1, 1_000_000, errors);
			int cost = parseInt(form, "unitCost_" + c, name + " 셀당 원가", 1, 100_000_000, errors);
			factoryValues.put(c, new int[] {max, limit});
			unitCosts.put(c, (long) cost);
		}

		List<Country> all = List.of(Country.values());
		Map<String, Integer> days = new HashMap<>();
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				String key = routeKey(all.get(i), all.get(j));
				String label = all.get(i).getLabel() + "-" + all.get(j).getLabel() + " 항해 일수";
				days.put(key, parseInt(form, key, label, 1, 365, errors));
			}
		}

		if (!errors.isEmpty()) {
			throw new BusinessException(errors);
		}

		for (Country c : Country.factories()) {
			Factory f = factories.findById(c).orElseThrow();
			f.setMaxDaily(factoryValues.get(c)[0]);
			f.setStockLimit(factoryValues.get(c)[1]);
			f.setUnitCost(unitCosts.get(c));
		}
		for (Route r : routes.findAll()) {
			r.setDays(days.get(routeKey(r.getFromCountry(), r.getToCountry())));
		}
		Setting s = settings.findById(Setting.SINGLETON_ID).orElseThrow();
		s.setShipCount(shipCount);
		s.setShipDailyCostWon(shipCostWon);
		s.bumpMasterVersion();
	}

	private static int parseInt(Map<String, String> form, String key, String label, int min, int max,
			List<String> errors) {
		String text = form.getOrDefault(key, "").trim().replace(",", "");
		try {
			int v = Integer.parseInt(text);
			if (v < min || v > max) {
				errors.add("%s은(는) %d 이상 %d 이하의 정수여야 합니다".formatted(label, min, max));
			}
			return v;
		} catch (NumberFormatException e) {
			errors.add("%s은(는) 정수로 입력하세요".formatted(label));
			return min;
		}
	}

	// ---------------------------------------------------------------- 시뮬레이션

	public SimulationResult simulate(Map<Country, Integer> rawOrders) {
		Map<Country, Integer> validated = validateOrders(rawOrders);
		Master master = currentMaster();
		return new SimulationResult(master, validated, Planner.visits(master, validated),
				Planner.plan(master, validated));
	}

	private static Map<Country, Integer> validateOrders(Map<Country, Integer> raw) {
		Map<Country, Integer> validated = new EnumMap<>(Country.class);
		long total = 0;
		for (Country c : Country.factories()) {
			Integer q = raw == null ? null : raw.get(c);
			int v = q == null ? 0 : q;
			if (v < 0) {
				throw new BusinessException(c.getLabel() + " 주문 수량은 0 이상이어야 합니다");
			}
			validated.put(c, v);
			total += v;
		}
		if (total <= 0) {
			throw new BusinessException("총 생산 필요 수는 1K 이상이어야 합니다");
		}
		if (total > Planner.MAX_TOTAL_K) {
			throw new BusinessException("총 생산 필요 수는 %,dK 이하여야 합니다".formatted(Planner.MAX_TOTAL_K));
		}
		return validated;
	}

	// ---------------------------------------------------------------- 주문

	/**
	 * 주문을 확정한다. 서버가 같은 입력으로 다시 계산해 계획을 만들고(클라이언트 값은 믿지 않는다),
	 * 시뮬레이션 이후 마스터 데이터가 바뀌었으면 확정을 거부한다.
	 */
	@Transactional
	public Order confirm(Map<Country, Integer> requested, String routeType, long masterVersion, String memo,
			String user) {
		SimulationResult result = simulate(requested);
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

		StoredPlan stored = new StoredPlan(result.master(), result.orders(), result.visits(), plan);
		int totalK = result.orders().values().stream().mapToInt(Integer::intValue).sum();
		LocalDateTime now = LocalDateTime.now();
		Order order = orders.save(new Order(now, user, totalK, plan.type(), plan.label(), plan.totalDays(),
				plan.productionCost(), plan.shippingCost(), plan.totalCost(), note, json.writeValueAsString(stored)));
		histories.save(new OrderStatusHistory(order.getId(), null, OrderStatus.CONFIRMED, user, now));
		return order;
	}

	@Transactional(readOnly = true)
	public List<Order> listOrders(OrderStatus status) {
		return status == null ? orders.findAllByOrderByIdDesc() : orders.findByStatusOrderByIdDesc(status);
	}

	@Transactional(readOnly = true)
	public Map<OrderStatus, Long> countByStatus() {
		Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
		for (OrderStatus s : OrderStatus.values()) {
			counts.put(s, orders.countByStatus(s));
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public Order getOrder(Long id) {
		return orders.findById(id).orElseThrow(() -> new BusinessException("주문을 찾을 수 없습니다"));
	}

	@Transactional(readOnly = true)
	public List<OrderStatusHistory> history(Long orderId) {
		return histories.findByOrderIdOrderByIdAsc(orderId);
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
			orders.saveAndFlush(order);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw conflict();
		}
		histories.save(new OrderStatusHistory(id, from, target, user, LocalDateTime.now()));
		if (target == OrderStatus.CANCELED) {
			orders.delete(order);
		}
	}

	private static BusinessException conflict() {
		return new BusinessException("다른 사용자가 먼저 이 주문을 변경했습니다. 화면을 새로 고쳐 현재 상태를 확인한 뒤 다시 시도해 주세요", true);
	}
}
