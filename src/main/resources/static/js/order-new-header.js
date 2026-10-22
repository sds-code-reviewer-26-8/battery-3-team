// 주문 추가 화면의 상단 메뉴. 다른 화면과 달리 본문 스크립트(order-new.js)가 따로 있어 상단만 그린다.
try {
	await renderHeader('new');
} catch (e) { /* 로그인이 풀렸으면 renderHeader 가 로그인 화면으로 보낸다. 그 밖의 오류는 메뉴만 비어 보이게 둔다. */ }
