package com.youngmin.board.category;

import com.youngmin.board.common.config.CacheConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 목록은 거의 바뀌지 않는데 모든 목록 화면(탭)과 글쓰기 화면에서 쓰인다.
 * → @Cacheable로 처음 한 번만 DB에서 읽고, 이후에는 메모리(Caffeine)에서 꺼낸다.
 *
 * 주의: @Cacheable은 프록시로 동작하므로, 같은 클래스 안에서 this.getCategories()로 부르면 캐시를 거치지 않는다.
 *       다른 빈(PostService 등)에서 호출해야 캐시가 적용된다.
 * 카테고리를 추가/수정하는 기능을 만들면 그 메서드에 @CacheEvict(cacheNames = CATEGORIES, allEntries = true)를
 * 붙여 캐시를 비워야 한다. (지금은 DB를 직접 고쳐도 1시간 뒤에 반영)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(CacheConfig.CATEGORIES)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
