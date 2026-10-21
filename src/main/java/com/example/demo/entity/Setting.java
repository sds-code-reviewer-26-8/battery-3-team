package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 마스터 데이터 중 선박 값과 마스터 전체의 변경 번호. 한 행만 존재한다. */
@Entity
@Table(name = "setting")
public class Setting {

	public static final long SINGLETON_ID = 1L;

	@Id
	private Long id = SINGLETON_ID;

	private int shipCount;

	/** 척당 일 운항비(원) */
	private long shipDailyCostWon;

	/** 마스터 데이터를 저장할 때마다 1씩 증가. 시뮬레이션 이후 마스터가 바뀌었는지 확인하는 데 쓴다. */
	private long masterVersion;

	@Version
	private long lockVersion;

	protected Setting() {
	}

	public Setting(int shipCount, long shipDailyCostWon) {
		this.shipCount = shipCount;
		this.shipDailyCostWon = shipDailyCostWon;
		this.masterVersion = 1;
	}

	public int getShipCount() {
		return shipCount;
	}

	public void setShipCount(int shipCount) {
		this.shipCount = shipCount;
	}

	public long getShipDailyCostWon() {
		return shipDailyCostWon;
	}

	public void setShipDailyCostWon(long shipDailyCostWon) {
		this.shipDailyCostWon = shipDailyCostWon;
	}

	public long getMasterVersion() {
		return masterVersion;
	}

	public void bumpMasterVersion() {
		this.masterVersion++;
	}
}
