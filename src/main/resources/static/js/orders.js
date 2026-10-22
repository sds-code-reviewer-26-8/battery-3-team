// 주문 목록: ?status= 로 거른 목록과 상태별 건수를 JSON API 로 받아 그린다.
// 최상위 await 로 그린 뒤에야 페이지 로딩이 끝나게 한다(그려지기 전에 화면을 읽는 일이 없도록).
const status = new URLSearchParams(location.search).get('status');

try {
	const [, data] = await Promise.all([
		renderHeader('orders'),
		getJson('/api/orders' + (status ? '?status=' + encodeURIComponent(status) : ''))
	]);
	renderFilters(data);
	renderRows(data.orders);
} catch (e) {
	document.getElementById('rows').innerHTML =
		`<tr><td colspan="8" class="empty">${esc(e.message)}</td></tr>`;
}

function renderFilters(data) {
	const chip = (href, active, label, count) =>
		`<a href="${href}" class="chip${active ? ' active' : ''}">${label} <span class="count">${count}</span></a>`;
	document.getElementById('filters').innerHTML =
		chip('/orders', !status, '전체', data.total) +
		data.statuses.map(s =>
			chip('/orders?status=' + s.name, status === s.name, `<span>${esc(s.label)}</span>`, data.counts[s.name])
		).join('');
}

function renderRows(orders) {
	const tbody = document.getElementById('rows');
	if (orders.length === 0) {
		tbody.innerHTML = '<tr><td colspan="8" class="empty">주문이 없습니다.</td></tr>';
		return;
	}
	tbody.innerHTML = orders.map(o => `<tr class="clickable" data-id="${o.id}">
		<td><a href="/orders/${o.id}">#${o.id}</a></td>
		<td>${fmtDateTime(o.confirmedAt)}</td>
		<td class="num">${fmt(o.totalK)}</td>
		<td>${esc(o.routeLabel)}</td>
		<td class="num">${o.totalDays}</td>
		<td class="num">${o.totalCostEok}</td>
		<td><span class="badge badge-${o.status.name}">${esc(o.status.label)}</span></td>
		<td>${esc(o.createdBy)}</td>
	</tr>`).join('');
	tbody.querySelectorAll('tr.clickable').forEach(tr =>
		tr.addEventListener('click', () => { location.href = '/orders/' + tr.dataset.id; }));
}
