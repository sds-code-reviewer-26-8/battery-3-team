package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 나라 간 항로. 양방향 동일하므로 from 이 to 보다 앞선 순서(ordinal)인 한 건만 저장한다. */
@Entity
@Table(name = "route", uniqueConstraints = @UniqueConstraint(columnNames = {"from_country", "to_country"}))
public class Route {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_country", nullable = false)
	private Country fromCountry;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_country", nullable = false)
	private Country toCountry;

	@Column(nullable = false)
	private int days;

	protected Route() {
	}

	public Route(Country fromCountry, Country toCountry, int days) {
		this.fromCountry = fromCountry;
		this.toCountry = toCountry;
		this.days = days;
	}

	public Long getId() {
		return id;
	}

	public Country getFromCountry() {
		return fromCountry;
	}

	public Country getToCountry() {
		return toCountry;
	}

	public int getDays() {
		return days;
	}

	public void setDays(int days) {
		this.days = days;
	}
}
