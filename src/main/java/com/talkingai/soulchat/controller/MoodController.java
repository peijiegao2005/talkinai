package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.entity.MoodRoom;
import com.talkingai.soulchat.service.MoodAssessmentService;
import com.talkingai.soulchat.service.MoodRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Slf4j
@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodAssessmentService assessmentService;
    private final MoodRoomService roomService;

    // ==================== 心情评测 ====================

    @PostMapping("/assessment/start")
    public Mono<ApiResponse<MoodAssessmentService.MoodSessionResponse>> startAssessment(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户开始心情评测: {}", userId);

        return assessmentService.startAssessment(userId)
                .map(ApiResponse::success);
    }

    @PostMapping("/assessment/answer")
    public Mono<ApiResponse<MoodAssessmentService.AnswerResponse>> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest request) {

        return assessmentService.submitAnswer(request.getSessionId(), request.getQuestionNumber(), request.getSelectedOption())
                .map(ApiResponse::success);
    }

    @PostMapping("/assessment/complete")
    public Mono<ApiResponse<MoodAssessmentService.MoodResultResponse>> completeAssessment(
            @Valid @RequestBody CompleteAssessmentRequest request) {

        return assessmentService.completeAssessment(request.getSessionId())
                .map(ApiResponse::success);
    }

    // ==================== 聊天室 ====================

    @GetMapping("/rooms")
    public Flux<ApiResponse<MoodRoom>> getAllRooms() {
        return roomService.getAllRooms()
                .map(ApiResponse::success);
    }

    @GetMapping("/rooms/{roomId}")
    public Mono<ApiResponse<MoodRoomService.RoomDetailResponse>> getRoomDetail(@PathVariable String roomId) {
        return roomService.getRoomDetail(roomId)
                .map(ApiResponse::success);
    }

    @PostMapping("/rooms/{roomId}/join")
    public Mono<ApiResponse<Void>> joinRoom(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody JoinRoomRequest request) {

        String userId = authentication.getPrincipal().toString();
        log.info("用户 {} 加入聊天室 {}", userId, roomId);

        return roomService.joinRoom(userId, roomId, request.getCurrentMood(), request.getAllowDirectMessage())
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/rooms/leave")
    public Mono<ApiResponse<Void>> leaveRoom(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户 {} 离开聊天室", userId);

        return roomService.leaveRoom(userId)
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/rooms/{roomId}/message")
    public Mono<ApiResponse<Void>> sendMessage(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request) {

        String userId = authentication.getPrincipal().toString();

        return roomService.sendMessage(userId, roomId, request.getContent())
                .thenReturn(ApiResponse.success(null));
    }

    @GetMapping("/rooms/{roomId}/history")
    public Flux<ApiResponse<MoodRoomService.ChatMessageDTO>> getRoomHistory(@PathVariable String roomId) {
        return roomService.getRoomHistory(roomId)
                .map(msg -> MoodRoomService.ChatMessageDTO.builder()
                        .id(msg.getId())
                        .userId(msg.getUserId())
                        .nickname(msg.getNickname())
                        .avatar(msg.getAvatar())
                        .content(msg.getContent())
                        .messageType(msg.getMessageType())
                        .timestamp(msg.getTimestamp())
                        .build())
                .map(ApiResponse::success);
    }

    // ==================== 一对一邀请 ====================

    @PostMapping("/rooms/{roomId}/invite")
    public Mono<ApiResponse<Void>> sendInvite(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody SendInviteRequest request) {

        String fromUserId = authentication.getPrincipal().toString();
        log.info("用户 {} 向 {} 发送私信邀请", fromUserId, request.getToUserId());

        return roomService.sendDirectMessageInvite(fromUserId, request.getToUserId(), roomId)
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/rooms/{roomId}/invite/respond")
    public Mono<ApiResponse<Void>> respondToInvite(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody RespondInviteRequest request) {

        String userId = authentication.getPrincipal().toString();
        log.info("用户 {} {} 了来自 {} 的邀请", userId, request.getAccept() ? "接受" : "拒绝", request.getFromUserId());

        return roomService.respondToInvite(userId, roomId, request.getFromUserId(), request.getAccept())
                .thenReturn(ApiResponse.success(null));
    }

    // ==================== Request DTOs ====================

    @lombok.Data
    public static class SubmitAnswerRequest {
        @NotBlank
        private String sessionId;
        private Integer questionNumber;
        @NotBlank
        private String selectedOption;
    }

    @lombok.Data
    public static class CompleteAssessmentRequest {
        @NotBlank
        private String sessionId;
    }

    @lombok.Data
    public static class JoinRoomRequest {
        private String currentMood;
        private Boolean allowDirectMessage;
    }

    @lombok.Data
    public static class SendMessageRequest {
        @NotBlank
        private String content;
    }

    @lombok.Data
    public static class SendInviteRequest {
        @NotBlank
        private String toUserId;
    }

    @lombok.Data
    public static class RespondInviteRequest {
        @NotBlank
        private String fromUserId;
        private Boolean accept;
    }
}
