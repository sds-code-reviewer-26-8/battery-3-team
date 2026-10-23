package com.example.demo.slice;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.controller.SignupController;
import com.example.demo.service.BusinessException;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SignupController.class)
class SignupControllerSliceTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	UserService userService;

	@Test
	void 가입은_로그인_없이도_되고_201과_아이디를_돌려준다() throws Exception {
		when(userService.signup("tester", "pw1234!", "pw1234!")).thenReturn("tester");

		mvc.perform(post("/api/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"tester\",\"password\":\"pw1234!\",\"passwordConfirm\":\"pw1234!\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("tester"));
	}

	@Test
	void 업무_오류가_400으로_바뀐다() throws Exception {
		when(userService.signup("admin", "x", "x")).thenThrow(new BusinessException("이미 사용 중인 아이디입니다"));

		mvc.perform(post("/api/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"x\",\"passwordConfirm\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("이미 사용 중인 아이디입니다"));
	}
}
