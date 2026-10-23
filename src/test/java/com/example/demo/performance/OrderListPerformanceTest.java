package com.example.demo.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Order;
import com.example.demo.performance.PerformanceSupport.Timing;
import com.example.demo.repository.OrderRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 주문이 많이 쌓였을 때 주문 목록·상세 조회의 성능 테스트.
 * 목록 API 는 페이지 처리 없이 전체 주문을 한 번에 돌려주므로 주문 수에 비례해 시간과 응답 크기가 늘어난다.
 * 주문 1,000건·5,000건에서 정해진 시간 안에 응답하는지 확인한다.
 *
 * <p>기준 시간은 개발 PC에서 잰 값(1,000건 목록 45~110ms, 5,000건 목록 140~230ms, 상세 3~6ms)의 10배 안팎으로 잡았다.
 * 다른 테스트와 데이터가 섞이지 않도록 이 클래스만 별도 인메모리 DB 를 쓰고, 끝나면 컨텍스트를 버린다.
 */
@Tag("performance")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:perf-orders;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderListPerformanceTest {

	private static final String ORDERS = """
			{"CHINA":3000,"JAPAN":3000,"VIETNAM":3000,"INDONESIA":3000}""";

	/** 주문 1,000건일 때 목록 조회(전체·상태 필터)의 중앙값 상한 */
	private static final long LIST_1000_MEDIAN_BUDGET_MS = 1_000;
	/** 주문 5,000건일 때 목록 조회(전체·상태 필터)의 중앙값 상한 */
	private static final long LIST_5000_MEDIAN_BUDGET_MS = 3_000;
	/** 주문이 5,000건 쌓여 있어도 주문 하나를 여는 시간의 중앙값 상한 */
	private static final long DETAIL_MEDIAN_BUDGET_MS = 300;
	/** 어느 한 번도 넘으면 안 되는 상한(첫 실행의 JIT·GC 흔들림까지 포함) */
	private static final long ANY_RUN_BUDGET_MS = 5_000;

	@Autowired
	MockMvc mvc;
	@Autowired
	OrderRepository orders;

	private MockHttpSession login() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/login").param("username", "admin").param("password", "admin").session(session))
				.andExpect(status().isFound());
		return session;
	}

	/**
	 * DB 의 주문을 정확히 count 건으로 맞춘다. 테스트 실행 순서와 상관없이 같은 상태에서 재도록, 매번 지우고 다시 채운다.
	 * 값은 실제로 확정한 주문 1건을 복제한다(계획 JSON 의 크기까지 실제와 같게). 복제한 주문 중 하나의 번호를 돌려준다.
	 */
	private long seedExactly(MockHttpSession s, int count) throws Exception {
		if (orders.count() == 0) {
			MvcResult sim = mvc.perform(post("/api/simulate").session(s).contentType(MediaType.APPLICATION_JSON)
					.content("{\"orders\":" + ORDERS + "}")).andReturn();
			int version = JsonPath.read(sim.getResponse().getContentAsString(), "$.master.version");
			mvc.perform(post("/api/orders").session(s).contentType(MediaType.APPLICATION_JSON)
					.content("{\"orders\":" + ORDERS + ",\"routeType\":\"COST\",\"masterVersion\":" + version
							+ ",\"memo\":\"perf\"}")).andExpect(status().isOk());
		}
		Order base = orders.findAllByOrderByIdDesc().get(0);
		orders.deleteAllInBatch();
		List<Order> copies = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			copies.add(new Order(LocalDateTime.now(), "admin", base.getTotalK(), base.getRouteType(), base.getRouteLabel(),
					base.getTotalDays(), base.getProductionCost(), base.getShippingCost(), base.getTotalCost(), "perf",
					null, base.getPlanJson(), base.getOperatingCost()));
		}
		List<Order> saved = orders.saveAll(copies);
		assertThat(orders.count()).isEqualTo(count);
		return saved.get(0).getId();
	}

	private void assertWithin(Timing timing, long medianBudgetMs) {
		assertThat(timing.medianMs()).as(timing.toString()).isLessThanOrEqualTo(medianBudgetMs);
		assertThat(timing.maxMs()).as(timing.toString()).isLessThanOrEqualTo(ANY_RUN_BUDGET_MS);
	}

	private void measureList(MockHttpSession s, int count, long budgetMs) throws Exception {
		seedExactly(s, count);

		// 빠르게 끝난 것이 오류 응답이어서가 아니도록, 모든 주문이 빠짐없이 내려오는지 먼저 확인한다
		MvcResult all = mvc.perform(get("/api/orders").session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.orders.length()").value(count)).andReturn();
		System.out.println("[성능] 주문 %d건 목록 응답 크기: %dKB".formatted(count, all.getResponse().getContentAsByteArray().length / 1024));

		assertWithin(PerformanceSupport.measure("주문 %d건 목록 조회".formatted(count), 2, 7,
				() -> mvc.perform(get("/api/orders").session(s)).andExpect(status().isOk())), budgetMs);
		assertWithin(PerformanceSupport.measure("주문 %d건 상태(확정)로 거른 목록 조회".formatted(count), 2, 7,
				() -> mvc.perform(get("/api/orders").param("status", "CONFIRMED").session(s)).andExpect(status().isOk())),
				budgetMs);
	}

	@Test
	void listWithThousandOrdersWithinBudget() throws Exception {
		measureList(login(), 1_000, LIST_1000_MEDIAN_BUDGET_MS);
	}

	@Test
	void listWithFiveThousandOrdersWithinBudget() throws Exception {
		measureList(login(), 5_000, LIST_5000_MEDIAN_BUDGET_MS);
	}

	@Test
	void openingOneOrderStaysFastWithManyOrders() throws Exception {
		MockHttpSession s = login();
		long id = seedExactly(s, 5_000);

		assertWithin(PerformanceSupport.measure("주문 5,000건 중 하나 상세 조회", 3, 15,
				() -> mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isOk())), DETAIL_MEDIAN_BUDGET_MS);
	}
}
