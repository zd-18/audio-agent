package com.audioagent.file.mapper;

import com.audioagent.file.entity.AudioFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
