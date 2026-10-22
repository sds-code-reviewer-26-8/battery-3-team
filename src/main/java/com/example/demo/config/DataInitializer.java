package com.example.demo.config;

import com.example.demo.service.MasterService;
import com.example.demo.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

	private final MasterService service;
	private final UserService userService;

	public DataInitializer(MasterService service, UserService userService) {
		this.service = service;
		this.userService = userService;
	}

	@Override
	public void run(ApplicationArguments args) {
		service.initDefaults();
		userService.ensureAdmin();
	}
}
