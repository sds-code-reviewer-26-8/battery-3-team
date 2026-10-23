package com.example.demo.slice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.OrderController;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.service.BusinessException;
import com.example.demo.service.OrderService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 주문 API 의 요청 해석·응답 변환·오류 매핑만 확인한다. 업무 규칙은 서비스의 몫이라 여기서 다루지 않는다. */
@WebMvcTest(OrderController.class)
class OrderControllerSliceTest {

	private static final String CONFIRM = """
			{"orders":{"CHINA":3000},"routeType":"COST","masterVersion":1,%s}""";

	@Autowired
	MockMvc mvc;
	@MockitoBean
	OrderService orderService;

	private static MockHttpSession login() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(LoginInterceptor.SESSION_USER, "admin");
		return session;
	}

	private static Order order(long id, OrderStatus status) {
		Order o = new Order(LocalDateTime.of(2026, 1, 1, 9, 0), "admin", 3, "COST", "운항비 최소", 10, 300_000_000L,
				100_000_000L, 400_000_000L, "메모", null, "{\"routes\":[]}", 50_000_000L);
		o.setStatus(status);
		ReflectionTestUtils.setField(o, "id", id);
		return o;
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
		verify(orderService, never()).listOrders(any());
	}

	@Test
	void 목록은_상태별_건수와_전체_건수를_함께_준다() throws Exception {
		when(orderService.listOrders(null)).thenReturn(List.of(order(1, OrderStatus.CONFIRMED)));
		when(orderService.countByStatus()).thenReturn(Map.of(OrderStatus.CONFIRMED, 1L, OrderStatus.COMPLETED, 2L));

		mvc.perform(get("/api/orders").session(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orders[0].id").value(1))
				.andExpect(jsonPath("$.orders[0].totalCostEok").value("4.00"))
				.andExpect(jsonPath("$.counts.CONFIRMED").value(1))
				.andExpect(jsonPath("$.counts.IN_PROGRESS").value(0))
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.statuses.length()").value(4));
	}

	@Test
	void 상태_질의값이_틀리면_400() throws Exception {
		mvc.perform(get("/api/orders?status=NOPE").session(login()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("status 값이 올바르지 않습니다"));
	}

	@Test
	void 상세는_다음_가능한_상태와_저장된_계획을_포함한다() throws Exception {
		when(orderService.getOrder(5L)).thenReturn(order(5, OrderStatus.CONFIRMED));
		when(orderService.history(5L)).thenReturn(List.of(
				new OrderStatusHistory(5L, null, OrderStatus.CONFIRMED, "admin", LocalDateTime.of(2026, 1, 1, 9, 0))));

		mvc.perform(get("/api/orders/5").session(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(5))
				.andExpect(jsonPath("$.next.length()").value(2))
				.andExpect(jsonPath("$.history.length()").value(1))
				.andExpect(jsonPath("$.plan.routes").isArray())
				.andExpect(jsonPath("$.late").value(false));
	}

	@Test
	void 없는_주문은_404() throws Exception {
		when(orderService.getOrder(99L)).thenThrow(BusinessException.notFound("주문을 찾을 수 없습니다"));

		mvc.perform(get("/api/orders/99").session(login()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("주문을 찾을 수 없습니다"));
	}

	@Test
	void 확정하면_세션_사용자와_함께_서비스를_부르고_번호를_돌려준다() throws Exception {
		when(orderService.confirm(any(), eq("COST"), eq(1L), any(), any(), any(), eq("admin")))
				.thenReturn(order(7, OrderStatus.CONFIRMED));

		mvc.perform(post("/api/orders").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content(CONFIRM.formatted("\"requiredDays\":20,\"customerName\":\"고객A\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7));

		verify(orderService).confirm(any(), eq("COST"), eq(1L), any(), eq(20), eq("고객A"), eq("admin"));
	}

	@Test
	void 필요_납기일이_없으면_가능한_빨리_null() throws Exception {
		when(orderService.confirm(any(), any(), anyLong(), any(), any(), any(), any()))
				.thenReturn(order(8, OrderStatus.CONFIRMED));

		mvc.perform(post("/api/orders").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content(CONFIRM.formatted("\"memo\":\"x\"")))
				.andExpect(status().isOk());

		verify(orderService).confirm(any(), eq("COST"), eq(1L), eq("x"), eq((Integer) null), eq((String) null),
				eq("admin"));
	}

	@Test
	void 필요_납기일이_소수나_문자열이면_400() throws Exception {
		for (String bad : List.of("5.5", "\"10\"", "true")) {
			mvc.perform(post("/api/orders").session(login()).contentType(MediaType.APPLICATION_JSON)
					.content(CONFIRM.formatted("\"requiredDays\":" + bad)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("필요 납기일은 정수로 입력하세요"));
		}
		verify(orderService, never()).confirm(any(), any(), anyLong(), any(), any(), any(), any());
	}

	@Test
	void 마스터가_바뀌었으면_409() throws Exception {
		when(orderService.confirm(any(), any(), anyLong(), any(), any(), any(), any()))
				.thenThrow(new BusinessException("마스터 데이터가 바뀌었습니다", true));

		mvc.perform(post("/api/orders").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content(CONFIRM.formatted("\"memo\":\"\"")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.conflict").value(true));
	}

	@Test
	void 취소하면_삭제됐다고_알리고_상세를_다시_읽지_않는다() throws Exception {
		mvc.perform(post("/api/orders/3/status").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELED\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true))
				.andExpect(jsonPath("$.order").doesNotExist());

		verify(orderService).changeStatus(3L, OrderStatus.CANCELED, 0L, "admin");
		verify(orderService, never()).getOrder(anyLong());
	}

	@Test
	void 상태를_바꾸면_바뀐_상세를_돌려준다() throws Exception {
		when(orderService.getOrder(3L)).thenReturn(order(3, OrderStatus.IN_PROGRESS));
		when(orderService.history(3L)).thenReturn(List.of());

		mvc.perform(post("/api/orders/3/status").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(false))
				.andExpect(jsonPath("$.order.id").value(3))
				.andExpect(jsonPath("$.order.next.length()").value(2));
	}

	@Test
	void 다른_사람이_먼저_바꿨으면_409() throws Exception {
		doThrow(new BusinessException("다른 사용자가 먼저 변경했습니다", true))
				.when(orderService).changeStatus(anyLong(), any(), anyLong(), any());

		mvc.perform(post("/api/orders/3/status").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
				.andExpect(status().isConflict());
	}

	@Test
	void 고객사_변경은_version이_없으면_400() throws Exception {
		mvc.perform(put("/api/orders/3/customer").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"customerName\":\"A\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("주문 version 이 필요합니다"));
	}

	@Test
	void 고객사_변경은_customerName_키가_없으면_400() throws Exception {
		mvc.perform(put("/api/orders/3/customer").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":0}"))
				.andExpect(status().isBadRequest());
		verify(orderService, never()).updateCustomer(anyLong(), any(), anyLong());
	}

	@Test
	void 고객사_변경은_이름이_문자열이_아니면_400() throws Exception {
		mvc.perform(put("/api/orders/3/customer").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":0,\"customerName\":123}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("customerName 은 문자열이어야 합니다"));
	}

	@Test
	void 고객사_이름을_null로_보내면_지운다() throws Exception {
		when(orderService.getOrder(3L)).thenReturn(order(3, OrderStatus.CONFIRMED));
		when(orderService.history(3L)).thenReturn(List.of());

		mvc.perform(put("/api/orders/3/customer").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":2,\"customerName\":null}"))
				.andExpect(status().isOk());

		verify(orderService).updateCustomer(3L, null, 2L);
	}
}
