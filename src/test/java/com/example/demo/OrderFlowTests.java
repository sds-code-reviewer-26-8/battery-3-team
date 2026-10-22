package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.OrderStatus;
import com.example.demo.service.BusinessException;
import com.example.demo.service.OrderService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowTests {

	private static final String ORDERS = """
			{"CHINA":3000,"JAPAN":3000,"VIETNAM":3000,"INDONESIA":3000}""";

	@Autowired
	MockMvc mvc;
	@Autowired
	OrderService orderService;
	@Autowired
	ObjectMapper json;
	@Autowired
	com.example.demo.repository.FactoryRepository factoryRepository;
	@Autowired
	com.example.demo.repository.OrderRepository orderRepository;

	private MockHttpSession login() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/login").param("username", "admin").param("password", "admin").session(session))
				.andExpect(redirectedUrl("/orders"));
		return session;
	}

	@Test
	void anonymousUserIsSentToLogin() throws Exception {
		mvc.perform(get("/orders")).andExpect(redirectedUrl("/login"));
		mvc.perform(post("/api/simulate").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/login").param("username", "admin").param("password", "x"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").exists());
		mvc.perform(post("/login").param("username", "other").param("password", "admin"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pagesRender() throws Exception {
		MockHttpSession s = login();
		for (String url : new String[] {"/orders", "/orders/new", "/master"}) {
			mvc.perform(get(url).session(s)).andExpect(status().isOk());
		}
	}

	@Test
	void simulateReturnsThreeOptions() throws Exception {
		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":" + ORDERS + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.options.length()").value(3))
				.andExpect(jsonPath("$.options[0].type").value("COST"))
				.andExpect(jsonPath("$.options[0].stock.CHINA").isArray())
				.andExpect(jsonPath("$.visits.JAPAN").value(2))
				.andExpect(jsonPath("$.master.factories.length()").value(4));
	}

	@Test
	void emptyOrderIsRejectedWithReason() throws Exception {
		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":{\"CHINA\":0}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	private long confirm(MockHttpSession s) throws Exception {
		MvcResult sim = mvc.perform(post("/api/simulate").session(s).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":" + ORDERS + "}")).andReturn();
		int version = com.jayway.jsonpath.JsonPath.read(sim.getResponse().getContentAsString(), "$.master.version");
		MvcResult res = mvc.perform(post("/api/orders").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":" + ORDERS + ",\"routeType\":\"FAST\",\"masterVersion\":" + version
								+ ",\"memo\":\"테스트\"}"))
				.andExpect(status().isOk()).andReturn();
		Integer id = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id");
		return id;
	}

	@Test
	void confirmThenDetailAndStatusFlow() throws Exception {
		MockHttpSession s = login();
		long id = confirm(s);

		mvc.perform(get("/orders/" + id).session(s)).andExpect(status().isOk());
		var order = orderService.getOrder(id);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(order.getPlanJson()).contains("\"master\"").contains("\"plan\"");

		// 확정 → 완료는 불가
		assertThatThrownBy(() -> orderService.changeStatus(id, OrderStatus.COMPLETED, order.getVersion(), "admin"))
				.isInstanceOf(BusinessException.class);

		orderService.changeStatus(id, OrderStatus.IN_PROGRESS, order.getVersion(), "admin");
		var inProgress = orderService.getOrder(id);
		assertThat(inProgress.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

		// 오래된 version 으로 바꾸려 하면 충돌
		assertThatThrownBy(() -> orderService.changeStatus(id, OrderStatus.COMPLETED, order.getVersion(), "admin"))
				.isInstanceOf(BusinessException.class).hasMessageContaining("먼저");

		orderService.changeStatus(id, OrderStatus.COMPLETED, inProgress.getVersion(), "admin");
		assertThat(orderService.history(id)).hasSize(3);

		// 완료는 최종 상태
		var done = orderService.getOrder(id);
		assertThatThrownBy(() -> orderService.changeStatus(id, OrderStatus.CANCELED, done.getVersion(), "admin"))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void confirmFailsWhenMasterChangedAfterSimulation() throws Exception {
		MockHttpSession s = login();
		mvc.perform(post("/api/orders").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":" + ORDERS + ",\"routeType\":\"COST\",\"masterVersion\":-1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.conflict").value(true));
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void masterUpdateBumpsVersionAndKeepsConfirmedPlans() throws Exception {
		MockHttpSession s = login();
		long id = confirm(s);
		String before = orderService.getOrder(id).getPlanJson();

		Map<String, String> form = new HashMap<>();
		form.put("shipCount", "3");
		form.put("shipDailyCost", "1.5");
		for (String c : new String[] {"CHINA", "JAPAN", "VIETNAM", "INDONESIA"}) {
			form.put("maxDaily_" + c, "500");
			form.put("stockLimit_" + c, "5000");
			form.put("unitCost_" + c, "600");
			form.put("operatingCost_" + c, "1");
		}
		String[] all = {"KOREA", "CHINA", "JAPAN", "VIETNAM", "INDONESIA"};
		for (int i = 0; i < all.length; i++) {
			for (int j = i + 1; j < all.length; j++) {
				form.put("days_" + all[i] + "_" + all[j], "3");
			}
		}
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(form)))
				.andExpect(status().isOk());

		assertThat(orderService.getOrder(id).getPlanJson()).isEqualTo(before);
		mvc.perform(get("/api/master").session(s))
				.andExpect(jsonPath("$.shipDailyCostWon").value(150_000_000));
	}

	@Test
	void invalidMasterIsNotSaved() throws Exception {
		MockHttpSession s = login();
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"shipCount\":\"9\",\"shipDailyCost\":\"abc\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());
		mvc.perform(get("/api/master").session(s)).andExpect(jsonPath("$.shipCount").value(3));
	}

	// ---- 공장별 하루 운영비 (TASK-007)

	private Map<String, String> masterForm(String operatingCost) {
		Map<String, String> form = new HashMap<>();
		form.put("shipCount", "3");
		form.put("shipDailyCost", "1");
		for (String c : new String[] {"CHINA", "JAPAN", "VIETNAM", "INDONESIA"}) {
			form.put("maxDaily_" + c, "500");
			form.put("stockLimit_" + c, "5000");
			form.put("unitCost_" + c, "600");
			form.put("operatingCost_" + c, operatingCost);
		}
		String[] all = {"KOREA", "CHINA", "JAPAN", "VIETNAM", "INDONESIA"};
		for (int i = 0; i < all.length; i++) {
			for (int j = i + 1; j < all.length; j++) {
				form.put("days_" + all[i] + "_" + all[j], "3");
			}
		}
		return form;
	}

	private long masterVersion(MockHttpSession s) throws Exception {
		return ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/master").session(s)).andReturn()
				.getResponse().getContentAsString(), "$.version")).longValue();
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void 기준정보_운영비_저장시_버전증가() throws Exception {
		MockHttpSession s = login();
		// 기본값은 1억(폼에는 "1")
		mvc.perform(get("/api/master/form").session(s)).andExpect(jsonPath("$.operatingCost_CHINA").value("1"))
				.andExpect(jsonPath("$.operatingCost_INDONESIA").value("1"));
		long v0 = masterVersion(s);

		Map<String, String> form = masterForm("0.5");
		form.put("operatingCost_JAPAN", "0");
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(form)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.operatingCost_CHINA").value("0.5"))
				.andExpect(jsonPath("$.operatingCost_JAPAN").value("0"));
		assertThat(masterVersion(s)).isEqualTo(v0 + 1);
		mvc.perform(get("/api/master").session(s))
				.andExpect(jsonPath("$.factories[0].operatingCostWon").value(50_000_000))
				.andExpect(jsonPath("$.factories[1].operatingCostWon").value(0));
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void 기준정보_운영비_잘못된값이면_전부_저장안됨() throws Exception {
		MockHttpSession s = login();
		long v0 = masterVersion(s);
		for (String bad : new String[] {"abc", "-1", "0.000000001", ""}) {
			Map<String, String> form = masterForm("2");
			form.put("operatingCost_VIETNAM", bad);
			form.put("maxDaily_CHINA", "777");
			mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON)
							.content(json.writeValueAsString(form)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").exists());
		}
		assertThat(masterVersion(s)).isEqualTo(v0);
		mvc.perform(get("/api/master/form").session(s)).andExpect(jsonPath("$.operatingCost_CHINA").value("1"))
				.andExpect(jsonPath("$.operatingCost_VIETNAM").value("1"))
				.andExpect(jsonPath("$.maxDaily_CHINA").value("400"));
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void 운영비_null이면_기본_1억() throws Exception {
		MockHttpSession s = login();
		for (var f : factoryRepository.findAll()) {
			f.setDailyOperatingCostWon(null);
			factoryRepository.save(f);
		}
		mvc.perform(get("/api/master/form").session(s)).andExpect(jsonPath("$.operatingCost_CHINA").value("1"));
		mvc.perform(get("/api/master").session(s))
				.andExpect(jsonPath("$.factories[0].operatingCostWon").value(100_000_000));
	}

	@Test
	void 확정시_운영비_저장() throws Exception {
		MockHttpSession s = login();
		long id = confirm(s);
		var order = orderService.getOrder(id);
		// 확정은 FAST 안이므로 서버가 다시 계산한 FAST 안의 운영비와 같다. 4개 공장 x 1억 x 소요 일수
		assertThat(order.getOperatingCost()).isEqualTo(400_000_000L * order.getTotalDays());
		assertThat(order.getTotalCost()).isEqualTo(
				order.getProductionCost() + order.getShippingCost() + order.getOperatingCost());
		String detail = mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.operatingCostEok").value(order.getOperatingCostEok()))
				.andExpect(jsonPath("$.plan.plan.operatingCost").value(order.getOperatingCost()))
				.andReturn().getResponse().getContentAsString();
		assertThat(detail).contains("operatingCostWon");
		mvc.perform(get("/api/orders").session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.orders[0].operatingCostEok").exists());
	}

	@Test
	void 운영비없는_과거주문은_빈값으로_열림() throws Exception {
		MockHttpSession s = login();
		long src = confirm(s);
		var base = orderService.getOrder(src);
		tools.jackson.databind.node.ObjectNode root = (tools.jackson.databind.node.ObjectNode) json
				.readTree(base.getPlanJson());
		((tools.jackson.databind.node.ObjectNode) root.get("plan")).remove("operatingCost");
		root.get("master").get("factories")
				.forEach(f -> ((tools.jackson.databind.node.ObjectNode) f).remove("operatingCostWon"));
		java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5);
		var legacy = orderRepository.save(new com.example.demo.entity.Order(at, "admin", base.getTotalK(), "COST",
				"운항비 최소", base.getTotalDays(), base.getProductionCost(), base.getShippingCost(),
				base.getProductionCost() + base.getShippingCost(), null, json.writeValueAsString(root), null));

		mvc.perform(get("/api/orders/" + legacy.getId()).session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.operatingCostEok").doesNotExist())
				.andExpect(jsonPath("$.totalCostEok").value(legacy.getTotalCostEok()))
				.andExpect(jsonPath("$.confirmedAt").value(org.hamcrest.Matchers.startsWith("2026-01-02T03:04:05")))
				.andExpect(jsonPath("$.plan.plan.operatingCost").doesNotExist());
		mvc.perform(get("/api/orders").session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.orders[?(@.id==" + legacy.getId() + ")].totalCostEok")
						.value(legacy.getTotalCostEok()));
		mvc.perform(get("/orders/" + legacy.getId()).session(s)).andExpect(status().isOk());
	}
}
