package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.service.AiMoodAssessmentService;
import com.talkingai.soulchat.service.MoodAssessmentService;
import com.talkingai.soulchat.service.MoodRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodAssessmentService moodAssessmentService;
    private final AiMoodAssessmentService aiMoodAssessmentService;
    private final MoodRoomService moodRoomService;

    // ==================== 心情评测 (选择题模式) ====================

    @PostMapping("/assessment/start")
    public Mono<ApiResponse<MoodAssessmentService.MoodSessionResponse>> startAssessment(Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return moodAssessmentService.startAssessment(userId)
                .map(ApiResponse::success);
    }

    @PostMapping("/assessment/answer")
    public Mono<ApiResponse<MoodAssessmentService.AnswerResponse>> submitAnswer(
            Authentication auth,
            @RequestBody MoodAnswerRequest request) {
        return moodAssessmentService.submitAnswer(
                        request.getSessionId(),
                        request.getQuestionNumber(),
                        request.getSelectedOption())
                .map(ApiResponse::success);
    }

    @PostMapping("/assessment/complete")
    public Mono<ApiResponse<MoodAssessmentService.MoodResultResponse>> completeAssessment(
            Authentication auth,
            @RequestBody MoodCompleteRequest request) {
        return moodAssessmentService.completeAssessment(request.getSessionId())
                .map(ApiResponse::success);
    }

    // ==================== AI对话式心情评测 ====================

    @PostMapping("/ai-assessment/start")
    public Mono<ApiResponse<AiMoodAssessmentService.StartAssessmentResult>> startAiAssessment(
            Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return aiMoodAssessmentService.startAiAssessment(userId)
                .map(ApiResponse::success);
    }

    @PostMapping("/ai-assessment/dialog")
    public Mono<ApiResponse<AiMoodAssessmentService.DialogResponse>> submitDialog(
            Authentication auth,
            @RequestBody MoodDialogRequest request) {
        return aiMoodAssessmentService.submitDialogMessage(
                        request.getSessionId(),
                        request.getUserMessage())
                .map(ApiResponse::success);
    }

    @PostMapping("/ai-assessment/fallback-answer")
    public Mono<ApiResponse<AiMoodAssessmentService.DialogResponse>> submitFallbackAnswer(
            Authentication auth,
            @RequestBody MoodAnswerRequest request) {
        return aiMoodAssessmentService.submitFallbackAnswer(
                        request.getSessionId(),
                        request.getQuestionNumber(),
                        request.getSelectedOption())
                .map(ApiResponse::success);
    }

    // ==================== 聊天室 ====================

    @GetMapping("/rooms")
    public Mono<ApiResponse<java.util.List<com.talkingai.soulchat.entity.MoodRoom>>> getAllRooms() {
        return moodRoomService.getAllRooms()
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/rooms/{roomId}")
    public Mono<ApiResponse<MoodRoomService.RoomDetailResponse>> getRoomDetail(@PathVariable String roomId) {
        return moodRoomService.getRoomDetail(roomId)
                .map(ApiResponse::success);
    }

    @PostMapping("/rooms/{roomId}/join")
    public Mono<ApiResponse<Void>> joinRoom(
            Authentication auth,
            @PathVariable String roomId,
            @RequestBody JoinRoomRequest request) {
        String userId = auth.getPrincipal().toString();
        return moodRoomService.joinRoom(userId, roomId,
                        request.getCurrentMood(), request.getAllowDirectMessage())
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/rooms/leave")
    public Mono<ApiResponse<Void>> leaveRoom(Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return moodRoomService.leaveRoom(userId)
                .thenReturn(ApiResponse.success(null));
    }

    @GetMapping("/rooms/{roomId}/history")
    public Mono<ApiResponse<java.util.List<com.talkingai.soulchat.entity.MoodChatMessage>>> getRoomHistory(
            @PathVariable String roomId) {
        return moodRoomService.getRoomHistory(roomId)
                .collectList()
                .map(ApiResponse::success);
    }

    @PostMapping("/rooms/{roomId}/invite")
    public Mono<ApiResponse<Void>> sendInvite(
            Authentication auth,
            @PathVariable String roomId,
            @RequestBody InviteRequest request) {
        String userId = auth.getPrincipal().toString();
        return moodRoomService.sendDirectMessageInvite(userId,
                        request.getToUserId(), roomId)
                .thenReturn(ApiResponse.success(null));
    }

    // ==================== 请求体 ====================

    @lombok.Data
    public static class MoodAnswerRequest {
        private String sessionId;
        private Integer questionNumber;
        private String selectedOption;
    }

    @lombok.Data
    public static class MoodCompleteRequest {
        private String sessionId;
    }

    @lombok.Data
    public static class MoodDialogRequest {
        private String sessionId;
        private String userMessage;
    }

    @lombok.Data
    public static class JoinRoomRequest {
        private String currentMood;
        private Boolean allowDirectMessage;
    }

    @lombok.Data
    public static class InviteRequest {
        private String toUserId;
    }
}
