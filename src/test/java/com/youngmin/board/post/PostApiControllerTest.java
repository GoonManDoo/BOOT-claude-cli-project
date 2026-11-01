package com.youngmin.board.post;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youngmin.board.category.Category;
import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.member.MemberService;
import com.youngmin.board.member.dto.MemberJoinRequest;
import com.youngmin.board.support.TestMembers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * REST API 테스트: HTTP 상태 코드와 응답 JSON 모양(success, data, error.code)을 확인한다.
 * 실제 Security 필터(HTTP Basic)를 거치도록 MockMvc로 호출한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired CategoryRepository categoryRepository;

    Category category;
    MemberPrincipal writer;
    Post post;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0);
        // HTTP Basic은 실제 비밀번호(BCrypt)를 확인하므로 회원가입 서비스로 만든다
        MemberJoinRequest join = new MemberJoinRequest();
        join.setLoginId("apiwriter");
        join.setPassword("password123");
        join.setNickname("API작성자");
        join.setEmail("apiwriter@example.com");
        Long writerId = memberService.join(join);
        writer = new MemberPrincipal(memberRepository.findById(writerId).orElseThrow());
        post = postRepository.save(Post.create("API 글", "API 내용", memberRepository.getReferenceById(writerId), category));
    }

    @Test
    void 목록은_로그인_없이_200과_공통_응답_형식() throws Exception {
        mockMvc.perform(get("/api/posts").param("categoryId", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 상세는_200() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("API 글"))
                .andExpect(jsonPath("$.data.authorNickname").value("API작성자"));
    }

    @Test
    void 없는_글은_404와_에러_코드() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", 99999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 작성은_HTTP_Basic_인증_후_201과_Location_헤더() throws Exception {
        mockMvc.perform(post("/api/posts").with(httpBasic("apiwriter", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "새 API 글", "content": "본문"}
                                """.formatted(category.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/posts/\\d+")))
                .andExpect(jsonPath("$.data.title").value("새 API 글"));
    }

    @Test
    void 인증_없이_작성하면_401_JSON() throws Exception {
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 비밀번호가_틀리면_401_JSON() throws Exception {
        mockMvc.perform(post("/api/posts").with(httpBasic("apiwriter", "wrong"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                // 이 헤더가 없어야 브라우저가 로그인 창을 띄우거나 인증 정보를 기억하지 않는다 (CSRF 방어)
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void HTML_폼_형식으로_작성하면_415로_거절한다() throws Exception {
        // 다른 사이트가 사용자 몰래 보낼 수 있는 형식(form-urlencoded)은 받지 않는다
        mockMvc.perform(post("/api/posts").with(httpBasic("apiwriter", "password123"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("categoryId", category.getId().toString())
                        .param("title", "폼으로 보냄").param("content", "본문"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 검증_실패는_400과_필드별_에러() throws Exception {
        mockMvc.perform(post("/api/posts").with(user(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "", "content": "본문"}
                                """.formatted(category.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("title"));
    }

    @Test
    void JSON_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/api/posts").with(user(writer))
                        .contentType(MediaType.APPLICATION_JSON).content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 남의_글을_수정하면_403과_NOT_AUTHOR() throws Exception {
        MemberPrincipal other = new MemberPrincipal(TestMembers.user(memberRepository, "남"));
        mockMvc.perform(put("/api/posts/{id}", post.getId()).with(user(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "해킹", "content": "해킹"}
                                """.formatted(category.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_AUTHOR"));
    }

    @Test
    void 작성자가_수정하면_200_삭제하면_204() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", post.getId()).with(user(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "고친 제목", "content": "고친 본문"}
                                """.formatted(category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("고친 제목"));

        mockMvc.perform(delete("/api/posts/{id}", post.getId()).with(user(writer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_API_주소는_404_JSON() throws Exception {
        mockMvc.perform(get("/api/nothing-here").with(user(writer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(header().string("Content-Type", startsWith("application/json")));
    }
}
