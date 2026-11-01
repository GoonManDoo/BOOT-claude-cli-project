// 좋아요 버튼: 새로고침 없이 fetch로 처리한다.
// 현재 상태가 "좋아요 안 함"이면 POST(좋아요), "좋아요 함"이면 DELETE(취소)를 보낸다.
document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('like-button');
    if (!button) return; // 로그인하지 않은 사용자에게는 버튼이 없다

    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
    const countEl = document.getElementById('like-count');

    button.addEventListener('click', async () => {
        // 응답이 오기 전까지 버튼을 잠근다. (화면에서 1차로 막는 것일 뿐, 진짜 방어는 서버의 유니크 제약)
        button.disabled = true;
        const liked = button.dataset.liked === 'true';
        try {
            const response = await fetch(button.dataset.url, {
                method: liked ? 'DELETE' : 'POST',
                headers: { [csrfHeader]: csrfToken, 'Accept': 'application/json' },
            });
            if (response.status === 401) {
                location.href = '/login'; // 세션이 만료된 경우
                return;
            }
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            const data = await response.json();
            button.dataset.liked = String(data.liked);
            button.textContent = data.liked ? '♥ 좋아요 취소' : '♡ 좋아요';
            countEl.textContent = data.likeCount;
        } catch (e) {
            alert('좋아요 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        } finally {
            button.disabled = false;
        }
    });
});
