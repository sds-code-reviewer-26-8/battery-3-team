package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuthController {

	private static final String ACCOUNT = "admin";

	@GetMapping("/login")
	public String loginForm(HttpSession session) {
		if (session.getAttribute(LoginInterceptor.SESSION_USER) != null) {
			return "redirect:/orders";
		}
		return "forward:/login.html";
	}

	/** 성공하면 주문 목록으로 이동(302), 실패하면 401 과 이유를 돌려준다. */
	@PostMapping("/login")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> login(@RequestParam String username, @RequestParam String password,
			HttpSession session) {
		if (ACCOUNT.equals(username) && ACCOUNT.equals(password)) {
			session.setAttribute(LoginInterceptor.SESSION_USER, username);
			return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/orders").build();
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "아이디 또는 비밀번호가 올바르지 않습니다", "conflict", false));
	}

	@PostMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/login";
	}

	/** 화면 상단에 보여 줄 로그인한 아이디 */
	@GetMapping("/api/me")
	@ResponseBody
	public Map<String, String> me(HttpSession session) {
		return Map.of("username", (String) session.getAttribute(LoginInterceptor.SESSION_USER));
	}
}
