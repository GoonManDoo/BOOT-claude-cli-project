package com.youngmin.board.report;

import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.member.MemberPrincipal;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PostReportController {

    /** 사용자가 고칠 수 있는 실수 → 에러 화면 대신 상세 화면에 메시지로 보여 준다 */
    private static final Set<ErrorCode> SOFT_ERRORS = EnumSet.of(ErrorCode.ALREADY_REPORTED, ErrorCode.CANNOT_REPORT_OWN_POST);

    private final PostReportService postReportService;

    @PostMapping("/posts/{postId}/report")
    public String report(@PathVariable Long postId,
                         @Valid @ModelAttribute ReportRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("reportMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/posts/" + postId;
        }
        try {
            boolean hidden = postReportService.report(postId, principal.getId(), request.getReason());
            if (hidden) {
                redirectAttributes.addFlashAttribute("flashMessage", "신고가 누적되어 게시글이 숨김 처리되었습니다.");
                return "redirect:/posts";
            }
            redirectAttributes.addFlashAttribute("reportMessage", "신고가 접수되었습니다.");
        } catch (BusinessException e) {
            if (!SOFT_ERRORS.contains(e.getErrorCode())) {
                throw e;
            }
            redirectAttributes.addFlashAttribute("reportMessage", e.getMessage());
        }
        return "redirect:/posts/" + postId;
    }
}
