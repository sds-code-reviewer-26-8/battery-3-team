(function () {
	const form = document.getElementById('loginForm');
	const messages = document.getElementById('messages');

	// 성공하면 서버가 주문 목록으로 보내고(302), 실패하면 401 과 이유(error)를 준다.
	const submit = form.querySelector('button[type=submit]');

	form.addEventListener('submit', async e => {
		e.preventDefault();
		if (submit.disabled) return;   // 요청 중 중복 제출 방지
		submit.disabled = true;
		messages.innerHTML = '';
		try {
			const res = await fetch('/login', {
				method: 'POST',
				headers: {'Content-Type': 'application/x-www-form-urlencoded'},
				body: new URLSearchParams(new FormData(form)),
				redirect: 'manual'
			});
			if (res.type === 'opaqueredirect' || res.ok) {
				location.href = '/orders';
				return;
			}
			const data = await res.json().catch(() => null);
			messages.innerHTML = `<p class="alert error">${esc((data && data.error) || '로그인에 실패했습니다')}</p>`;
		} catch (err) {
			messages.innerHTML = '<p class="alert error">서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요</p>';
		} finally {
			submit.disabled = false;
		}
	});
})();
