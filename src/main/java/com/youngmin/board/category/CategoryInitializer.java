package com.youngmin.board.category;

import com.youngmin.board.post.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 시작 시 기본 카테고리를 만든다 (이미 있으면 건너뜀).
 * 카테고리 기능 전에 쓴 글(category_id가 NULL)은 "자유"로 옮긴다.
 *
 * posts.category_id를 DB에서 NOT NULL로 만들지 않은 이유:
 * ddl-auto: update가 이미 글이 있는 테이블에 NOT NULL 컬럼을 추가하면 기존 행에 0이 들어가고,
 * 0번 카테고리는 없어서 외래 키 제약을 만들지 못한다. 그래서 컬럼은 NULL 허용으로 두고,
 * 새 글은 애플리케이션(PostRequest의 @NotNull)이 반드시 카테고리를 넣는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryInitializer implements ApplicationRunner {

    private static final List<String> DEFAULT_CATEGORIES = List.of("자유", "질문", "정보");

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() == 0) {
            for (int i = 0; i < DEFAULT_CATEGORIES.size(); i++) {
                categoryRepository.save(Category.of(DEFAULT_CATEGORIES.get(i), i + 1));
            }
            log.info("기본 카테고리 생성: {}", DEFAULT_CATEGORIES);
        }
        Category defaultCategory = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0);
        int moved = postRepository.assignCategoryIfMissing(defaultCategory);
        if (moved > 0) {
            log.info("카테고리가 없던 게시글 {}개를 기본 카테고리로 옮김", moved);
        }
    }
}
