package com.youngmin.board.image;

import com.youngmin.board.member.MemberPrincipal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 이미지 보기. 저장 폴더를 정적 리소스로 통째로 열지 않고, DB에 등록된 이미지만 id로 내려 준다.
 * Content-Type은 업로드 때 확인한 실제 형식으로 정한다. (Security가 X-Content-Type-Options: nosniff도 붙여 준다)
 */
@Controller
@RequiredArgsConstructor
public class PostImageController {

    private final PostImageService postImageService;

    @GetMapping("/post-images/{id}")
    public ResponseEntity<Resource> image(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        boolean admin = principal != null && principal.isAdmin();
        PostImageService.ImageFile file = postImageService.load(id, admin);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file.resource());
    }
}
