package com.youngmin.board.common.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.member.MemberService;
import com.youngmin.board.member.dto.MemberJoinRequest;
import com.youngmin.board.support.TestMembers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * SecurityConfig 동작 확인.
 * 실제 MySQL을 사용하므로 DB_PASSWORD 환경 변수가 필요하다.
 * @Transactional로 테스트가 끝나면 가입한 회원은 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 홈은_게시글_목록으로_보내고_목록은_비로그인도_볼_수_있다() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/posts"));
        mockMvc.perform(get("/posts")).andExpect(status().isOk());
    }

    @Test
    void 비로그인_사용자가_관리자_페이지에_가면_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USER는_관리자_페이지에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    void ADMIN은_관리자_페이지에_접근할_수_있다() throws Exception {
        // 화면 상단 메뉴가 닉네임을 쓰므로 @WithMockUser 대신 실제 MemberPrincipal로 로그인한다
        MemberPrincipal admin = new MemberPrincipal(TestMembers.admin(memberRepository, "관리자"));
        mockMvc.perform(get("/admin").with(user(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/members").with(user(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/posts").with(user(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/reports").with(user(admin))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원이_공지_작성_URL에_직접_들어가면_403() throws Exception {
        mockMvc.perform(get("/admin/notices/new")).andExpect(status().isForbidden());
    }

    @Test
    void 가입한_회원은_올바른_비밀번호로_로그인할_수_있다() throws Exception {
        memberService.join(joinRequest("logintest", "password123"));

        mockMvc.perform(formLogin("/login").userParameter("loginId").user("logintest").password("password123"))
                .andExpect(authenticated().withUsername("logintest").withRoles("USER"))
                .andExpect(redirectedUrl("/posts"));
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() throws Exception {
        memberService.join(joinRequest("logintest", "password123"));

        mockMvc.perform(formLogin("/login").userParameter("loginId").user("logintest").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser
    void 로그아웃하면_목록으로_보낸다() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/posts?logout"));
    }

    private MemberJoinRequest joinRequest(String loginId, String password) {
        MemberJoinRequest request = new MemberJoinRequest();
        request.setLoginId(loginId);
        request.setPassword(password);
        request.setNickname("테스트");
        request.setEmail(loginId + "@example.com");
        return request;
    }
}
