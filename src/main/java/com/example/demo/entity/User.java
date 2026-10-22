package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 로그인할 수 있는 사용자. 모든 사용자는 같은 권한을 갖는다. */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 소문자로 정규화한 아이디. 유일하다. */
	@Column(nullable = false, unique = true, length = 20)
	private String username;

	/** 비밀번호 해시(평문은 저장하지 않는다) */
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected User() {
	}

	public User(String username, String passwordHash, LocalDateTime createdAt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
