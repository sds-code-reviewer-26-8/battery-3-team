package com.example.demo.simulation;

import com.example.demo.entity.Country;
import java.util.Map;

/** 주문에 저장되는 계획 스냅샷: 확정 시점의 마스터 값, 나라별 주문 수량, 선택한 경로 */
public record StoredPlan(Master master, Map<Country, Integer> orders, Map<Country, Integer> visits, Plan plan) {
}
