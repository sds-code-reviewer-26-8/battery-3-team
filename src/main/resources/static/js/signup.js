(function () {
	const form = document.getElementById('signupForm');
	const messages = document.getElementById('messages');
	const submit = form.querySelector('button[type=submit]');

	function showErrors(errors) {
		messages.innerHTML = `<div class="alert error"><strong>가입하지 않았습니다. 아래 내용을 확인하세요.</strong>
			<ul>${errors.map(e => `<li>${esc(e)}</li>`).join('')}</ul></div>`;
	}

	function showDone(username) {
		form.hidden = true;
		messages.innerHTML = `<p class="alert ok">가입이 완료되었습니다. <b>${esc(username)}</b> 계정으로 로그인해 주세요.</p>
			<p><a class="button primary" href="/login">로그인하러 가기</a></p>`;
	}

	// 틀린 값이 하나라도 있으면 서버는 가입시키지 않고 이유를 모두 돌려준다. 아이디는 남기고 비밀번호는 비운다.
	form.addEventListener('submit', async e => {
		e.preventDefault();
		if (submit.disabled) return;   // 요청 중 중복 제출 방지
		submit.disabled = true;
		messages.innerHTML = '';
		try {
			const result = await postJson('/api/signup', Object.fromEntries(new FormData(form)));
			showDone(result.username);
		} catch (err) {
			form.elements.password.value = '';
			form.elements.passwordConfirm.value = '';
			showErrors(err.errors || [err.message]);
		} finally {
			submit.disabled = false;
		}
	});
})();
