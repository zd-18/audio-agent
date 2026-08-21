package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.vo.TaskListVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioAnalysisTaskMapper extends BaseMapper<AudioAnalysisTask> {

    @Select("""
            SELECT t.*
            FROM audio_analysis_task t
            WHERE t.audio_file_id = #{audioFileId}
              AND t.status = 'SUCCESS'
            ORDER BY t.finished_at DESC, t.created_at DESC, t.id DESC
            LIMIT 1
            """)
    AudioAnalysisTask selectLatestSuccessfulByAudioFileId(
            @Param("audioFileId") Long audioFileId);

    @Select("""
            SELECT t.*
            FROM audio_analysis_task t
            WHERE t.audio_file_id = #{audioFileId}
              AND t.analysis_type = 'PROCESSING_CONTEXT'
              AND t.status = 'SUCCESS'
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT 1
            """)
    AudioAnalysisTask selectProcessingContextByAudioFileId(
            @Param("audioFileId") Long audioFileId);

    @Select("""
            SELECT *
            FROM audio_analysis_task
            WHERE source_event_id = #{sourceEventId}
            LIMIT 1
            """)
    AudioAnalysisTask selectBySourceEventId(
            @Param("sourceEventId") Long sourceEventId);

    @Select("""
            <script>
            SELECT t.id AS task_id,
                   t.audio_file_id,
                   f.original_name AS file_name,
                   t.analysis_type,
                   t.status,
                   t.progress,
                   t.retry_count,
                   t.max_retry_count,
                   t.last_error_code,
                   t.error_message,
                   t.created_at,
                   t.started_at,
                   t.finished_at
            FROM audio_analysis_task t
            LEFT JOIN audio_file f
              ON f.id = t.audio_file_id AND f.deleted = 0
            WHERE t.analysis_type != 'PROCESSING_CONTEXT'
              AND f.user_id = #{userId}
            <if test="status != null">
              AND t.status = #{status}
            </if>
            <if test="audioFileId != null">
              AND t.audio_file_id = #{audioFileId}
            </if>
            <if test="analysisType != null">
              AND t.analysis_type = #{analysisType}
            </if>
            <if test="keyword != null and keyword != ''">
              AND f.original_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY t.created_at DESC
            </script>
            """)
    IPage<TaskListVO> selectTaskPage(Page<TaskListVO> page,
                                     @Param("userId") Long userId,
                                     @Param("status") String status,
                                     @Param("audioFileId") Long audioFileId,
                                     @Param("analysisType") String analysisType,
                                     @Param("keyword") String keyword);
}
