package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "factory")
public class Factory {

	@Id
	@Enumerated(EnumType.STRING)
	private Country country;

	/** 하루 최대 생산량(K) */
	@Column(nullable = false)
	private int maxDaily;

	/** 재고 상한(K) */
	@Column(nullable = false)
	private int stockLimit;

	/** 셀당 원가(원) */
	@Column(nullable = false)
	private long unitCost;

	protected Factory() {
	}

	public Factory(Country country, int maxDaily, int stockLimit, long unitCost) {
		this.country = country;
		this.maxDaily = maxDaily;
		this.stockLimit = stockLimit;
		this.unitCost = unitCost;
	}

	public Country getCountry() {
		return country;
	}

	public int getMaxDaily() {
		return maxDaily;
	}

	public void setMaxDaily(int maxDaily) {
		this.maxDaily = maxDaily;
	}

	public int getStockLimit() {
		return stockLimit;
	}

	public void setStockLimit(int stockLimit) {
		this.stockLimit = stockLimit;
	}

	public long getUnitCost() {
		return unitCost;
	}

	public void setUnitCost(long unitCost) {
		this.unitCost = unitCost;
	}
}
