package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/** 로그인하지 않으면 로그인 화면만 볼 수 있다. API 는 401 을 돌려준다. */
public class LoginInterceptor implements HandlerInterceptor {

	public static final String SESSION_USER = "loginUser";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(SESSION_USER) != null) {
			return true;
		}
		if (request.getRequestURI().startsWith("/api/")) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		} else {
			response.sendRedirect("/login");
		}
		return false;
	}
}
