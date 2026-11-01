package com.youngmin.board.like;

import com.youngmin.board.like.dto.LikeResponse;
import com.youngmin.board.member.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요는 화면 이동 없이 fetch로 호출하므로 HTML 대신 JSON을 돌려준다 (@RestController).
 * 로그인하지 않았으면 SecurityConfig 설정으로 401이 나간다.
 */
@RestController
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeService postLikeService;

    @PostMapping("/posts/{postId}/like")
    public LikeResponse like(@PathVariable Long postId, @AuthenticationPrincipal MemberPrincipal principal) {
        return postLikeService.like(postId, principal.getId());
    }

    @DeleteMapping("/posts/{postId}/like")
    public LikeResponse unlike(@PathVariable Long postId, @AuthenticationPrincipal MemberPrincipal principal) {
        return postLikeService.unlike(postId, principal.getId());
    }
}
