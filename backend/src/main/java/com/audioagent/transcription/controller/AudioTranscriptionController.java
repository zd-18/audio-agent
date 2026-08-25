package com.audioagent.transcription.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.common.api.PageResult;
import com.audioagent.transcription.dto.CreateTranscriptionTaskRequest;
import com.audioagent.transcription.dto.UpdateTranscriptSegmentRequest;
import com.audioagent.transcription.service.AudioTranscriptionService;
import com.audioagent.transcription.vo.TranscriptSegmentVO;
import com.audioagent.transcription.vo.TranscriptVO;
import com.audioagent.transcription.vo.TranscriptionTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/audio-transcriptions")
@RequiredArgsConstructor
public class AudioTranscriptionController {

    private final AudioTranscriptionService transcriptionService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/tasks")
    public ApiResponse<TranscriptionTaskVO> create(
            @Valid @RequestBody CreateTranscriptionTaskRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                transcriptionService.create(userId, request));
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TranscriptionTaskVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long audioFileId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(transcriptionService.list(
                userId, current, size, status, audioFileId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TranscriptionTaskVO> get(
            @PathVariable Long taskId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                transcriptionService.get(userId, taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<TranscriptionTaskVO> retry(
            @PathVariable Long taskId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                transcriptionService.retry(userId, taskId));
    }

    @GetMapping("/tasks/{taskId}/transcript")
    public ApiResponse<TranscriptVO> getTranscript(
            @PathVariable Long taskId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                transcriptionService.getTranscript(userId, taskId));
    }

    @GetMapping("/transcripts/{transcriptId}/segments")
    public ApiResponse<PageResult<TranscriptSegmentVO>> listSegments(
            @PathVariable Long transcriptId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(transcriptionService.listSegments(
                userId, transcriptId, current, size, keyword));
    }

    @PatchMapping("/transcripts/{transcriptId}/segments/{segmentId}")
    public ApiResponse<TranscriptSegmentVO> updateSegment(
            @PathVariable Long transcriptId,
            @PathVariable Long segmentId,
            @Valid @RequestBody UpdateTranscriptSegmentRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(transcriptionService.updateSegment(
                userId, transcriptId, segmentId, request));
    }

    @GetMapping("/transcripts/{transcriptId}/export")
    public ResponseEntity<byte[]> exportTranscript(
            @PathVariable Long transcriptId,
            @RequestParam(defaultValue = "txt") String format) {
        Long userId = currentUserProvider.requireUserId();
        var export = transcriptionService.exportTranscript(
                userId, transcriptId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(export.fileName(),
                                        StandardCharsets.UTF_8)
                                .build().toString())
                .body(export.content());
    }
}
