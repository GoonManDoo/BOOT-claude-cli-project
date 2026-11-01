package com.youngmin.board.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.youngmin.board.category.Category;
import com.youngmin.board.category.CategoryRepository;
import com.youngmin.board.comment.Comment;
import com.youngmin.board.comment.CommentRepository;
import com.youngmin.board.image.ImageStorage;
import com.youngmin.board.image.PostImage;
import com.youngmin.board.image.PostImageRepository;
import com.youngmin.board.member.Member;
import com.youngmin.board.member.MemberPrincipal;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.dto.PostListResponse;
import com.youngmin.board.report.PostReportService;
import com.youngmin.board.support.TestImages;
import com.youngmin.board.support.TestMembers;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면 흐름 통합 테스트 (게시글/댓글/좋아요/이미지/신고/공지).
 * 실제 MySQL을 사용하므로 DB_PASSWORD 환경 변수가 필요하다. @Transactional로 테스트가 끝나면 롤백된다.
 *
 * 주의: 테스트 전체가 하나의 트랜잭션이라 영속성 컨텍스트(1차 캐시)를 공유한다.
 * DB에 실제로 반영된 값을 확인할 때는 em.flush() + em.clear()로 캐시를 비운 뒤 다시 조회한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostImageRepository postImageRepository;
    @Autowired ImageStorage imageStorage;
    @Autowired EntityManager em;

    Member writer;
    Member other;
    MemberPrincipal writerLogin;
    MemberPrincipal otherLogin;
    Category category;
    Post post;

    @BeforeEach
    void setUp() {
        writer = TestMembers.user(memberRepository, "작성자");
        other = TestMembers.user(memberRepository, "다른사람");
        writerLogin = new MemberPrincipal(writer);
        otherLogin = new MemberPrincipal(other);
        category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0);
        post = postRepository.save(Post.create("원래 제목", "원래 내용", writer, category));
    }

    // ===== 작성 =====

    @Test
    void 로그인한_회원이_글을_쓰면_상세로_리다이렉트된다() throws Exception {
        mockMvc.perform(post("/posts").with(user(writerLogin)).with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "새 글").param("content", "새 내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/posts/*"));
    }

    @Test
    void 비로그인_사용자는_글쓰기_화면에_들어갈_수_없다() throws Exception {
        mockMvc.perform(get("/posts/new")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 제목이_100자를_넘거나_내용이_비거나_카테고리가_없으면_폼을_다시_보여준다() throws Exception {
        mockMvc.perform(post("/posts").with(user(writerLogin)).with(csrf())
                        .param("title", "가".repeat(101)).param("content", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("post/form"))
                .andExpect(model().attributeHasFieldErrors("postRequest", "title", "content", "categoryId"));
    }

    // ===== 서버 측 권한 검증 =====

    @Test
    void 다른_회원이_남의_글_수정_URL에_직접_들어가면_403() throws Exception {
        mockMvc.perform(get("/posts/{id}/edit", post.getId()).with(user(otherLogin)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("작성자만 수정하거나 삭제할 수 있습니다.")));
    }

    @Test
    void 다른_회원이_수정_요청을_보내면_403이고_글은_바뀌지_않는다() throws Exception {
        mockMvc.perform(post("/posts/{id}/edit", post.getId()).with(user(otherLogin)).with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "해킹").param("content", "해킹"))
                .andExpect(status().isForbidden());

        em.flush();
        em.clear();
        assertThat(postRepository.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("원래 제목");
    }

    @Test
    void 작성자가_수정하면_save_없이도_변경_감지로_DB에_반영된다() throws Exception {
        mockMvc.perform(post("/posts/{id}/edit", post.getId()).with(user(writerLogin)).with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "바뀐 제목").param("content", "바뀐 내용"))
                .andExpect(redirectedUrl("/posts/" + post.getId()));

        em.flush(); // 변경 감지 → UPDATE 실행
        em.clear(); // 1차 캐시를 비우고 DB에서 다시 읽는다
        Post updated = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("바뀐 제목");
    }

    // ===== 소프트 삭제 =====

    @Test
    void 삭제한_글은_DB에_남아_있지만_목록과_상세에서는_보이지_않는다() throws Exception {
        commentRepository.save(Comment.create("댓글", other, post, null));

        mockMvc.perform(post("/posts/{id}/delete", post.getId()).with(user(writerLogin)).with(csrf()))
                .andExpect(redirectedUrl("/posts"));
        em.flush();
        em.clear();

        Post deleted = postRepository.findById(post.getId()).orElseThrow(); // findById는 삭제된 글도 찾는다
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(commentRepository.findAllWithAuthorByPostId(post.getId())).hasSize(1); // 댓글도 남아 있다

        mockMvc.perform(get("/posts/{id}", post.getId())).andExpect(status().isNotFound());
        assertThat(searchTitles("TITLE", "원래 제목")).isEmpty();
    }

    @Test
    void 다른_회원이_삭제_요청을_보내면_403() throws Exception {
        mockMvc.perform(post("/posts/{id}/delete", post.getId()).with(user(otherLogin)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ===== 목록 검색 =====

    @Test
    void 제목과_작성자로_검색할_수_있다() throws Exception {
        postRepository.save(Post.create("스프링 부트 질문", "내용", other, category));
        postRepository.save(Post.create("JPA 질문", "스프링 데이터", other, category));

        assertThat(searchTitles("TITLE", "스프링 부트 질문")).containsExactly("스프링 부트 질문");
        assertThat(searchTitles("TITLE_CONTENT", "스프링 데이터")).containsExactly("JPA 질문");
        assertThat(searchTitles("AUTHOR", other.getNickname())).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void 검색어의_퍼센트와_밑줄은_와일드카드가_아니라_글자_그대로_찾는다() throws Exception {
        postRepository.save(Post.create("100% 달성", "내용", writer, category));
        postRepository.save(Post.create("snake_case 이름", "내용", writer, category));

        // 이스케이프하지 않으면 "%"는 모든 글, "e_c"는 "eXc" 같은 글까지 찾는다
        assertThat(searchTitles("TITLE", "%")).contains("100% 달성").allMatch(title -> title.contains("%"));
        assertThat(searchTitles("TITLE", "e_c")).contains("snake_case 이름").allMatch(title -> title.contains("e_c"));
        assertThat(searchTitles("TITLE", "!")).isEmpty();
    }

    // ===== 조회수 =====

    @Test
    void 같은_세션으로_여러_번_조회해도_조회수는_한_번만_오르고_다른_세션은_따로_센다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/posts/{id}", post.getId()).session(session)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/posts/{id}", post.getId()).session(new MockHttpSession())).andExpect(status().isOk());

        em.clear();
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViewCount()).isEqualTo(2);
    }

    // ===== 댓글 (소프트 삭제) =====

    @Test
    void 대댓글이_있는_댓글을_지우면_삭제된_댓글입니다로_남고_대댓글은_유지된다() throws Exception {
        Comment parent = commentRepository.save(Comment.create("부모 댓글", writer, post, null));
        commentRepository.save(Comment.create("살아있는 대댓글", other, post, parent));

        mockMvc.perform(post("/comments/{id}/delete", parent.getId()).with(user(writerLogin)).with(csrf()))
                .andExpect(redirectedUrl("/posts/" + post.getId() + "#comments"));

        em.flush();
        em.clear();
        assertThat(commentRepository.findById(parent.getId()).orElseThrow().isDeleted()).isTrue();
        mockMvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(content().string(containsString("삭제된 댓글입니다")))
                .andExpect(content().string(containsString("살아있는 대댓글")));
    }

    @Test
    void 대댓글까지_모두_지우면_화면에서_사라지지만_행은_남는다() throws Exception {
        Comment parent = commentRepository.save(Comment.create("부모 댓글", writer, post, null));
        Comment reply = commentRepository.save(Comment.create("대댓글", other, post, parent));
        mockMvc.perform(post("/comments/{id}/delete", parent.getId()).with(user(writerLogin)).with(csrf()));
        mockMvc.perform(post("/comments/{id}/delete", reply.getId()).with(user(otherLogin)).with(csrf()));

        em.flush();
        em.clear();
        assertThat(commentRepository.findAllWithAuthorByPostId(post.getId())).hasSize(2);
        mockMvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(content().string(not(containsString("삭제된 댓글입니다"))));
    }

    @Test
    void 대댓글에_다시_답글을_달면_400() throws Exception {
        Comment parent = commentRepository.save(Comment.create("부모 댓글", writer, post, null));
        Comment reply = commentRepository.save(Comment.create("대댓글", other, post, parent));

        mockMvc.perform(post("/posts/{id}/comments", post.getId()).with(user(writerLogin)).with(csrf())
                        .param("content", "대대댓글").param("parentId", reply.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 남의_댓글은_수정도_삭제도_할_수_없다() throws Exception {
        Comment comment = commentRepository.save(Comment.create("내 댓글", writer, post, null));

        mockMvc.perform(post("/comments/{id}/edit", comment.getId()).with(user(otherLogin)).with(csrf())
                        .param("content", "바꿈"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/comments/{id}/delete", comment.getId()).with(user(otherLogin)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ===== 좋아요 =====

    @Test
    void 좋아요를_두_번_보내도_1개만_저장되고_취소를_두_번_보내도_0_아래로_내려가지_않는다() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/posts/{id}/like", post.getId()).with(user(otherLogin)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true))
                    .andExpect(jsonPath("$.likeCount").value(1));
        }
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/posts/{id}/like", post.getId()).with(user(otherLogin)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false))
                    .andExpect(jsonPath("$.likeCount").value(0));
        }
    }

    @Test
    void 비로그인_사용자가_좋아요를_보내면_로그인_화면_대신_401() throws Exception {
        mockMvc.perform(post("/posts/{id}/like", post.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ===== 이미지 첨부 =====

    @Test
    void 이미지를_올리면_UUID_이름으로_저장되고_원래_이름은_DB에_남는다() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/posts").file(TestImages.png("images", "내 사진.png"))
                        .param("categoryId", category.getId().toString())
                        .param("title", "사진 글").param("content", "내용")
                        .with(user(writerLogin)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long postId = idFromRedirect(result);

        List<PostImage> images = postImageRepository.findAllByPostIdOrderByIdAsc(postId);
        assertThat(images).hasSize(1);
        assertThat(images.get(0).getOriginalName()).isEqualTo("내 사진.png");
        assertThat(images.get(0).getStoredName()).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(imageStorage.getRoot().resolve(images.get(0).getStoredName()))).isTrue();

        mockMvc.perform(get("/post-images/{id}", images.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void 확장자만_jpg로_바꾼_다른_파일은_거절되고_글_저장도_롤백된다() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("images", "virus.jpg", "image/jpeg",
                "MZ this is not an image".getBytes());

        mockMvc.perform(multipart("/posts").file(fake)
                        .param("categoryId", category.getId().toString())
                        .param("title", "가짜 이미지").param("content", "내용")
                        .with(user(writerLogin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("post/form"))
                .andExpect(model().attributeHasFieldErrors("postRequest", "images"));

        // 실제 요청이라면 서비스 트랜잭션이 바로 롤백된다. 이 테스트는 전체가 하나의 트랜잭션이라
        // "롤백 예정(rollback-only)" 표시가 붙었는지로 확인한다. (글 INSERT도 함께 취소된다는 뜻)
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();
    }

    @Test
    void 이미지는_6장_이상_올릴_수_없다() throws Exception {
        var request = multipart("/posts");
        for (int i = 0; i < 6; i++) {
            request.file(TestImages.png("images", "img" + i + ".png"));
        }
        mockMvc.perform(request.param("categoryId", category.getId().toString())
                        .param("title", "사진 6장").param("content", "내용")
                        .with(user(writerLogin)).with(csrf()))
                .andExpect(model().attributeHasFieldErrors("postRequest", "images"));
    }

    // ===== 신고 / 숨김 =====

    @Test
    void 같은_글을_두_번_신고하면_두_번째는_거절되고_신고_5번이면_자동으로_숨겨진다(
            @Autowired PostReportService postReportService) throws Exception {
        postReportService.report(post.getId(), other.getId(), "광고");
        mockMvc.perform(post("/posts/{id}/report", post.getId()).with(user(otherLogin)).with(csrf()))
                .andExpect(redirectedUrl("/posts/" + post.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("reportMessage", "이미 신고한 게시글입니다."));

        boolean hidden = false;
        for (int i = 0; i < 4; i++) {
            hidden = postReportService.report(post.getId(), TestMembers.user(memberRepository, "신고자" + i).getId(), null);
        }
        assertThat(hidden).isTrue(); // 다섯 번째 신고에서 숨김

        em.flush();
        em.clear();
        Post reported = postRepository.findById(post.getId()).orElseThrow();
        assertThat(reported.getReportCount()).isEqualTo(5);
        assertThat(reported.isHidden()).isTrue();
        mockMvc.perform(get("/posts/{id}", post.getId())).andExpect(status().isNotFound());
    }

    @Test
    void 자기_글은_신고할_수_없다() throws Exception {
        mockMvc.perform(post("/posts/{id}/report", post.getId()).with(user(writerLogin)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("reportMessage", "자신의 글은 신고할 수 없습니다."));
    }

    // ===== 공지 =====

    @Test
    void 관리자가_쓴_공지는_모든_페이지_맨_위에_보인다() throws Exception {
        MemberPrincipal admin = new MemberPrincipal(TestMembers.admin(memberRepository, "관리자"));
        mockMvc.perform(post("/admin/notices").with(user(admin)).with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "필독 공지").param("content", "공지 내용"))
                .andExpect(redirectedUrlPattern("/posts/*"));

        mockMvc.perform(get("/posts").param("page", "999"))
                .andExpect(content().string(containsString("필독 공지")));
    }

    @Test
    void 일반_회원이_공지_작성_요청을_보내면_403() throws Exception {
        mockMvc.perform(post("/admin/notices").with(user(writerLogin)).with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "가짜 공지").param("content", "내용"))
                .andExpect(status().isForbidden());
    }

    // ===== 도우미 =====

    @SuppressWarnings("unchecked")
    private List<String> searchTitles(String type, String keyword) throws Exception {
        MvcResult result = mockMvc.perform(get("/posts").param("type", type).param("keyword", keyword)).andReturn();
        Page<PostListResponse> page = (Page<PostListResponse>) result.getModelAndView().getModel().get("posts");
        return page.getContent().stream().map(PostListResponse::getTitle).toList();
    }

    private static Long idFromRedirect(MvcResult result) {
        String url = result.getResponse().getRedirectedUrl();
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
    }
}
