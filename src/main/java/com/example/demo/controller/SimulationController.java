package com.example.demo.controller;

import com.example.demo.entity.Country;
import com.example.demo.service.SimulationService;
import com.example.demo.service.SimulationService.SimulationResult;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

	public record SimulateRequest(Map<Country, Integer> orders) {
	}

	private final SimulationService simulationService;

	public SimulationController(SimulationService simulationService) {
		this.simulationService = simulationService;
	}

	@PostMapping
	public SimulationResult simulate(@RequestBody SimulateRequest request) {
		return simulationService.simulate(request.orders());
	}
}
