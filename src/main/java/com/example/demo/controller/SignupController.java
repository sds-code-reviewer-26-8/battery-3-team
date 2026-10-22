package com.example.demo.controller;

import com.example.demo.service.UserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signup")
public class SignupController {

	public record SignupRequest(String username, String password, String passwordConfirm) {
	}

	private final UserService userService;

	public SignupController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest request) {
		String username = userService.signup(request.username(), request.password(), request.passwordConfirm());
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username));
	}
}
