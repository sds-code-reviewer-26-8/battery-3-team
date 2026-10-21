package com.example.demo.entity;

import java.util.Arrays;
import java.util.List;

public enum Country {
	KOREA("한국"),
	CHINA("중국"),
	JAPAN("일본"),
	VIETNAM("베트남"),
	INDONESIA("인도네시아");

	private final String label;

	Country(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean isFactory() {
		return this != KOREA;
	}

	public static List<Country> factories() {
		return Arrays.stream(values()).filter(Country::isFactory).toList();
	}
}
