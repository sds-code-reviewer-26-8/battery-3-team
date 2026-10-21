package com.example.demo.service;

import java.util.List;

/** 사용자에게 이유를 그대로 보여 줄 업무 오류. conflict 가 true 면 동시 변경·마스터 변경 같은 충돌이다. */
public class BusinessException extends RuntimeException {

	private final boolean conflict;
	private final List<String> messages;

	public BusinessException(String message) {
		this(message, false);
	}

	public BusinessException(String message, boolean conflict) {
		super(message);
		this.conflict = conflict;
		this.messages = List.of(message);
	}

	public BusinessException(List<String> messages) {
		super(String.join(" / ", messages));
		this.conflict = false;
		this.messages = messages;
	}

	public boolean isConflict() {
		return conflict;
	}

	public List<String> getMessages() {
		return messages;
	}
}
