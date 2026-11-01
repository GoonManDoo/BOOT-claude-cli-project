package com.youngmin.board.image;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고아 이미지 파일 정리: 디스크에는 있는데 DB(post_images)에는 없는 파일을 지운다.
 *
 * 생기는 경우
 * - 글/이미지 삭제가 커밋된 뒤 파일 삭제에 실패했을 때 (파일이 다른 프로그램에 열려 있는 등)
 * - 파일을 저장한 직후 서버가 꺼져서 롤백 정리(afterCompletion)가 실행되지 못했을 때
 *
 * 만든 지 MIN_AGE(1시간)가 안 된 파일은 건드리지 않는다.
 * 방금 저장했고 아직 트랜잭션이 커밋되기 전인 파일도 "DB에 없는 파일"로 보이기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanImageCleaner {

    static final Duration MIN_AGE = Duration.ofHours(1);
    private static final int BATCH_SIZE = 500;

    private final ImageStorage imageStorage;
    private final PostImageRepository postImageRepository;
    private final Clock clock = Clock.systemDefaultZone();

    /** 매일 새벽 4시 (설정 board.upload.orphan-cleanup-cron으로 바꿀 수 있다) */
    @Scheduled(cron = "${board.upload.orphan-cleanup-cron:0 0 4 * * *}")
    public void scheduledCleanUp() {
        int deleted = cleanUp(clock.instant());
        log.info("고아 이미지 파일 정리 완료: {}개 삭제", deleted);
    }

    /** @return 지운 파일 수 */
    public int cleanUp(Instant now) {
        List<String> candidates = imageStorage.listFilesOlderThan(now.minus(MIN_AGE));
        int deleted = 0;
        // IN 조건이 너무 길어지지 않게 500개씩 나눠서 DB에 있는지 확인한다
        for (int from = 0; from < candidates.size(); from += BATCH_SIZE) {
            List<String> batch = candidates.subList(from, Math.min(from + BATCH_SIZE, candidates.size()));
            Set<String> registered = postImageRepository.findExistingStoredNames(batch);
            for (String name : batch) {
                if (!registered.contains(name) && imageStorage.delete(name)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }
}
