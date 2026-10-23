package com.example.demo.stress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.demo.entity.Country;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.service.BusinessException;
import com.example.demo.service.OrderService;
import com.example.demo.service.SimulationService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 동시에 많은 요청이 몰릴 때 데이터가 어긋나지 않는지 본다(낙관적 락, 주문·이력 일관성, 마스터 버전).
 * 주문을 많이 만들기 때문에 끝나면 컨텍스트(메모리 DB)를 버려 다른 테스트에 영향을 주지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StressTests {

	private static final int THREADS = 16;

	@Autowired
	OrderService orderService;
	@Autowired
	SimulationService simulationService;
	@Autowired
	MockMvc mvc;

	private static Map<Country, Integer> smallOrder() {
		Map<Country, Integer> m = new EnumMap<>(Country.class);
		m.put(Country.CHINA, 300);
		m.put(Country.JAPAN, 200);
		return m;
	}

	private Order confirmOne() {
		long version = simulationService.simulate(smallOrder()).master().version();
		return orderService.confirm(smallOrder(), "COST", version, "stress", null, null, "stress");
	}

	/** 모든 작업을 같은 순간에 출발시키고 결과(또는 예외)를 모은다. */
	private <T> List<Object> runConcurrently(int count, Callable<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		try {
			CountDownLatch ready = new CountDownLatch(count);
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Object>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					try {
						return task.call();
					} catch (Exception e) {
						return e;
					}
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> f : futures) {
				results.add(f.get(120, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void sameVersionStatusChangeSucceedsExactlyOnce() throws Exception {
		Order order = confirmOne();
		long id = order.getId();
		long version = order.getVersion();

		List<Object> results = runConcurrently(THREADS, () -> {
			orderService.changeStatus(id, OrderStatus.IN_PROGRESS, version, "stress");
			return "ok";
		});

		assertThat(results.stream().filter("ok"::equals)).hasSize(1);
		for (Object r : results) {
			if (!"ok".equals(r)) {
				assertThat(r).isInstanceOf(BusinessException.class);
				assertThat(((BusinessException) r).isConflict()).isTrue();
			}
		}
		assertThat(orderService.getOrder(id).getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
		assertThat(orderService.history(id)).hasSize(2);
	}

	@Test
	void concurrentCancelDeletesOrderOnceAndLeavesOneHistory() throws Exception {
		Order order = confirmOne();
		long id = order.getId();
		long version = order.getVersion();

		List<Object> results = runConcurrently(THREADS, () -> {
			orderService.changeStatus(id, OrderStatus.CANCELED, version, "stress");
			return "ok";
		});

		assertThat(results.stream().filter("ok"::equals)).hasSize(1);
		results.stream().filter(r -> !"ok".equals(r)).forEach(r -> assertThat(r).isInstanceOf(BusinessException.class));
		assertThatThrownBy(() -> orderService.getOrder(id)).isInstanceOf(BusinessException.class);
		assertThat(orderService.history(id)).hasSize(2);
	}

	@Test
	void manyConcurrentConfirmsCreateDistinctConsistentOrders() throws Exception {
		int before = orderService.listOrders(null).size();

		List<Object> results = runConcurrently(THREADS * 2, this::confirmOne);

		List<Long> ids = new ArrayList<>();
		for (Object r : results) {
			assertThat(r).as("확정 중 예외: %s", r).isInstanceOf(Order.class);
			ids.add(((Order) r).getId());
		}
		assertThat(ids).doesNotHaveDuplicates();
		assertThat(orderService.listOrders(null)).hasSize(before + THREADS * 2);
		for (Long id : ids) {
			assertThat(orderService.history(id)).hasSize(1);
			assertThat(orderService.getOrder(id).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
		}
		long confirmed = orderService.countByStatus().get(OrderStatus.CONFIRMED);
		assertThat(confirmed).isGreaterThanOrEqualTo(THREADS * 2L);
	}

	@Test
	void concurrentSimulationsGiveIdenticalResults() throws Exception {
		var expected = simulationService.simulate(smallOrder());

		List<Object> results = runConcurrently(THREADS * 2, () -> simulationService.simulate(smallOrder()));

		for (Object r : results) {
			assertThat(r).isInstanceOf(SimulationService.SimulationResult.class);
			var actual = (SimulationService.SimulationResult) r;
			assertThat(actual.master()).isEqualTo(expected.master());
			assertThat(actual.visits()).isEqualTo(expected.visits());
			for (int i = 0; i < expected.options().size(); i++) {
				var e = expected.options().get(i);
				var a = actual.options().get(i);
				assertThat(a.type()).isEqualTo(e.type());
				assertThat(a.totalDays()).isEqualTo(e.totalDays());
				assertThat(a.totalCost()).isEqualTo(e.totalCost());
			}
		}
	}

	@Test
	void repeatedAndConcurrentMasterSavesNeverBreakTheMaster() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/login").param("username", "admin").param("password", "admin").session(session));
		String form = mvc.perform(get("/api/master/form").session(session)).andReturn().getResponse()
				.getContentAsString();
		long startVersion = simulationService.simulate(smallOrder()).master().version();

		// 같은 값으로 순서대로 30번 저장: 저장마다 버전이 하나씩 오른다
		int sequential = 30;
		for (int i = 0; i < sequential; i++) {
			assertThat(save(session, form)).isEqualTo(200);
		}
		long afterSequential = simulationService.simulate(smallOrder()).master().version();
		assertThat(afterSequential).isEqualTo(startVersion + sequential);

		// 동시에 저장해도 서버 오류(5xx)가 없고 값은 그대로, 버전은 줄지 않는다
		List<Object> codes = runConcurrently(8, () -> save(session, form));
		for (Object c : codes) {
			assertThat(c).as("동시 저장 결과: %s", c).isInstanceOf(Integer.class);
			assertThat((Integer) c).isLessThan(500);
		}
		assertThat(simulationService.simulate(smallOrder()).master().version()).isGreaterThan(afterSequential);
		String after = mvc.perform(get("/api/master/form").session(session)).andReturn().getResponse()
				.getContentAsString();
		assertThat(after).isEqualTo(form);
	}

	private int save(MockHttpSession session, String form) throws Exception {
		return mvc.perform(put("/api/master").session(session).contentType(MediaType.APPLICATION_JSON).content(form))
				.andReturn().getResponse().getStatus();
	}
}
