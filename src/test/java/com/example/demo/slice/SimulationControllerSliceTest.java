package com.example.demo.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.SimulationController;
import com.example.demo.entity.Country;
import com.example.demo.service.BusinessException;
import com.example.demo.service.SimulationService;
import com.example.demo.service.SimulationService.SimulationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulationController.class)
class SimulationControllerSliceTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	SimulationService simulationService;

	private static MockHttpSession login() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(LoginInterceptor.SESSION_USER, "admin");
		return session;
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mvc.perform(post("/api/simulate").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@SuppressWarnings("unchecked")
	void 나라별_수량을_Country로_바꿔_서비스에_넘긴다() throws Exception {
		when(simulationService.simulate(any())).thenReturn(
				new SimulationResult(null, Map.of(Country.CHINA, 3000), Map.of(Country.CHINA, 1), List.of()));

		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":{\"CHINA\":3000,\"JAPAN\":0}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orders.CHINA").value(3000))
				.andExpect(jsonPath("$.visits.CHINA").value(1))
				.andExpect(jsonPath("$.options").isArray());

		ArgumentCaptor<Map<Country, Integer>> captor = ArgumentCaptor.forClass(Map.class);
		verify(simulationService).simulate(captor.capture());
		assertThat(captor.getValue()).containsEntry(Country.CHINA, 3000).containsEntry(Country.JAPAN, 0);
	}

	@Test
	void 알_수_없는_나라는_400() throws Exception {
		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":{\"MARS\":10}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.conflict").value(false));
	}

	@Test
	void 업무_오류는_400_본문으로_바뀐다() throws Exception {
		when(simulationService.simulate(any())).thenThrow(new BusinessException("총 생산 필요 수는 1K 이상이어야 합니다"));

		mvc.perform(post("/api/simulate").session(login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":{}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("총 생산 필요 수는 1K 이상이어야 합니다"))
				.andExpect(jsonPath("$.conflict").value(false));
	}
}
