package com.audioagent.file.multipart;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MultipartUploadStateStore {

    private static final String STATE_PREFIX = "audio:multipart:state:";
    private static final String CHUNKS_PREFIX = "audio:multipart:chunks:";
    private static final String LOCK_PREFIX = "audio:multipart:lock:";
    private static final String RESUME_PREFIX = "audio:multipart:resume:";
    private static final String CLEANUP_KEY = "audio:multipart:cleanup";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final MultipartUploadProperties properties;

    public void create(MultipartUploadState state) {
        Map<String, String> values = new HashMap<>();
        values.put("uploadId", state.getUploadId());
        values.put("userId", state.getUserId().toString());
        values.put("originalName", state.getOriginalName());
        values.put("extension", state.getExtension());
        values.put("mimeType", state.getMimeType());
        values.put("sizeBytes", state.getSizeBytes().toString());
        values.put("sha256", state.getSha256());
        values.put("resumeFingerprint", state.getResumeFingerprint());
        values.put("chunkSize", state.getChunkSize().toString());
        values.put("totalChunks", state.getTotalChunks().toString());
        values.put("finalObjectKey", state.getFinalObjectKey());
        values.put("status", state.getStatus().name());
        values.put("createdAt", state.getCreatedAt().toString());
        values.put("updatedAt", state.getUpdatedAt().toString());
        redisTemplate.opsForHash().putAll(stateKey(state.getUploadId()), values);
        redisTemplate.opsForValue().set(
                resumeKey(state.getResumeFingerprint()), state.getUploadId());
        touch(state);
    }

    public Optional<MultipartUploadState> find(String uploadId) {
        Map<Object, Object> values = redisTemplate.opsForHash()
                .entries(stateKey(uploadId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(MultipartUploadState.builder()
                .uploadId(value(values, "uploadId"))
                .userId(Long.valueOf(value(values, "userId")))
                .originalName(value(values, "originalName"))
                .extension(value(values, "extension"))
                .mimeType(value(values, "mimeType"))
                .sizeBytes(Long.valueOf(value(values, "sizeBytes")))
                .sha256(value(values, "sha256"))
                .resumeFingerprint(optionalString(values,
                        "resumeFingerprint"))
                .chunkSize(Long.valueOf(value(values, "chunkSize")))
                .totalChunks(Integer.valueOf(value(values, "totalChunks")))
                .finalObjectKey(value(values, "finalObjectKey"))
                .status(MultipartUploadStatus.valueOf(value(values, "status")))
                .audioFileId(optionalLong(values, "audioFileId"))
                .createdAt(Long.valueOf(value(values, "createdAt")))
                .updatedAt(Long.valueOf(value(values, "updatedAt")))
                .build());
    }

    public Optional<String> findResumeUploadId(String fingerprint) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(
                resumeKey(fingerprint)));
    }

    public void bindResumeSession(MultipartUploadState state,
                                  String fingerprint) {
        state.setResumeFingerprint(fingerprint);
        redisTemplate.opsForHash().put(stateKey(state.getUploadId()),
                "resumeFingerprint", fingerprint);
        redisTemplate.opsForValue().set(resumeKey(fingerprint),
                state.getUploadId());
        touch(state);
    }

    public void removeResumeSession(String fingerprint, String uploadId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                List.of(resumeKey(fingerprint)), uploadId);
    }

    public List<Integer> uploadedChunks(String uploadId) {
        Set<String> members = redisTemplate.opsForSet()
                .members(chunksKey(uploadId));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(Integer::valueOf)
                .sorted()
                .toList();
    }

    public boolean isChunkUploaded(String uploadId, int chunkIndex) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet()
                .isMember(chunksKey(uploadId), Integer.toString(chunkIndex)));
    }

    public void markChunkUploaded(MultipartUploadState state, int chunkIndex) {
        redisTemplate.opsForSet().add(
                chunksKey(state.getUploadId()), Integer.toString(chunkIndex));
        updateStatus(state, MultipartUploadStatus.UPLOADING);
    }

    public void clearUploadedChunks(String uploadId) {
        redisTemplate.delete(chunksKey(uploadId));
    }

    public void updateStatus(MultipartUploadState state,
                             MultipartUploadStatus status) {
        state.setStatus(status);
        state.setUpdatedAt(System.currentTimeMillis());
        redisTemplate.opsForHash().put(
                stateKey(state.getUploadId()), "status", status.name());
        redisTemplate.opsForHash().put(
                stateKey(state.getUploadId()), "updatedAt",
                state.getUpdatedAt().toString());
        touch(state);
    }

    public void markCompleted(MultipartUploadState state, Long audioFileId) {
        state.setStatus(MultipartUploadStatus.COMPLETED);
        state.setAudioFileId(audioFileId);
        state.setUpdatedAt(System.currentTimeMillis());
        String key = stateKey(state.getUploadId());
        redisTemplate.opsForHash().put(key, "status",
                MultipartUploadStatus.COMPLETED.name());
        redisTemplate.opsForHash().put(key, "audioFileId",
                audioFileId.toString());
        redisTemplate.opsForHash().put(key, "updatedAt",
                state.getUpdatedAt().toString());
        Duration retention = Duration.ofHours(
                properties.getCompletedRetentionHours());
        redisTemplate.expire(key, retention);
        redisTemplate.expire(chunksKey(state.getUploadId()), retention);
        redisTemplate.opsForZSet().remove(CLEANUP_KEY, cleanupMember(state));
    }

    public boolean acquireMergeLock(String uploadId, String token) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                lockKey(uploadId), token, Duration.ofMinutes(15)));
    }

    public void releaseMergeLock(String uploadId, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                List.of(lockKey(uploadId)), token);
    }

    public List<ExpiredUpload> expiredUploads(long nowEpochMillis) {
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(CLEANUP_KEY, 0, nowEpochMillis);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<ExpiredUpload> result = new ArrayList<>();
        for (String member : members) {
            String[] parts = member.split("\\|", 3);
            if (parts.length == 3) {
                result.add(new ExpiredUpload(
                        Long.parseLong(parts[0]), parts[1],
                        Integer.parseInt(parts[2]), member));
            }
        }
        return result;
    }

    public boolean exists(String uploadId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(stateKey(uploadId)));
    }

    public void removeCleanupMember(String member) {
        redisTemplate.opsForZSet().remove(CLEANUP_KEY, member);
    }

    public void scheduleCleanup(MultipartUploadState state, Duration delay) {
        redisTemplate.opsForZSet().add(CLEANUP_KEY, cleanupMember(state),
                System.currentTimeMillis() + delay.toMillis());
    }

    private void touch(MultipartUploadState state) {
        Duration ttl = Duration.ofHours(properties.getStateTtlHours());
        redisTemplate.expire(stateKey(state.getUploadId()), ttl);
        redisTemplate.expire(chunksKey(state.getUploadId()), ttl);
        if (state.getResumeFingerprint() != null
                && !state.getResumeFingerprint().isBlank()) {
            redisTemplate.expire(resumeKey(state.getResumeFingerprint()), ttl);
        }
        redisTemplate.opsForZSet().add(CLEANUP_KEY, cleanupMember(state),
                System.currentTimeMillis() + ttl.toMillis());
    }

    private String cleanupMember(MultipartUploadState state) {
        return state.getUserId() + "|" + state.getUploadId() + "|"
                + state.getTotalChunks();
    }

    private static String value(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Incomplete upload state: " + key);
        }
        return value.toString();
    }

    private static Long optionalLong(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : Long.valueOf(value.toString());
    }

    private static String optionalString(Map<Object, Object> values,
                                         String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static String stateKey(String uploadId) {
        return STATE_PREFIX + uploadId;
    }

    private static String chunksKey(String uploadId) {
        return CHUNKS_PREFIX + uploadId;
    }

    private static String lockKey(String uploadId) {
        return LOCK_PREFIX + uploadId;
    }

    private static String resumeKey(String fingerprint) {
        return RESUME_PREFIX + fingerprint;
    }

    public record ExpiredUpload(Long userId, String uploadId,
                                int totalChunks, String member) {
    }
}
