package com.example.demo.controller;

import com.example.demo.config.LoginInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class LoginUserAdvice {

	/** 모든 화면 상단에 로그인한 아이디를 보여 주기 위한 공통 모델 */
	@ModelAttribute("loginUser")
	public String loginUser(HttpSession session) {
		return (String) session.getAttribute(LoginInterceptor.SESSION_USER);
	}
}
