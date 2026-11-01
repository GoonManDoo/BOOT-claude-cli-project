package com.youngmin.board.post;

import com.youngmin.board.common.api.ApiResponse;
import com.youngmin.board.common.api.PageResponse;
import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.post.dto.PostApiRequest;
import com.youngmin.board.post.dto.PostDetailResponse;
import com.youngmin.board.post.dto.PostListResponse;
import com.youngmin.board.post.dto.PostSearchCondition;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 REST API. 화면용 PostController와 컨트롤러는 나눴지만 PostService는 같이 쓴다.
 * → 권한 검증, 소프트 삭제 조건 같은 규칙이 화면과 API에서 똑같이 적용된다.
 *
 * 상태 코드
 * - 200 OK: 조회, 수정 성공 (응답 본문 있음)
 * - 201 Created: 생성 성공 + Location 헤더에 새 글 주소
 * - 204 No Content: 삭제 성공 (돌려줄 본문 없음)
 * - 400/401/403/404: ApiExceptionHandler, SecurityConfig의 API 핸들러가 만든다
 *
 * CSRF: API 체인은 CSRF 검사를 끈 대신
 * - 작성/수정은 JSON만 받는다 (consumes). 다른 사이트가 몰래 보낼 수 있는 HTML 폼(form-urlencoded)은 415로 거절된다.
 *   JSON을 다른 사이트에서 보내려면 브라우저가 CORS 사전 요청(preflight)을 하는데, CORS를 열어 두지 않았으므로 막힌다.
 * - 401 응답에 WWW-Authenticate 헤더가 없어서 브라우저가 Basic 인증 정보를 기억하지 않는다 (ApiSecurityHandlers).
 *
 * 조회수: API 상세 조회는 조회수를 올리지 않는다 (세션 기반 중복 방지를 쓸 수 없고, 사람이 본 것이 아닐 수 있다).
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostApiController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<PageResponse<PostListResponse>> list(@ModelAttribute PostSearchCondition condition,
                                                           @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(PageResponse.from(postService.search(condition, page)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostDetailResponse> detail(@PathVariable Long id,
                                                  @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(postService.getDetail(id, principal != null && principal.isAdmin()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PostDetailResponse>> create(@Valid @RequestBody PostApiRequest request,
                                                                  @AuthenticationPrincipal MemberPrincipal principal) {
        Long postId = postService.create(request.toPostRequest(), principal.getId());
        return ResponseEntity.created(URI.create("/api/posts/" + postId))
                .body(ApiResponse.ok(postService.getDetail(postId, false)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PostDetailResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody PostApiRequest request,
                                                  @AuthenticationPrincipal MemberPrincipal principal) {
        postService.update(id, request.toPostRequest(), principal.getId());
        return ApiResponse.ok(postService.getDetail(id, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        postService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
