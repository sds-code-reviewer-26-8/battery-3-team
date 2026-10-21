const COUNTRY_LABEL = {KOREA: '한국', CHINA: '중국', JAPAN: '일본', VIETNAM: '베트남', INDONESIA: '인도네시아'};
const FACTORY_ORDER = ['CHINA', 'JAPAN', 'VIETNAM', 'INDONESIA'];

/** 천 단위 쉼표 */
const fmt = n => Number(n).toLocaleString('ko-KR');

/** 원 → 억원(소수 2자리) */
const eok = won => (won / 1e8).toFixed(2);

/** JSON 요청. 실패하면 서버가 준 이유(error)를 담은 Error 를 던진다. */
async function postJson(url, body) {
	const res = await fetch(url, {
		method: 'POST',
		headers: {'Content-Type': 'application/json'},
		body: JSON.stringify(body)
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
		throw err;
	}
	return data;
}
