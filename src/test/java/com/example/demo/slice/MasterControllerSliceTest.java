package com.example.demo.slice;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.MasterController;
import com.example.demo.entity.Country;
import com.example.demo.service.BusinessException;
import com.example.demo.service.MasterService;
import com.example.demo.simulation.Master;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 마스터 API 의 웹 계층만 확인한다. 서비스는 가짜로 대체한다. */
@WebMvcTest(MasterController.class)
class MasterControllerSliceTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	MasterService masterService;

	private static MockHttpSession login() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(LoginInterceptor.SESSION_USER, "admin");
		return session;
	}

	private static Master master() {
		return new Master(3, 2, 300_000_000L,
				List.of(new Master.FactoryParam(Country.CHINA, 100, 500, 1000, 150_000_000L)),
				List.of(new Master.RouteParam(Country.KOREA, Country.CHINA, 3)));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mvc.perform(get("/api/master")).andExpect(status().isUnauthorized());
		verify(masterService, never()).currentMaster();
	}

	@Test
	void 마스터_조회는_서비스_값을_JSON으로_돌려준다() throws Exception {
		when(masterService.currentMaster()).thenReturn(master());

		mvc.perform(get("/api/master").session(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3))
				.andExpect(jsonPath("$.shipCount").value(2))
				.andExpect(jsonPath("$.factories[0].country").value("CHINA"))
				.andExpect(jsonPath("$.routes[0].days").value(3));
	}

	@Test
	void 입력칸_이름으로_변환하고_운항비는_억_원으로_보여_준다() throws Exception {
		when(masterService.currentMaster()).thenReturn(master());

		mvc.perform(get("/api/master/form").session(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxDaily_CHINA").value("100"))
				.andExpect(jsonPath("$.stockLimit_CHINA").value("500"))
				.andExpect(jsonPath("$.unitCost_CHINA").value("1000"))
				.andExpect(jsonPath("$.operatingCost_CHINA").value("1.5"))
				.andExpect(jsonPath("$.shipCount").value("2"))
				.andExpect(jsonPath("$.shipDailyCost").value("3"))
				.andExpect(jsonPath("$.days_KOREA_CHINA").value("3"));
	}

	@Test
	void 저장하면_서비스에_폼을_넘기고_저장된_값을_돌려준다() throws Exception {
		when(masterService.currentMaster()).thenReturn(master());

		mvc.perform(put("/api/master").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipCount\":\"2\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shipCount").value("2"));

		verify(masterService).updateMaster(anyMap());
	}

	@Test
	void 검증에_실패하면_400과_이유_목록() throws Exception {
		doThrow(new BusinessException(List.of("배 척수는 1~4", "운항비는 0 이상")))
				.when(masterService).updateMaster(anyMap());

		mvc.perform(put("/api/master").session(login()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.conflict").value(false))
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void 깨진_JSON은_400() throws Exception {
		mvc.perform(put("/api/master").session(login()).contentType(MediaType.APPLICATION_JSON).content("{깨짐"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}
}
