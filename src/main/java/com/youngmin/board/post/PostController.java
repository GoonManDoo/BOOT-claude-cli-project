package com.youngmin.board.post;

import com.youngmin.board.category.CategoryService;
import com.youngmin.board.comment.CommentService;
import com.youngmin.board.comment.dto.CommentResponse;
import com.youngmin.board.common.dto.PageNavigation;
import com.youngmin.board.holiday.HolidayService;
import com.youngmin.board.image.ImageForms;
import com.youngmin.board.image.PostImageService;
import com.youngmin.board.like.PostLikeService;
import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.post.dto.PostDetailResponse;
import com.youngmin.board.post.dto.PostListPage;
import com.youngmin.board.post.dto.PostRequest;
import com.youngmin.board.post.dto.PostSearchCondition;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 게시글 화면.
 * 작성/수정/삭제는 모두 처리 후 redirect 한다 (PRG: Post → Redirect → Get).
 * POST 응답으로 바로 화면을 그리면, 새로고침 시 브라우저가 같은 POST를 다시 보내서 글이 중복 등록된다.
 */
@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final PostLikeService postLikeService;
    private final PostImageService postImageService;
    private final CategoryService categoryService;
    private final HolidayService holidayService;
    private final PostViewTracker postViewTracker;

    @GetMapping
    public String list(@ModelAttribute("cond") PostSearchCondition condition,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        PostListPage listPage = postService.getListPage(condition, page);
        model.addAttribute("posts", listPage.posts());
        model.addAttribute("notices", listPage.notices());
        model.addAttribute("nav", PageNavigation.of(listPage.posts()));
        model.addAttribute("categories", categoryService.getCategories());
        model.addAttribute("searchTypes", SearchType.values());
        model.addAttribute("sorts", PostSort.values());
        return "post/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         HttpSession session,
                         Model model) {
        boolean admin = principal != null && principal.isAdmin();
        PostDetailResponse post = postService.getDetail(id, admin); // 없거나 볼 수 없는 글이면 여기서 404
        if (postViewTracker.shouldCount(session, id)) {
            postService.increaseViewCount(id);
        }
        List<CommentResponse> comments = commentService.getComments(id);
        Long loginMemberId = principal == null ? null : principal.getId();

        model.addAttribute("post", post);
        model.addAttribute("isAuthor", post.getAuthorId().equals(loginMemberId));
        model.addAttribute("liked", postLikeService.isLiked(id, loginMemberId));
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", CommentResponse.countVisible(comments));
        model.addAttribute("loginMemberId", loginMemberId);
        // 외부 API가 실패해도 empty → 공휴일 표시만 빠지고 화면은 정상
        model.addAttribute("holidayName", holidayService.findHolidayName(post.getCreatedAt().toLocalDate()).orElse(null));
        return "post/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("postRequest", new PostRequest());
        model.addAttribute("categories", categoryService.getCategories());
        return "post/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("postRequest") PostRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model) {
        Optional<Long> postId = ImageForms.submit(bindingResult, () -> postService.create(request, principal.getId()));
        if (postId.isPresent()) {
            return "redirect:/posts/" + postId.get();
        }
        model.addAttribute("categories", categoryService.getCategories());
        return "post/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        model.addAttribute("postRequest", postService.getEditForm(id, principal.getId()));
        addEditFormAttributes(id, model);
        return "post/form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("postRequest") PostRequest request,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model) {
        Optional<Long> saved = ImageForms.submit(bindingResult, () -> {
            postService.update(id, request, principal.getId());
            return id;
        });
        if (saved.isPresent()) {
            return "redirect:/posts/" + id;
        }
        // 입력값이 틀려도 남의 글 수정 폼을 보여 주면 안 되므로 권한을 다시 확인한다
        postService.getEditForm(id, principal.getId());
        addEditFormAttributes(id, model);
        return "post/form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        postService.delete(id, principal.getId());
        return "redirect:/posts";
    }

    private void addEditFormAttributes(Long postId, Model model) {
        model.addAttribute("postId", postId);
        model.addAttribute("existingImages", postImageService.getImages(postId));
        model.addAttribute("categories", categoryService.getCategories());
    }
}
