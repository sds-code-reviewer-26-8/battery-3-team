package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 화면이 쓰는 JSON API(리팩토링 2)와 화면 주소(정적 HTML)를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class JsonApiTests {

	private static final String ORDERS = """
			{"CHINA":3000,"JAPAN":3000,"VIETNAM":3000,"INDONESIA":3000}""";

	@Autowired
	MockMvc mvc;
	@Autowired
	OrderStatusHistoryRepository histories;

	private MockHttpSession login() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/login").param("username", "admin").param("password", "admin").session(session))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/orders"));
		return session;
	}

	/** 주문 하나를 확정하고 번호를 돌려준다. */
	private int confirm(MockHttpSession s) throws Exception {
		MvcResult sim = mvc.perform(post("/api/simulate").session(s).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":" + ORDERS + "}")).andReturn();
		int version = JsonPath.read(sim.getResponse().getContentAsString(), "$.master.version");
		MvcResult res = mvc.perform(post("/api/orders").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":" + ORDERS + ",\"routeType\":\"COST\",\"masterVersion\":" + version
								+ ",\"memo\":\"api\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
	}

	private static String statusBody(String status, long version) {
		return "{\"status\":\"" + status + "\",\"version\":" + version + "}";
	}

	@Test
	void pageUrlsServeStaticHtmlAndStayProtected() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/login.html"));
		mvc.perform(get("/login.html")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"loginForm\"")));

		MockHttpSession s = login();
		mvc.perform(get("/orders").session(s)).andExpect(forwardedUrl("/orders.html"));
		mvc.perform(get("/orders/new").session(s)).andExpect(forwardedUrl("/order-new.html"));
		mvc.perform(get("/orders/7").session(s)).andExpect(forwardedUrl("/order-detail.html"));
		mvc.perform(get("/master").session(s)).andExpect(forwardedUrl("/master.html"));

		// 로그인하지 않으면 화면은 로그인으로, API 는 401
		mvc.perform(get("/orders/7")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/master")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("IN_PROGRESS", 0))).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/master").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsLoginUser() throws Exception {
		mvc.perform(get("/api/me").session(login())).andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"));
	}

	@Test
	void orderListHasCountsAndFilters() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);

		mvc.perform(get("/api/orders").session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.statuses.length()").value(4))
				.andExpect(jsonPath("$.statuses[0].name").value("CONFIRMED"))
				.andExpect(jsonPath("$.statuses[0].label").value("확정"))
				.andExpect(jsonPath("$.counts.CONFIRMED").isNumber())
				.andExpect(jsonPath("$.total").isNumber())
				.andExpect(jsonPath("$.orders[?(@.id==" + id + ")].status.name").value("CONFIRMED"))
				.andExpect(jsonPath("$.orders[?(@.id==" + id + ")].totalK").value(12000));

		// 상태로 거르면 그 상태 주문만 나오고, 건수는 거르기와 무관하게 전체 기준이다
		mvc.perform(get("/api/orders").param("status", "COMPLETED").session(s))
				.andExpect(jsonPath("$.orders[?(@.status.name!='COMPLETED')]").isEmpty())
				.andExpect(jsonPath("$.counts.CONFIRMED").isNumber());
	}

	@Test
	void orderDetailHasStoredPlanHistoryAndVersion() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);

		mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status.name").value("CONFIRMED"))
				.andExpect(jsonPath("$.version").isNumber())
				.andExpect(jsonPath("$.memo").value("api"))
				.andExpect(jsonPath("$.createdBy").value("admin"))
				.andExpect(jsonPath("$.next.length()").value(2))
				.andExpect(jsonPath("$.history.length()").value(1))
				.andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
				.andExpect(jsonPath("$.history[0].toStatus.name").value("CONFIRMED"))
				.andExpect(jsonPath("$.plan.master.factories.length()").value(4))
				.andExpect(jsonPath("$.plan.plan.type").value("COST"));
	}

	@Test
	void missingOrderIsNotFoundNotServerError() throws Exception {
		mvc.perform(get("/api/orders/999999").session(login())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("주문을 찾을 수 없습니다"))
				.andExpect(jsonPath("$.conflict").value(false));
	}

	@Test
	void statusChangeReturnsUpdatedOrderAndRejectsBadRequests() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		long version = ((Number) JsonPath.read(mvc.perform(get("/api/orders/" + id).session(s)).andReturn()
				.getResponse().getContentAsString(), "$.version")).longValue();

		// 허용되지 않는 전이: 400, 상태 그대로
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("COMPLETED", version)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("확정 상태에서는 완료(으)로 바꿀 수 없습니다"));

		// 정상 전이: 바뀐 주문과 이력이 함께 온다
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("IN_PROGRESS", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(false))
				.andExpect(jsonPath("$.order.status.name").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.order.history.length()").value(2))
				.andExpect(jsonPath("$.order.version").value(org.hamcrest.Matchers.greaterThan((int) version)));

		// 옛 version 으로 바꾸려 하면 409 (동시 수정 확인)
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("COMPLETED", version)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.conflict").value(true));

		// 없는 주문
		mvc.perform(post("/api/orders/999999/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("IN_PROGRESS", 0)))
				.andExpect(status().isNotFound());
	}

	@Test
	void missingOrderPageStillOpens() throws Exception {
		// 화면 주소는 주문 존재를 확인하지 않고 화면(HTML)을 내준다. 안내는 화면이 JSON API(404)로 보여 준다.
		mvc.perform(get("/orders/999999").session(login())).andExpect(status().isOk())
				.andExpect(forwardedUrl("/order-detail.html"));
	}

	@Test
	void cancelWithStaleVersionIsConflict() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		long version = ((Number) JsonPath.read(mvc.perform(get("/api/orders/" + id).session(s)).andReturn()
				.getResponse().getContentAsString(), "$.version")).longValue();
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("IN_PROGRESS", version))).andExpect(status().isOk());

		// 옛 version 으로 취소하려 하면 409, 주문은 그대로 남는다
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("CANCELED", version)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.conflict").value(true));
		mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status.name").value("IN_PROGRESS"));
	}

	@Test
	void cancelDeletesOrderAndReportsDeleted() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		long version = ((Number) JsonPath.read(mvc.perform(get("/api/orders/" + id).session(s)).andReturn()
				.getResponse().getContentAsString(), "$.version")).longValue();

		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("CANCELED", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true))
				.andExpect(jsonPath("$.order").doesNotExist());
		mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isNotFound());
	}

	@Test
	void masterFormUsesEokAndInvalidSaveListsAllErrors() throws Exception {
		MockHttpSession s = login();
		mvc.perform(get("/api/master/form").session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.shipCount").isString())
				.andExpect(jsonPath("$.shipDailyCost").isString())
				.andExpect(jsonPath("$.maxDaily_CHINA").isString())
				.andExpect(jsonPath("$.days_KOREA_CHINA").isString());

		// 틀린 값이 여럿이면 이유를 모두 돌려주고 아무것도 저장하지 않는다
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"shipCount\":\"9\",\"shipDailyCost\":\"abc\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.conflict").value(false))
				.andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThan(2)))
				.andExpect(jsonPath("$.errors[0]").value("배 척수은(는) 1 이상 4 이하의 정수여야 합니다"));
		mvc.perform(get("/api/master").session(s)).andExpect(jsonPath("$.shipCount").value(3));
	}

	@Test
	void malformedRequestsGetReasonInJson() throws Exception {
		MockHttpSession s = login();
		// 없는 상태 이름, 깨진 JSON, 알 수 없는 나라 키, 범위를 넘는 번호, 알 수 없는 상태 값
		mvc.perform(get("/api/orders").param("status", "FOO").session(s))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("status 값이 올바르지 않습니다"))
				.andExpect(jsonPath("$.conflict").value(false));
		mvc.perform(post("/api/simulate").session(s).contentType(MediaType.APPLICATION_JSON).content("{bad"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("요청 내용을 읽을 수 없습니다. 입력값을 확인해 주세요"));
		mvc.perform(post("/api/simulate").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":{\"MARS\":1}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
		mvc.perform(get("/api/orders/99999999999999999999").session(s))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("id 값이 올바르지 않습니다"));
		mvc.perform(post("/api/orders/1/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"FOO\",\"version\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON).content("{bad"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.conflict").value(false));
	}

	@Test
	void masterSaveReturnsSavedFormInEok() throws Exception {
		MockHttpSession s = login();
		String current = mvc.perform(get("/api/master/form").session(s)).andReturn().getResponse()
				.getContentAsString();
		// 값을 바꾸지 않고 그대로 저장해도 정상 응답은 저장된 폼(억 원 문자열)이다
		mvc.perform(put("/api/master").session(s).contentType(MediaType.APPLICATION_JSON).content(current))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shipDailyCost").value((String) JsonPath.read(current, "$.shipDailyCost")))
				.andExpect(jsonPath("$.shipCount").value((String) JsonPath.read(current, "$.shipCount")));
	}

	@Test
	void singleErrorHasNoErrorsList() throws Exception {
		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":{}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("총 생산 필요 수는 1K 이상이어야 합니다"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}
	// ---- 취소(삭제) 관련 추가 확인 (fix/cancel-error-page)

	private long versionOf(MockHttpSession s, int id) throws Exception {
		return ((Number) JsonPath.read(mvc.perform(get("/api/orders/" + id).session(s)).andReturn()
				.getResponse().getContentAsString(), "$.version")).longValue();
	}

	/** 현재 version 으로 상태를 바꾸고 성공(200)을 확인한다. */
	private void moveTo(MockHttpSession s, int id, String target) throws Exception {
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
				.content(statusBody(target, versionOf(s, id)))).andExpect(status().isOk());
	}

	@Test
	void cancelingAlreadyCanceledOrderIsNotFoundNotServerError() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		long version = versionOf(s, id);
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("CANCELED", version))).andExpect(status().isOk());

		// 이미 삭제된 주문에 다시 요청하면(두 번 클릭, 다른 사용자가 먼저 취소) 500 이 아니라 404 와 이유
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("CANCELED", version)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("주문을 찾을 수 없습니다"))
				.andExpect(jsonPath("$.conflict").value(false));
	}

	@Test
	void cancelFromInProgressDeletesOrder() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		moveTo(s, id, "IN_PROGRESS");

		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("CANCELED", versionOf(s, id))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true))
				.andExpect(jsonPath("$.order").doesNotExist());
		mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isNotFound());
	}

	@Test
	void completedOrderCannotBeCanceled() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		moveTo(s, id, "IN_PROGRESS");
		moveTo(s, id, "COMPLETED");

		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("CANCELED", versionOf(s, id))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("완료 상태에서는 취소(으)로 바꿀 수 없습니다"));
		mvc.perform(get("/api/orders/" + id).session(s)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status.name").value("COMPLETED"));
	}

	@Test
	void cancelKeepsStatusHistoryAfterOrderIsDeleted() throws Exception {
		MockHttpSession s = login();
		int id = confirm(s);
		mvc.perform(post("/api/orders/" + id + "/status").session(s).contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("CANCELED", versionOf(s, id)))).andExpect(status().isOk());

		// 주문 행은 삭제되지만 이력(확정 → 취소)은 남는다
		List<OrderStatusHistory> history = histories.findByOrderIdOrderByIdAsc((long) id);
		assertThat(history).hasSize(2);
		assertThat(history.get(0).getFromStatus()).isNull();
		assertThat(history.get(0).getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(history.get(1).getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(history.get(1).getToStatus()).isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	void orderPageOpensForAnyNumericIdEvenOutOfRange() throws Exception {
		MockHttpSession s = login();
		// 화면 주소는 주문 존재를 확인하지 않는다. 범위를 넘는 숫자여도 서버 오류가 아니라 화면이 열린다.
		mvc.perform(get("/orders/99999999999999999999").session(s)).andExpect(status().isOk())
				.andExpect(forwardedUrl("/order-detail.html"));
	}

	@Test
	void nonNumericOrderPageIsNotFoundNotServerError() throws Exception {
		mvc.perform(get("/orders/abc").session(login())).andExpect(status().isNotFound());
	}
}
