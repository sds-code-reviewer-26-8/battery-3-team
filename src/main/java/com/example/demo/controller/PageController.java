package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 화면 주소는 그대로 두고 정적 HTML 을 내준다. 데이터는 화면의 JS 가 JSON API 로 받아 온다. */
@Controller
public class PageController {

	@GetMapping("/")
	public String home() {
		return "redirect:/orders";
	}

	@GetMapping("/orders")
	public String orders() {
		return "forward:/orders.html";
	}

	@GetMapping("/orders/new")
	public String newOrder() {
		return "forward:/order-new.html";
	}

	@GetMapping("/orders/{id:\\d+}")
	public String detail() {
		return "forward:/order-detail.html";
	}

	@GetMapping("/master")
	public String master() {
		return "forward:/master.html";
	}
}
