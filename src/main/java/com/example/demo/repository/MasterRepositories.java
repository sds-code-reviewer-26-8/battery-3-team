package com.example.demo.repository;

import com.example.demo.entity.Country;
import com.example.demo.entity.Factory;
import com.example.demo.entity.Route;
import com.example.demo.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public final class MasterRepositories {

	private MasterRepositories() {
	}

	public interface SettingRepository extends JpaRepository<Setting, Long> {
	}

	public interface FactoryRepository extends JpaRepository<Factory, Country> {
	}

	public interface RouteRepository extends JpaRepository<Route, Long> {
	}
}
