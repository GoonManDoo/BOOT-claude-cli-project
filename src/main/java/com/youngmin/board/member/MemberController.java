package com.youngmin.board.member;

import com.youngmin.board.member.dto.MemberJoinRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/members/join")
    public String joinForm(Model model) {
        model.addAttribute("joinRequest", new MemberJoinRequest());
        return "member/join";
    }

    @PostMapping("/members/join")
    public String join(@Valid @ModelAttribute("joinRequest") MemberJoinRequest joinRequest,
                       BindingResult bindingResult) {
        // 형식 검증(@NotBlank, @Size, @Email) 실패 → 입력값을 유지한 채 폼을 다시 보여 준다.
        if (bindingResult.hasErrors()) {
            return "member/join";
        }

        try {
            memberService.join(joinRequest);
        } catch (DuplicateMemberException e) {
            // 중복 에러를 해당 필드의 에러로 등록 → th:errors로 화면에 표시된다.
            bindingResult.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "member/join";
        }

        return "redirect:/login?joined";
    }

    /**
     * 로그인 화면만 보여 준다.
     * POST /login(아이디/비밀번호 확인)은 Spring Security 필터가 가로채서 처리하므로 컨트롤러가 필요 없다.
     */
    @GetMapping("/login")
    public String loginForm() {
        return "member/login";
    }
}
