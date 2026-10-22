(function () {
	const form = document.getElementById('loginForm');
	const messages = document.getElementById('messages');

	// 성공하면 서버가 주문 목록으로 보내고(302), 실패하면 401 과 이유(error)를 준다.
	form.addEventListener('submit', async e => {
		e.preventDefault();
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
			messages.innerHTML = `<p class="alert error">${esc(err.message)}</p>`;
		}
	});
})();
