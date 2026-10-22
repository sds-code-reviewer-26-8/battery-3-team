package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	public static final String ADMIN_USERNAME = "admin";
	private static final String ADMIN_PASSWORD = "admin";

	private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9_]{4,20}");
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MAX_PASSWORD_LENGTH = 64;
	/** BCrypt 가 처리할 수 있는 비밀번호의 최대 길이(UTF-8 바이트). 한글은 한 글자가 3바이트다. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	/** 관리자 계정(admin)이 없으면 만든다. 이미 있으면 그대로 둔다. */
	@Transactional
	public void ensureAdmin() {
		if (!users.existsByUsername(ADMIN_USERNAME)) {
			users.save(new User(ADMIN_USERNAME, passwordEncoder.encode(ADMIN_PASSWORD), LocalDateTime.now()));
		}
	}

	/**
	 * 회원 가입. 올바르지 않은 값이 하나라도 있으면 아무것도 저장하지 않고 이유를 모아
	 * {@link BusinessException} 으로 알린다. 가입한 회원은 admin 과 같은 권한을 갖는다.
	 *
	 * @return 저장한(소문자로 정규화한) 아이디
	 */
	public String signup(String username, String password, String passwordConfirm) {
		String normalized = normalize(username);
		validate(normalized, password, passwordConfirm);
		if (users.existsByUsername(normalized)) {
			throw usernameTaken();
		}
		try {
			users.saveAndFlush(new User(normalized, passwordEncoder.encode(password), LocalDateTime.now()));
		} catch (DataIntegrityViolationException e) {
			// 같은 아이디로 동시에 가입했을 때 유일 제약이 마지막 방어선이다
			throw usernameTaken();
		}
		return normalized;
	}

	/**
	 * 아이디와 비밀번호가 맞는지 확인한다.
	 *
	 * @return 맞으면 저장된 아이디, 틀리면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<String> authenticate(String username, String password) {
		return users.findByUsername(normalize(username))
				.filter(user -> password != null && passwordEncoder.matches(password, user.getPasswordHash()))
				.map(User::getUsername);
	}

	private static String normalize(String username) {
		return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
	}

	private static void validate(String username, String password, String passwordConfirm) {
		List<String> errors = new ArrayList<>();
		if (!USERNAME_PATTERN.matcher(username).matches()) {
			errors.add("아이디는 영문·숫자·밑줄(_) 4~20자여야 합니다");
		}
		if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
			errors.add("비밀번호는 %d자 이상 %d자 이하여야 합니다".formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH));
		} else if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			errors.add("비밀번호가 너무 깁니다. UTF-8 기준 %d바이트까지 가능합니다(영문·숫자 %d자, 한글 %d자)"
					.formatted(MAX_PASSWORD_BYTES, MAX_PASSWORD_LENGTH, MAX_PASSWORD_BYTES / 3));
		}
		if (password != null && !password.equals(passwordConfirm)) {
			errors.add("비밀번호와 비밀번호 확인이 같지 않습니다");
		}
		if (!errors.isEmpty()) {
			throw new BusinessException(errors);
		}
	}

	private static BusinessException usernameTaken() {
		return new BusinessException("이미 사용 중인 아이디입니다", true);
	}
}
