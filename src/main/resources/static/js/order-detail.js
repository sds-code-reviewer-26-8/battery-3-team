// 주문 상세: 주문·이력·저장된 계획을 JSON API 로 받아 그리고, 상태 변경도 API 로 보낸다.
// 최상위 await 로 그린 뒤에야 페이지 로딩이 끝나게 한다.
const orderId = location.pathname.split('/').pop();
const main = document.getElementById('detail');

// 아래 최상위 await 보다 먼저 초기화되어야 하므로 파일 맨 위에 둔다.
const LIST_URL = '/orders';
const STATUS_CANCELED = 'CANCELED';

try {
	await Promise.all([renderHeader('orders'), load()]);
} catch (e) {
	showLoadError(e);
}

async function load(ok, error) {
	try {
		render(await getJson('/api/orders/' + orderId), ok, error);
	} catch (e) {
		showLoadError(e);
	}
}

/** 화면 맨 위 제목 줄(제목과 목록으로 버튼) */
function pageHead(titleHtml) {
	return `<div class="page-head"><h1>${titleHtml}</h1><a class="button" href="${LIST_URL}">목록으로</a></div>`;
}

/** 상세 대신 안내만 보여 준다. kind 는 ok 또는 error */
function showMessage(titleHtml, kind, text) {
	main.innerHTML = `${pageHead(titleHtml)}<p class="alert ${kind}">${esc(text)}</p>`;
}

/** 주문을 불러오지 못했을 때(예: 없는 주문) */
function showLoadError(e) {
	showMessage('주문 상세', 'error', e.message);
}

function showCanceled(d) {
	showMessage(`주문 <span>#${d.id}</span> <span class="badge badge-${STATUS_CANCELED}">취소</span>`, 'ok',
		`주문 #${d.id}을(를) 취소했습니다. 취소한 주문은 삭제되어 목록에서 사라집니다.`);
}

/** 필요 납기일 칸 문구. 선택한 경로가 납기를 넘으면 그 사실도 적는다. */
function deadlineText(d) {
	if (d.requiredDays == null) return '가능한 빨리';
	return d.late
		? `${d.requiredDays}일 (소요 ${d.totalDays}일, 납기 ${d.totalDays - d.requiredDays}일 초과)`
		: `${d.requiredDays}일`;
}

function render(d, ok, error) {
	const actions = d.next.length > 0
		? `<div class="actions">${d.next.map(s => `<form class="status-form" data-status="${s.name}" data-label="${esc(s.label)}">
				<button type="submit" class="${s.name === STATUS_CANCELED ? 'danger' : 'primary'}">${esc(s.label)}(으)로 변경</button>
			</form>`).join('')}</div>`
		: '<p class="muted">최종 상태라 더 바꿀 수 없습니다.</p>';
	const history = d.history.map(h => `<tr>
		<td>${fmtDateTime(h.changedAt, true)}</td>
		<td>${h.fromStatus ? esc(h.fromStatus.label) : '-'}</td>
		<td>${esc(h.toStatus.label)}</td>
		<td>${esc(h.changedBy)}</td>
	</tr>`).join('');

	main.innerHTML = `
	${pageHead(`주문 <span>#${d.id}</span> <span class="badge badge-${d.status.name}">${esc(d.status.label)}</span>`)}

	${ok ? `<p class="alert ok">${esc(ok)}</p>` : ''}
	${error ? `<p class="alert error">${esc(error)}</p>` : ''}

	<section class="card">
		<dl class="meta">
			<div><dt>확정 일시</dt><dd>${fmtDateTime(d.confirmedAt)}</dd></div>
			<div><dt>작성자</dt><dd>${esc(d.createdBy)}</dd></div>
			<div><dt>경로</dt><dd>${esc(d.routeLabel)}</dd></div>
			<div><dt>필요 납기일</dt><dd${d.late ? ' class="late"' : ''}>${deadlineText(d)}</dd></div>
			<div><dt>메모</dt><dd>${d.memo != null ? esc(d.memo) : '-'}</dd></div>
		</dl>
		${actions}
	</section>

	<section class="card">
		<h2>상태 이력</h2>
		<table class="grid">
			<thead><tr><th>일시</th><th>이전 상태</th><th>새 상태</th><th>변경한 사람</th></tr></thead>
			<tbody>${history}</tbody>
		</table>
	</section>

	<div id="summary"></div>

	<section class="card">
		<h2>지도 애니메이션 (저장된 계획)</h2>
		<div id="anim"></div>
	</section>

	<section class="card" id="details"></section>`;

	PlanView.renderSummary(document.getElementById('summary'), d.plan);
	PlanView.renderDetails(document.getElementById('details'), d.plan);
	BatteryAnim.mount(document.getElementById('anim'), d.plan, {autoplay: false});

	main.querySelectorAll('.status-form').forEach(f => f.addEventListener('submit', e => {
		e.preventDefault();
		changeStatus(d, f.dataset.status, f.dataset.label);
	}));
}

/** 확인 창을 거쳐 상태를 바꾼다. 실패하면(예: 다른 사람이 먼저 바꿈) 이유를 보여 주고 최신 상태를 다시 불러온다. */
async function changeStatus(d, status, label) {
	if (!confirm(`${label}(으)로 바꿉니다. 계속할까요?`)) return;
	try {
		const result = await postJson(`/api/orders/${d.id}/status`, {status, version: d.version});
		if (result.deleted) {
			showCanceled(d);   // 취소하면 주문이 삭제되므로 더 보여 줄 주문이 없다
		} else {
			render(result.order, `상태를 '${label}'(으)로 바꿨습니다`);
		}
	} catch (e) {
		if (e.notFound) showLoadError(e);
		else await load(null, e.message);
	}
}
