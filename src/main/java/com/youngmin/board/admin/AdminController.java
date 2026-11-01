package com.youngmin.board.admin;

import com.youngmin.board.category.CategoryService;
import com.youngmin.board.common.dto.PageNavigation;
import com.youngmin.board.image.ImageForms;
import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.post.PostService;
import com.youngmin.board.post.dto.PostRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 화면. /admin 아래 주소는 SecurityConfig에서 ADMIN만 들어올 수 있다.
 * 일반 회원이 /admin/notices/new를 직접 입력해도 Security가 403으로 막는다.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PostService postService;
    private final CategoryService categoryService;

    @GetMapping
    public String home() {
        return "admin/home";
    }

    @GetMapping("/members")
    public String members(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<AdminMemberResponse> members = adminService.getMembers(page);
        model.addAttribute("members", members);
        model.addAttribute("nav", PageNavigation.of(members));
        return "admin/members";
    }

    @GetMapping("/posts")
    public String posts(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<AdminPostResponse> posts = adminService.getPosts(page);
        model.addAttribute("posts", posts);
        model.addAttribute("nav", PageNavigation.of(posts));
        model.addAttribute("title", "게시글 관리 (삭제/숨김 포함)");
        model.addAttribute("listPath", "/admin/posts");
        return "admin/posts";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<AdminPostResponse> posts = adminService.getReportedPosts(page);
        model.addAttribute("posts", posts);
        model.addAttribute("nav", PageNavigation.of(posts));
        model.addAttribute("title", "신고된 게시글 (신고 많은 순)");
        model.addAttribute("listPath", "/admin/reports");
        return "admin/posts";
    }

    @PostMapping("/posts/{id}/hide")
    public String hide(@PathVariable Long id, @RequestParam(defaultValue = "/admin/posts") String returnTo) {
        adminService.hide(id);
        return "redirect:" + safeReturnPath(returnTo);
    }

    @PostMapping("/posts/{id}/restore")
    public String restore(@PathVariable Long id, @RequestParam(defaultValue = "/admin/posts") String returnTo) {
        adminService.restore(id);
        return "redirect:" + safeReturnPath(returnTo);
    }

    // ===== 공지 작성 =====

    @GetMapping("/notices/new")
    public String noticeForm(Model model) {
        model.addAttribute("postRequest", new PostRequest());
        addNoticeFormAttributes(model);
        return "post/form";
    }

    @PostMapping("/notices")
    public String createNotice(@Valid @ModelAttribute("postRequest") PostRequest request,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal MemberPrincipal principal,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        Optional<Long> postId = ImageForms.submit(bindingResult,
                () -> postService.createNotice(request, principal.getId()));
        if (postId.isPresent()) {
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 등록했습니다.");
            return "redirect:/posts/" + postId.get();
        }
        addNoticeFormAttributes(model);
        return "post/form";
    }

    private void addNoticeFormAttributes(Model model) {
        model.addAttribute("categories", categoryService.getCategories());
        model.addAttribute("noticeMode", true);
    }

    /** 리다이렉트 대상은 관리자 화면 안으로만 (외부 주소로 보내는 Open Redirect 방지) */
    private static String safeReturnPath(String returnTo) {
        return returnTo.startsWith("/admin/") && !returnTo.startsWith("//") ? returnTo : "/admin/posts";
    }
}
