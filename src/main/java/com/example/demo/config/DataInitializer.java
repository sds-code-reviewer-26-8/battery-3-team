package com.example.demo.config;

import com.example.demo.service.BatteryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

	private final BatteryService service;

	public DataInitializer(BatteryService service) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		service.initDefaults();
	}
}
