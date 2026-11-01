package com.youngmin.board.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 메모리 캐시(Caffeine).
 * 캐시마다 만료 시간을 다르게 준다.
 * - categories: 카테고리 목록. 거의 안 바뀌지만, DB를 직접 고쳤을 때도 1시간 뒤엔 반영되게.
 * - holidays: 연도별 공휴일. 하루에 한 번만 외부 API를 부른다.
 *
 * 서버가 여러 대면 서버마다 캐시를 따로 가진다. 모든 서버가 같은 캐시를 봐야 하면 Redis 같은 공용 캐시를 쓴다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORIES = "categories";
    public static final String HOLIDAYS = "holidays";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(CATEGORIES,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10).build());
        cacheManager.registerCustomCache(HOLIDAYS,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(20).build());
        return cacheManager;
    }
}
