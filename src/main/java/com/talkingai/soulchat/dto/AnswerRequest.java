package com.talkingai.soulchat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRequest {

    @NotNull(message = "题号不能为空")
    private Integer questionNumber;

    @NotNull(message = "选项分数不能为空")
    private Integer selectedScore;
}
