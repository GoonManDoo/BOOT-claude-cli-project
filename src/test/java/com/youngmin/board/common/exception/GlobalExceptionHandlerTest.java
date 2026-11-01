package com.youngmin.board.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ModelAndView;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void BusinessException은_ErrorCode의_상태코드와_메시지로_에러화면을_만든다() {
        ModelAndView mav = new GlobalExceptionHandler()
                .handleBusinessException(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mav.getModel()).containsEntry("errorMessage", "회원을 찾을 수 없습니다.");
    }

    @Test
    @WithMockUser
    void 없는_주소는_404_에러화면을_보여준다() throws Exception {
        mockMvc.perform(get("/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("errorMessage", "페이지를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    void 지원하지_않는_HTTP_메서드는_405_에러화면을_보여준다() throws Exception {
        mockMvc.perform(delete("/").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(view().name("error"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 권한_예외는_삼키지_않고_Security가_403으로_처리한다() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }
}
