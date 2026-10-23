package com.example.demo.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entity.Country;
import com.example.demo.entity.Factory;
import com.example.demo.entity.Route;
import com.example.demo.entity.Setting;
import com.example.demo.entity.User;
import com.example.demo.repository.FactoryRepository;
import com.example.demo.repository.RouteRepository;
import com.example.demo.repository.SettingRepository;
import com.example.demo.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/** 마스터(공장·항로·설정)와 사용자 리포지토리의 매핑·제약을 확인한다. 테스트마다 롤백된다. */
@DataJpaTest
class MasterUserRepositorySliceTest {

	@Autowired
	FactoryRepository factories;
	@Autowired
	RouteRepository routes;
	@Autowired
	SettingRepository settings;
	@Autowired
	UserRepository users;
	@Autowired
	TestEntityManager em;

	@Test
	void 공장은_나라가_키이고_수정하면_반영된다() {
		factories.saveAndFlush(new Factory(Country.CHINA, 100, 500, 1000, 150_000_000L));
		em.clear();

		Factory f = factories.findById(Country.CHINA).orElseThrow();
		assertThat(f.getMaxDaily()).isEqualTo(100);
		assertThat(f.getDailyOperatingCostWon()).isEqualTo(150_000_000L);

		f.setMaxDaily(200);
		f.setDailyOperatingCostWon(null);
		factories.saveAndFlush(f);
		em.clear();

		Factory changed = factories.findById(Country.CHINA).orElseThrow();
		assertThat(changed.getMaxDaily()).isEqualTo(200);
		assertThat(changed.getDailyOperatingCostWon()).isNull();
	}

	@Test
	void 운영비_없이_만든_공장은_운영비가_null() {
		factories.saveAndFlush(new Factory(Country.JAPAN, 80, 400, 900));
		em.clear();

		assertThat(factories.findById(Country.JAPAN).orElseThrow().getDailyOperatingCostWon()).isNull();
	}

	@Test
	void 같은_항로를_두_번_저장하면_실패() {
		routes.saveAndFlush(new Route(Country.KOREA, Country.CHINA, 3));

		assertThatThrownBy(() -> routes.saveAndFlush(new Route(Country.KOREA, Country.CHINA, 5)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 항로_일수를_바꾸면_저장된다() {
		Route r = routes.saveAndFlush(new Route(Country.KOREA, Country.JAPAN, 2));
		em.clear();

		Route loaded = routes.findById(r.getId()).orElseThrow();
		loaded.setDays(4);
		routes.saveAndFlush(loaded);
		em.clear();

		assertThat(routes.findById(r.getId()).orElseThrow().getDays()).isEqualTo(4);
	}

	@Test
	void 설정은_한_행이고_마스터_버전은_올릴_수_있다() {
		settings.saveAndFlush(new Setting(2, 300_000_000L));
		em.clear();

		Setting s = settings.findById(Setting.SINGLETON_ID).orElseThrow();
		assertThat(s.getMasterVersion()).isEqualTo(1);

		s.bumpMasterVersion();
		s.setShipCount(3);
		settings.saveAndFlush(s);
		em.clear();

		Setting changed = settings.findById(Setting.SINGLETON_ID).orElseThrow();
		assertThat(changed.getMasterVersion()).isEqualTo(2);
		assertThat(changed.getShipCount()).isEqualTo(3);
		assertThat(settings.count()).isEqualTo(1);
	}

	@Test
	void 아이디로_사용자를_찾고_존재_여부를_확인한다() {
		users.saveAndFlush(new User("tester", "hash", LocalDateTime.now()));

		assertThat(users.findByUsername("tester")).get().extracting(User::getPasswordHash).isEqualTo("hash");
		assertThat(users.findByUsername("nobody")).isEmpty();
		assertThat(users.existsByUsername("tester")).isTrue();
		assertThat(users.existsByUsername("nobody")).isFalse();
	}

	@Test
	void 같은_아이디는_저장할_수_없다() {
		users.saveAndFlush(new User("dup", "h1", LocalDateTime.now()));

		assertThatThrownBy(() -> users.saveAndFlush(new User("dup", "h2", LocalDateTime.now())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
