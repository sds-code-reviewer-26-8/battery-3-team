package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 회원 가입과 가입한 회원의 로그인·권한을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class SignupTests {

	private static final String PASSWORD = "password123";
	private static final String ORDERS = """
			{"CHINA":3000,"JAPAN":3000,"VIETNAM":3000,"INDONESIA":3000}""";
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	MockMvc mvc;
	@Autowired
	UserRepository users;
	@Autowired
	UserService userService;

	/** 테스트끼리 겹치지 않는 아이디 */
	private static String newUsername() {
		return "user_" + SEQUENCE.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 100_000, 36);
	}

	private ResultActions signup(String username, String password, String passwordConfirm) throws Exception {
		String body = "{\"username\":\"%s\",\"password\":\"%s\",\"passwordConfirm\":\"%s\"}"
				.formatted(username, password, passwordConfirm);
		return mvc.perform(post("/api/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions loginRequest(String username, String password, MockHttpSession session) throws Exception {
		return mvc.perform(post("/login").param("username", username).param("password", password).session(session));
	}

	private MockHttpSession loginAs(String username, String password) throws Exception {
		MockHttpSession session = new MockHttpSession();
		loginRequest(username, password, session).andExpect(status().isFound()).andExpect(redirectedUrl("/orders"));
		return session;
	}

	private String signedUpUser() throws Exception {
		String username = newUsername();
		signup(username, PASSWORD, PASSWORD).andExpect(status().isCreated());
		return username;
	}

	@Test
	void signupCreatesAccountAndDoesNotLogIn() throws Exception {
		String username = newUsername();
		signup(username, PASSWORD, PASSWORD).andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(username));

		// 가입만으로는 로그인되지 않는다
		mvc.perform(get("/orders")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void signedUpUserCanLogIn() throws Exception {
		String username = signedUpUser();
		MockHttpSession session = loginAs(username, PASSWORD);

		mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(username));
	}

	@Test
	void usernameIsCaseInsensitive() throws Exception {
		String username = newUsername();
		signup(username.toUpperCase(), PASSWORD, PASSWORD).andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(username));

		// 소문자·대문자 어느 쪽으로 로그인해도 같은 계정이다
		mvc.perform(get("/api/me").session(loginAs(username.toUpperCase(), PASSWORD)))
				.andExpect(jsonPath("$.username").value(username));
		signup(username, PASSWORD, PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void wrongCredentialsAreRejectedWithTheSameMessage() throws Exception {
		String username = signedUpUser();
		String message = "아이디 또는 비밀번호가 올바르지 않습니다";

		loginRequest(username, "wrong-password", new MockHttpSession()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value(message));
		loginRequest("nobody_here", PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value(message));
	}

	@Test
	void invalidInputIsRejectedWithAllReasons() throws Exception {
		signup("ab", "short", "different").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.conflict").value(false))
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[0]").value("아이디는 영문·숫자·밑줄(_) 4~20자여야 합니다"))
				.andExpect(jsonPath("$.errors[1]").value("비밀번호는 8자 이상 64자 이하여야 합니다"))
				.andExpect(jsonPath("$.errors[2]").value("비밀번호와 비밀번호 확인이 같지 않습니다"));

		// 빈 값
		mvc.perform(post("/api/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(2));
		// 허용하지 않는 문자와 너무 긴 아이디
		signup("한글아이디", PASSWORD, PASSWORD).andExpect(status().isBadRequest());
		signup("a".repeat(21), PASSWORD, PASSWORD).andExpect(status().isBadRequest());
		// 비밀번호는 64자까지만
		signup(newUsername(), "p".repeat(65), "p".repeat(65)).andExpect(status().isBadRequest());
		signup(newUsername(), "p".repeat(64), "p".repeat(64)).andExpect(status().isCreated());
	}

	@Test
	void passwordLongerThanBcryptLimitIsRejectedWithReasonNotServerError() throws Exception {
		String tooLong = "비밀번호가 너무 깁니다. UTF-8 기준 72바이트까지 가능합니다(영문·숫자 64자, 한글 24자)";

		// 한글은 한 글자가 3바이트라 24자(72바이트)까지만 가능하다
		signup(newUsername(), "가".repeat(24), "가".repeat(24)).andExpect(status().isCreated());
		String username = newUsername();
		// 이유가 하나뿐이면 error 에만 담긴다(errors 목록은 이유가 여럿일 때)
		signup(username, "가".repeat(25), "가".repeat(25)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(tooLong))
				.andExpect(jsonPath("$.errors").doesNotExist());
		assertThat(users.existsByUsername(username)).isFalse();

		// 글자 수는 64자 이하여도 바이트가 넘으면 같은 안내(영문 40자 + 한글 11자 = 73바이트)
		String mixed = "a".repeat(40) + "가".repeat(11);
		signup(newUsername(), mixed, mixed).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(tooLong));

		// 글자 수 규칙을 이미 어기면 같은 이유를 두 번 알리지 않는다(영문 65자)
		signup(newUsername(), "p".repeat(65), "p".repeat(65)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("비밀번호는 8자 이상 64자 이하여야 합니다"));

		// 영문 64자(64바이트)는 가능하다
		signup(newUsername(), "p".repeat(64), "p".repeat(64)).andExpect(status().isCreated());
	}

	@Test
	void rejectedSignupDoesNotCreateAccount() throws Exception {
		String username = newUsername();
		signup(username, "short", "short").andExpect(status().isBadRequest());

		assertThat(users.existsByUsername(username)).isFalse();
	}

	@Test
	void duplicateUsernameIsConflict() throws Exception {
		String username = signedUpUser();
		signup(username, PASSWORD, PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("이미 사용 중인 아이디입니다"))
				.andExpect(jsonPath("$.conflict").value(true));

		// admin 은 가입으로 쓸 수 없다
		signup("admin", PASSWORD, PASSWORD).andExpect(status().isConflict());
		signup("ADMIN", PASSWORD, PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void sameUsernameSignedUpConcurrentlyCreatesOnlyOneAccount() throws Exception {
		String username = newUsername();
		int attempts = 8;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				tasks.add(() -> signup(username, PASSWORD, PASSWORD).andReturn().getResponse().getStatus());
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : executor.invokeAll(tasks)) {
				statuses.add(future.get());
			}

			assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
			assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
			assertThat(users.findAll()).filteredOn(u -> u.getUsername().equals(username)).hasSize(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void passwordIsStoredAsHash() throws Exception {
		String username = signedUpUser();
		User user = users.findByUsername(username).orElseThrow();

		assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
	}

	@Test
	void adminIsSeededOnceAndStillLogsIn() throws Exception {
		userService.ensureAdmin();
		userService.ensureAdmin();

		assertThat(users.findAll()).filteredOn(u -> u.getUsername().equals("admin")).hasSize(1);
		mvc.perform(get("/api/me").session(loginAs("admin", "admin"))).andExpect(jsonPath("$.username").value("admin"));
	}

	@Test
	void signedUpUserHasSameAccessAsAdmin() throws Exception {
		String username = signedUpUser();
		MockHttpSession session = loginAs(username, PASSWORD);

		// 시뮬레이션과 주문 확정
		String sim = mvc.perform(post("/api/simulate").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orders\":" + ORDERS + "}")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		int version = JsonPath.read(sim, "$.master.version");
		String confirmed = mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
						.content("{\"orders\":" + ORDERS + ",\"routeType\":\"COST\",\"masterVersion\":" + version
								+ "}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int id = JsonPath.read(confirmed, "$.id");

		// 주문 작성자와 상태 변경 기록에 가입한 아이디가 남는다
		String detail = mvc.perform(get("/api/orders/" + id).session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.createdBy").value(username)).andReturn().getResponse().getContentAsString();
		long orderVersion = ((Number) JsonPath.read(detail, "$.version")).longValue();
		mvc.perform(post("/api/orders/" + id + "/status").session(session).contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"IN_PROGRESS\",\"version\":" + orderVersion + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.order.history[1].changedBy").value(username));

		// 마스터 데이터 저장
		String form = mvc.perform(get("/api/master/form").session(session)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		mvc.perform(put("/api/master").session(session).contentType(MediaType.APPLICATION_JSON).content(form))
				.andExpect(status().isOk());
	}

	@Test
	void signupPagesAreOpenOnlyBeforeLogin() throws Exception {
		mvc.perform(get("/signup")).andExpect(status().isOk()).andExpect(forwardedUrl("/signup.html"));
		mvc.perform(get("/signup.html")).andExpect(status().isOk());

		// 로그인한 사용자는 주문 목록으로 보낸다
		mvc.perform(get("/signup").session(loginAs("admin", "admin"))).andExpect(redirectedUrl("/orders"));

		// 가입 외의 화면·API 는 그대로 보호된다
		mvc.perform(get("/orders")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/master")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/master")).andExpect(status().isUnauthorized());
	}
}
