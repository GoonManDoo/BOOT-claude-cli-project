package com.youngmin.board.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업(고아 이미지 파일 정리)을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
