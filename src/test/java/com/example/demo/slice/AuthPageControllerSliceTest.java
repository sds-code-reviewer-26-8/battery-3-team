package com.example.demo.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.LoginInterceptor;
import com.example.demo.controller.AuthController;
import com.example.demo.controller.PageController;
import com.example.demo.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 로그인·로그아웃, 화면 주소 전달, 로그인 인터셉터(LoginInterceptor)의 동작을 함께 확인한다. */
@WebMvcTest({AuthController.class, PageController.class})
class AuthPageControllerSliceTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	UserService userService;

	private static MockHttpSession login() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(LoginInterceptor.SESSION_USER, "admin");
		return session;
	}

	@Test
	void 로그인에_성공하면_세션에_아이디를_넣고_주문_목록으로_보낸다() throws Exception {
		when(userService.authenticate("admin", "admin")).thenReturn(Optional.of("admin"));
		MockHttpSession session = new MockHttpSession();

		mvc.perform(post("/login").param("username", "admin").param("password", "admin").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/orders"));

		assertThat(session.getAttribute(LoginInterceptor.SESSION_USER)).isEqualTo("admin");
	}

	@Test
	void 로그인에_실패하면_401과_이유() throws Exception {
		when(userService.authenticate("admin", "bad")).thenReturn(Optional.empty());
		MockHttpSession session = new MockHttpSession();

		mvc.perform(post("/login").param("username", "admin").param("password", "bad").session(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 올바르지 않습니다"));

		assertThat(session.getAttribute(LoginInterceptor.SESSION_USER)).isNull();
	}

	@Test
	void 로그인_화면은_로그인_안_했으면_HTML을_전달하고_했으면_목록으로_보낸다() throws Exception {
		mvc.perform(get("/login")).andExpect(forwardedUrl("/login.html"));
		mvc.perform(get("/signup")).andExpect(forwardedUrl("/signup.html"));
		mvc.perform(get("/login").session(login())).andExpect(redirectedUrl("/orders"));
		mvc.perform(get("/signup").session(login())).andExpect(redirectedUrl("/orders"));
	}

	@Test
	void 로그아웃하면_세션을_끝내고_로그인으로_보낸다() throws Exception {
		MockHttpSession session = login();

		mvc.perform(post("/logout").session(session)).andExpect(redirectedUrl("/login"));

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void 내_아이디_조회() throws Exception {
		mvc.perform(get("/api/me").session(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"));
	}

	@Test
	void 로그인하지_않고_화면에_들어가면_로그인으로_이동한다() throws Exception {
		mvc.perform(get("/orders")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/master")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void 로그인하지_않고_API를_부르면_401() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void 로그인하면_화면_주소가_정적_HTML로_전달된다() throws Exception {
		mvc.perform(get("/").session(login())).andExpect(redirectedUrl("/orders"));
		mvc.perform(get("/orders").session(login())).andExpect(forwardedUrl("/orders.html"));
		mvc.perform(get("/orders/new").session(login())).andExpect(forwardedUrl("/order-new.html"));
		mvc.perform(get("/orders/12").session(login())).andExpect(forwardedUrl("/order-detail.html"));
		mvc.perform(get("/master").session(login())).andExpect(forwardedUrl("/master.html"));
	}
}
