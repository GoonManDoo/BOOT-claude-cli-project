package com.youngmin.board.report;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReportRequest {

    @Size(max = 200, message = "신고 사유는 200자 이하로 입력해 주세요.")
    private String reason;
}
