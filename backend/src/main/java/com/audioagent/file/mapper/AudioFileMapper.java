package com.audioagent.file.mapper;

import com.audioagent.file.entity.AudioFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface AudioFileMapper extends BaseMapper<AudioFile> {

    @Select("""
            SELECT * FROM audio_file
            WHERE id = #{fileId}
            FOR UPDATE
            """)
    AudioFile selectByIdForUpdate(@Param("fileId") Long fileId);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM audio_file
            WHERE root_audio_file_id = #{rootAudioFileId}
            """)
    Integer selectNextVersionNo(
            @Param("rootAudioFileId") Long rootAudioFileId);

    @Select("""
            SELECT * FROM audio_file
            WHERE source_execution_id = #{executionId}
            LIMIT 1
            """)
    AudioFile selectBySourceExecutionId(
            @Param("executionId") Long executionId);

    @Select("""
            SELECT * FROM audio_file
            WHERE user_id = #{userId}
              AND root_audio_file_id = #{rootAudioFileId}
              AND deleted = 0
            ORDER BY version_no ASC, created_at ASC, id ASC
            """)
    List<AudioFile> selectVersionChain(
            @Param("userId") Long userId,
            @Param("rootAudioFileId") Long rootAudioFileId);

    @Select("""
            SELECT * FROM audio_file
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND file_status = #{availableStatus}
              AND deleted = 0
            FOR UPDATE
            """)
    AudioFile selectOwnedAvailableForUpdate(
            @Param("userId") Long userId,
            @Param("fileId") Long fileId,
            @Param("availableStatus") int availableStatus);

    @Select("""
            SELECT * FROM audio_file
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    AudioFile selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("fileId") Long fileId);

    @Select("""
            <script>
            SELECT * FROM audio_file
            WHERE user_id = #{userId}
              AND deleted = 1
              AND purged_at IS NULL
            <if test="keyword != null and keyword != ''">
              AND original_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY deleted_at DESC, id DESC
            </script>
            """)
    IPage<AudioFile> selectRecycleBinPage(
            Page<AudioFile> page,
            @Param("userId") Long userId,
            @Param("keyword") String keyword);

    @Select("""
            SELECT * FROM audio_file
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND deleted = 1
              AND purged_at IS NULL
            FOR UPDATE
            """)
    AudioFile selectOwnedTrashForUpdate(
            @Param("userId") Long userId,
            @Param("fileId") Long fileId);

    @Update("""
            UPDATE audio_file
            SET pre_delete_status = file_status,
                file_status = 5,
                deleted = 1,
                deleted_at = #{now},
                purged_at = NULL,
                updated_at = #{now}
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND deleted = 0
              AND file_status NOT IN (1, 3)
            """)
    int moveToRecycleBin(@Param("userId") Long userId,
                         @Param("fileId") Long fileId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_file
            SET file_status = COALESCE(pre_delete_status, 2),
                pre_delete_status = NULL,
                deleted = 0,
                deleted_at = NULL,
                updated_at = #{now}
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND deleted = 1
              AND purged_at IS NULL
            """)
    int restoreFromRecycleBin(@Param("userId") Long userId,
                              @Param("fileId") Long fileId,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_file
            SET purged_at = #{now}, updated_at = #{now}
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND deleted = 1
              AND purged_at IS NULL
            """)
    int markPurged(@Param("userId") Long userId,
                   @Param("fileId") Long fileId,
                   @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM audio_file
            WHERE user_id = #{userId}
              AND sha256 = #{sha256}
              AND file_status = #{availableStatus}
              AND deleted = 0
            ORDER BY created_at ASC
            LIMIT 1
            """)
    AudioFile selectFirstAvailableByHash(
            @Param("userId") Long userId,
            @Param("sha256") String sha256,
            @Param("availableStatus") int availableStatus);

    @Select("""
            SELECT * FROM audio_file
            WHERE bucket_name = #{bucketName}
              AND object_key = #{objectKey}
              AND deleted = 0
            LIMIT 1
            """)
    AudioFile selectByStorageObject(
            @Param("bucketName") String bucketName,
            @Param("objectKey") String objectKey);
}
