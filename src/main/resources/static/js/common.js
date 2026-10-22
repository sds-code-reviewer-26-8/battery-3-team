const COUNTRY_LABEL = {KOREA: '한국', CHINA: '중국', JAPAN: '일본', VIETNAM: '베트남', INDONESIA: '인도네시아'};
const FACTORY_ORDER = ['CHINA', 'JAPAN', 'VIETNAM', 'INDONESIA'];
const COUNTRY_ORDER = ['KOREA', ...FACTORY_ORDER];

/** 천 단위 쉼표 */
const fmt = n => Number(n).toLocaleString('ko-KR');

/** 원 → 억원(소수 2자리) */
const eok = won => (won / 1e8).toFixed(2);

/** HTML 에 넣을 문자열 이스케이프 */
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

/** 서버의 LocalDateTime(ISO) → 'yyyy-MM-dd HH:mm' (withSeconds 면 초까지) */
function fmtDateTime(iso, withSeconds) {
	const [date, time = '00:00:00'] = String(iso).split('T');
	const [h, m, s = '00'] = time.split(':');
	return `${date} ${h}:${m}` + (withSeconds ? `:${s.slice(0, 2)}` : '');
}

/**
 * JSON 요청. 실패하면 서버가 준 이유(error, 여러 개면 errors)를 담은 Error 를 던진다.
 * 로그인이 풀렸으면(401) 로그인 화면으로 보낸다.
 */
async function requestJson(method, url, body) {
	const res = await fetch(url, {
		method,
		headers: body === undefined ? {} : {'Content-Type': 'application/json'},
		body: body === undefined ? undefined : JSON.stringify(body)
	});
	let data = null;
	try {
		data = await res.json();
	} catch (e) { /* 본문 없음 */ }
	if (res.status === 401) {
		location.href = '/login';
		throw new Error('로그인이 필요합니다');
	}
	if (!res.ok) {
		const err = new Error((data && data.error) || '요청에 실패했습니다');
		err.conflict = !!(data && data.conflict);
		err.notFound = res.status === 404;
		err.errors = (data && data.errors) || null;
		throw err;
	}
	return data;
}

const getJson = url => requestJson('GET', url);
const postJson = (url, body) => requestJson('POST', url, body);
const putJson = (url, body) => requestJson('PUT', url, body);

/** 모든 화면 상단: 메뉴와 로그인한 아이디. #app-header 자리에 그린다. */
async function renderHeader(active) {
	const me = await getJson('/api/me');
	const link = (key, href, label) => `<a href="${href}"${active === key ? ' class="active"' : ''}>${label}</a>`;
	document.getElementById('app-header').outerHTML = `<header class="topbar">
		<a class="brand" href="/orders">배터리 주문 관리</a>
		<nav>${link('orders', '/orders', '주문 목록')}${link('new', '/orders/new', '주문 추가')}${link('master', '/master', '마스터 데이터')}</nav>
		<div class="user">
			<span>${esc(me.username)}</span>
			<form action="/logout" method="post"><button type="submit" class="link">로그아웃</button></form>
		</div>
	</header>`;
}
