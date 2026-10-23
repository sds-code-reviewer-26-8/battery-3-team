package com.example.demo.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/** 주문·이력 리포지토리의 질의와 매핑만 확인한다. 테스트마다 롤백된다. */
@DataJpaTest
class OrderRepositorySliceTest {

	@Autowired
	OrderRepository orders;
	@Autowired
	OrderStatusHistoryRepository histories;
	@Autowired
	TestEntityManager em;

	private Order save(OrderStatus status) {
		Order o = new Order(LocalDateTime.of(2026, 1, 1, 9, 0), "admin", 3, "COST", "운항비 최소", 10, 300_000_000L,
				100_000_000L, 400_000_000L, "메모", null, "{}", null);
		o.setStatus(status);
		return orders.saveAndFlush(o);
	}

	@Test
	void 저장하면_번호와_version이_붙고_처음_상태는_확정() {
		Order saved = orders.saveAndFlush(new Order(LocalDateTime.now(), "admin", 1, "FAST", "빠르게", 5, 1, 1, 2, null,
				12, "{}", 7L));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(saved.getVersion()).isZero();
		assertThat(orders.findById(saved.getId())).get()
				.satisfies(o -> {
					assertThat(o.getRequiredDays()).isEqualTo(12);
					assertThat(o.getOperatingCost()).isEqualTo(7L);
				});
	}

	@Test
	void 상태별_건수를_센다() {
		save(OrderStatus.CONFIRMED);
		save(OrderStatus.CONFIRMED);
		save(OrderStatus.COMPLETED);

		assertThat(orders.countByStatus(OrderStatus.CONFIRMED)).isEqualTo(2);
		assertThat(orders.countByStatus(OrderStatus.COMPLETED)).isEqualTo(1);
		assertThat(orders.countByStatus(OrderStatus.IN_PROGRESS)).isZero();
	}

	@Test
	void 목록은_번호_내림차순() {
		Order a = save(OrderStatus.CONFIRMED);
		Order b = save(OrderStatus.IN_PROGRESS);
		Order c = save(OrderStatus.CONFIRMED);

		assertThat(orders.findAllByOrderByIdDesc()).extracting(Order::getId)
				.containsExactly(c.getId(), b.getId(), a.getId());
		assertThat(orders.findByStatusOrderByIdDesc(OrderStatus.CONFIRMED)).extracting(Order::getId)
				.containsExactly(c.getId(), a.getId());
	}

	@Test
	void 상태를_바꾸면_version이_올라간다() {
		Order o = save(OrderStatus.CONFIRMED);

		o.setStatus(OrderStatus.IN_PROGRESS);
		orders.saveAndFlush(o);
		em.clear();

		Order reloaded = orders.findById(o.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
		assertThat(reloaded.getVersion()).isEqualTo(1);
	}

	@Test
	void 필수값이_없으면_저장_실패() {
		Order noCreator = new Order(LocalDateTime.now(), null, 1, "COST", "x", 1, 1, 1, 2, null, null, "{}", null);

		assertThatThrownBy(() -> orders.saveAndFlush(noCreator)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 이력은_주문별로_오래된_순() {
		LocalDateTime t = LocalDateTime.of(2026, 1, 1, 9, 0);
		histories.save(new OrderStatusHistory(1L, null, OrderStatus.CONFIRMED, "admin", t));
		histories.save(new OrderStatusHistory(2L, null, OrderStatus.CONFIRMED, "admin", t));
		histories.save(new OrderStatusHistory(1L, OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, "admin", t.plusDays(1)));
		histories.flush();

		List<OrderStatusHistory> result = histories.findByOrderIdOrderByIdAsc(1L);

		assertThat(result).extracting(OrderStatusHistory::getToStatus)
				.containsExactly(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);
		assertThat(result.get(0).getFromStatus()).isNull();
		assertThat(histories.findByOrderIdOrderByIdAsc(3L)).isEmpty();
	}
}
