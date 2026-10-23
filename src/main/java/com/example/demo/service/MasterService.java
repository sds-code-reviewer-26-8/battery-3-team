package com.example.demo.service;

import com.example.demo.entity.Country;
import com.example.demo.entity.Factory;
import com.example.demo.entity.Route;
import com.example.demo.entity.Setting;
import com.example.demo.repository.FactoryRepository;
import com.example.demo.repository.RouteRepository;
import com.example.demo.repository.SettingRepository;
import com.example.demo.simulation.Master;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterService {

	public static final long WON_PER_EOK = 100_000_000L;
	public static final long DEFAULT_OPERATING_COST_WON = WON_PER_EOK;
	private static final int MAX_SHIPS = 4;

	private final FactoryRepository factoryRepository;
	private final RouteRepository routeRepository;
	private final SettingRepository settingRepository;

	public MasterService(FactoryRepository factoryRepository, RouteRepository routeRepository,
			SettingRepository settingRepository) {
		this.factoryRepository = factoryRepository;
		this.routeRepository = routeRepository;
		this.settingRepository = settingRepository;
	}

	/** 마스터 데이터가 비어 있으면 초기값을 넣는다. */
	@Transactional
	public void initDefaults() {
		if (settingRepository.count() > 0) {
			return;
		}
		factoryRepository.save(new Factory(Country.CHINA, 400, 4000, 700, DEFAULT_OPERATING_COST_WON));
		factoryRepository.save(new Factory(Country.JAPAN, 800, 2000, 900, DEFAULT_OPERATING_COST_WON));
		factoryRepository.save(new Factory(Country.VIETNAM, 200, 10000, 550, DEFAULT_OPERATING_COST_WON));
		factoryRepository.save(new Factory(Country.INDONESIA, 300, 7000, 450, DEFAULT_OPERATING_COST_WON));

		routeRepository.save(new Route(Country.KOREA, Country.CHINA, 1));
		routeRepository.save(new Route(Country.KOREA, Country.JAPAN, 2));
		routeRepository.save(new Route(Country.KOREA, Country.VIETNAM, 5));
		routeRepository.save(new Route(Country.KOREA, Country.INDONESIA, 7));
		routeRepository.save(new Route(Country.CHINA, Country.JAPAN, 2));
		routeRepository.save(new Route(Country.CHINA, Country.VIETNAM, 4));
		routeRepository.save(new Route(Country.CHINA, Country.INDONESIA, 6));
		routeRepository.save(new Route(Country.JAPAN, Country.VIETNAM, 5));
		routeRepository.save(new Route(Country.JAPAN, Country.INDONESIA, 7));
		routeRepository.save(new Route(Country.VIETNAM, Country.INDONESIA, 2));

		settingRepository.save(new Setting(3, WON_PER_EOK));
	}

	@Transactional(readOnly = true)
	public Master currentMaster() {
		Setting s = settingRepository.findById(Setting.SINGLETON_ID)
				.orElseThrow(() -> new IllegalStateException("마스터 데이터가 초기화되지 않았습니다"));
		List<Master.FactoryParam> fs = factoryRepository.findAll().stream()
				.sorted(Comparator.comparing(Factory::getCountry))
				.map(f -> new Master.FactoryParam(f.getCountry(), f.getMaxDaily(), f.getStockLimit(), f.getUnitCost(),
						f.getDailyOperatingCostWon() == null ? DEFAULT_OPERATING_COST_WON : f.getDailyOperatingCostWon()))
				.toList();
		List<Master.RouteParam> rs = routeRepository.findAll().stream()
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
		Map<Country, Long> operatingCosts = new EnumMap<>(Country.class);
		for (Country c : Country.factories()) {
			String name = c.getLabel();
			int max = parseInt(form, "maxDaily_" + c, name + " 하루 최대 생산량", 1, 1_000_000, errors);
			int limit = parseInt(form, "stockLimit_" + c, name + " 재고 상한", 1, 1_000_000, errors);
			int cost = parseInt(form, "unitCost_" + c, name + " 셀당 원가", 1, 100_000_000, errors);
			operatingCosts.put(c, parseOperatingCost(form, "operatingCost_" + c, name + " 하루 운영비", errors));
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
			Factory f = factoryRepository.findById(c).orElseThrow();
			f.setMaxDaily(factoryValues.get(c)[0]);
			f.setStockLimit(factoryValues.get(c)[1]);
			f.setUnitCost(unitCosts.get(c));
			f.setDailyOperatingCostWon(operatingCosts.get(c));
		}
		for (Route r : routeRepository.findAll()) {
			r.setDays(days.get(routeKey(r.getFromCountry(), r.getToCountry())));
		}
		Setting s = settingRepository.findById(Setting.SINGLETON_ID).orElseThrow();
		s.setShipCount(shipCount);
		s.setShipDailyCostWon(shipCostWon);
		s.bumpMasterVersion();
		// 커밋 때가 아니라 여기서 반영해야, 동시에 저장한 다른 사용자와 부딪힌 것을 잡아 충돌(409)로 알릴 수 있다
		try {
			settingRepository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new BusinessException("다른 사용자가 먼저 마스터 데이터를 변경했습니다. 화면을 새로 고쳐 현재 값을 확인한 뒤 다시 시도해 주세요", true);
		}
	}

	/** 억 원 단위 소수 입력(0 이상, 원 단위로 떨어져야 함)을 원으로 바꾼다. 틀리면 errors 에 이유를 모은다. */
	private static long parseOperatingCost(Map<String, String> form, String key, String label, List<String> errors) {
		String text = form.getOrDefault(key, "").trim().replace(",", "");
		try {
			BigDecimal won = new BigDecimal(text).multiply(BigDecimal.valueOf(WON_PER_EOK));
			if (won.signum() < 0) {
				errors.add("%s(억 원)은(는) 0 이상이어야 합니다".formatted(label));
			} else if (won.stripTrailingZeros().scale() > 0) {
				errors.add("%s(억 원)은(는) 원 단위로 떨어지는 값이어야 합니다".formatted(label));
			} else if (won.compareTo(BigDecimal.valueOf(Long.MAX_VALUE / 1_000_000L)) > 0) {
				errors.add("%s(억 원)이(가) 너무 큽니다".formatted(label));
			} else {
				return won.longValueExact();
			}
		} catch (NumberFormatException e) {
			errors.add("%s(억 원)은(는) 숫자로 입력하세요".formatted(label));
		}
		return 0;
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
}
