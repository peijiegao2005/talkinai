package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.*;
import com.talkingai.soulchat.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/start")
    public Mono<ApiResponse<StartAssessmentResponse>> startAssessment(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("Starting assessment for user: {}", userId);

        return assessmentService.startAssessment(userId)
                .map(ApiResponse::success)
                .doOnError(e -> log.error("Failed to start assessment for user: {}", userId, e));
    }

    @PostMapping("/answer")
    public Mono<ApiResponse<AnswerResponse>> submitAnswer(
            Authentication authentication,
            @Valid @RequestBody AnswerRequest request) {
        String userId = authentication.getPrincipal().toString();
        log.info("User {} submitting answer for question {}", userId, request.getQuestionNumber());

        return assessmentService.submitAnswer(userId, request.getQuestionNumber(), request.getSelectedScore())
                .map(ApiResponse::success)
                .doOnError(e -> log.error("Failed to submit answer for user: {}", userId, e));
    }

    @PostMapping("/complete")
    public Mono<ApiResponse<AssessmentResultResponse>> completeAssessment(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("Completing assessment for user: {}", userId);

        return assessmentService.getAssessmentResult(userId)
                .map(ApiResponse::success)
                .doOnError(e -> log.error("Failed to complete assessment for user: {}", userId, e));
    }

    @GetMapping("/result")
    public Mono<ApiResponse<AssessmentResultResponse>> getAssessmentResult(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("Getting assessment result for user: {}", userId);

        return assessmentService.getLatestReport(userId)
                .map(ApiResponse::success)
                .doOnError(e -> log.error("Failed to get assessment result for user: {}", userId, e));
    }

    @GetMapping("/status")
    public Mono<ApiResponse<Boolean>> hasAssessment(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();

        return assessmentService.getLatestReport(userId)
                .map(result -> ApiResponse.success(result.getCompleted() != null && result.getCompleted()))
                .doOnError(e -> log.error("Failed to check assessment status for user: {}", userId, e));
    }

    @PostMapping("/restart")
    public Mono<ApiResponse<StartAssessmentResponse>> restartAssessment(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("Restarting assessment for user: {}", userId);

        return assessmentService.restartAssessment(userId)
                .map(ApiResponse::success)
                .onErrorResume(e -> {
                    log.error("Failed to restart assessment for user: {}", userId, e);
                    return Mono.just(ApiResponse.error(500, "重新评估失败: " + e.getMessage()));
                });
    }

    // 测试端点，用于验证服务器是否加载了最新代码
    @GetMapping("/test")
    public Mono<ApiResponse<String>> test() {
        return Mono.just(ApiResponse.success("AssessmentController is working!"));
    }
}
