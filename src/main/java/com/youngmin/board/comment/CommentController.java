package com.youngmin.board.comment;

import com.youngmin.board.comment.dto.CommentRequest;
import com.youngmin.board.member.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 댓글은 게시글 상세 화면 안에서 쓰고 고치고 지운다.
 * 처리 후에는 항상 게시글 상세로 redirect 한다 (PRG 패턴).
 * 입력값이 잘못됐으면 상세 화면을 다시 그리는 대신, 에러 메시지를 flash로 넘기고 redirect 한다.
 */
@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public String create(@PathVariable Long postId,
                         @Valid @ModelAttribute CommentRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError", firstError(bindingResult));
            return "redirect:/posts/" + postId + "#comments";
        }
        Long commentId = commentService.create(postId, request, principal.getId());
        return "redirect:/posts/" + postId + "#comment-" + commentId;
    }

    @PostMapping("/comments/{commentId}/edit")
    public String update(@PathVariable Long commentId,
                         @Valid @ModelAttribute CommentRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError", firstError(bindingResult));
            Long postId = commentService.getPostId(commentId);
            return "redirect:/posts/" + postId + "#comments";
        }
        Long postId = commentService.update(commentId, request, principal.getId());
        return "redirect:/posts/" + postId + "#comment-" + commentId;
    }

    @PostMapping("/comments/{commentId}/delete")
    public String delete(@PathVariable Long commentId, @AuthenticationPrincipal MemberPrincipal principal) {
        Long postId = commentService.delete(commentId, principal.getId());
        return "redirect:/posts/" + postId + "#comments";
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getAllErrors().get(0).getDefaultMessage();
    }
}
