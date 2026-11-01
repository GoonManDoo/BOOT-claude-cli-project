package com.youngmin.board.home;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** 메인 화면은 게시글 목록이다. */
    @GetMapping("/")
    public String home() {
        return "redirect:/posts";
    }
}
