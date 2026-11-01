package com.youngmin.board.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * application.yaml의 board.upload.* 설정.
 * 저장 경로를 코드에 쓰지 않고 설정 파일(+ 환경 변수 UPLOAD_DIR)로 뺐다.
 */
@ConfigurationProperties("board.upload")
public record UploadProperties(String dir, DataSize maxFileSize, int maxCount) {
}
