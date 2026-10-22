// 마스터 데이터: 입력칸 값을 JSON API 로 받아 채우고, 저장도 API 로 보낸다.
// 최상위 await 로 그린 뒤에야 페이지 로딩이 끝나게 한다.
const form = document.getElementById('masterForm');
const messages = document.getElementById('messages');

try {
	const [, values] = await Promise.all([renderHeader('master'), getJson('/api/master/form')]);
	buildFields();   // 값을 받은 뒤에 입력칸을 만들어, 입력칸이 보이면 값도 채워져 있게 한다
	fill(values);
} catch (e) {
	showErrors([e.message]);
}

/** 입력칸 이름(maxDaily_CHINA, days_KOREA_CHINA ...)은 서버가 받는 이름과 같다. */
function buildFields() {
	document.getElementById('factoryRows').innerHTML = FACTORY_ORDER.map(c => `<tr>
		<td>${COUNTRY_LABEL[c]}</td>
		<td><input type="number" min="1" name="maxDaily_${c}"></td>
		<td><input type="number" min="1" name="stockLimit_${c}"></td>
		<td><input type="number" min="1" name="unitCost_${c}"></td>
	</tr>`).join('');

	const cols = COUNTRY_ORDER.slice(1);
	const rows = COUNTRY_ORDER.slice(0, -1);
	document.getElementById('routeMatrix').innerHTML = `<thead><tr><th></th>${cols.map(c => `<th>${COUNTRY_LABEL[c]}</th>`).join('')}</tr></thead>
		<tbody>${rows.map((a, ai) => `<tr><th>${COUNTRY_LABEL[a]}</th>${cols.map((b, bi) =>
			bi + 1 > ai
				? `<td><input type="number" min="1" name="days_${a}_${b}"></td>`
				: '<td><span class="muted">-</span></td>').join('')}</tr>`).join('')}</tbody>`;
}

function fill(values) {
	Object.entries(values).forEach(([name, value]) => {
		const input = form.elements[name];
		if (input) input.value = value;
	});
}

function showErrors(errors) {
	messages.innerHTML = `<div class="alert error"><strong>저장하지 않았습니다. 아래 값을 확인하세요.</strong>
		<ul>${errors.map(e => `<li>${esc(e)}</li>`).join('')}</ul></div>`;
}

// 틀린 값이 하나라도 있으면 서버는 아무것도 저장하지 않고 이유를 모두 돌려준다. 입력값은 그대로 둔다.
form.addEventListener('submit', async e => {
	e.preventDefault();
	messages.innerHTML = '';
	const body = {};
	new FormData(form).forEach((value, name) => { body[name] = value; });
	try {
		fill(await putJson('/api/master', body));
		messages.innerHTML = '<p class="alert ok">마스터 데이터를 저장했습니다</p>';
	} catch (err) {
		showErrors(err.errors || [err.message]);
	}
});
