package com.example.demo.controller;

import com.example.demo.service.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

	/** 깨진 JSON, 알 수 없는 나라·상태 값 같은 요청 형식 오류 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		return badRequest("요청 내용을 읽을 수 없습니다. 입력값을 확인해 주세요");
	}

	/** 주소나 질의 값의 형식이 맞지 않을 때(예: 없는 상태 이름, 숫자가 아닌 번호) */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
		return badRequest("%s 값이 올바르지 않습니다".formatted(e.getName()));
	}

	private static ResponseEntity<Map<String, Object>> badRequest(String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", message);
		body.put("conflict", false);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}
}
