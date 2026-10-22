package com.example.demo.controller;

import com.example.demo.service.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 업무 오류를 {error, conflict} JSON 으로 바꾼다. 이유가 여러 개면 errors 도 함께 준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, Object>> handle(BusinessException e) {
		HttpStatus status = e.isConflict() ? HttpStatus.CONFLICT
				: e.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", e.getMessage());
		body.put("conflict", e.isConflict());
		if (e.getMessages().size() > 1) {
			body.put("errors", e.getMessages());
		}
		return ResponseEntity.status(status).body(body);
	}
}
