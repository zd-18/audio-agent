package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioIssueSegment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AudioIssueSegmentMapper
        extends BaseMapper<AudioIssueSegment> {

    @Delete("""
            DELETE FROM audio_issue_segment
            WHERE task_id = #{taskId} AND issue_type = #{issueType}
            """)
    int deleteByTaskAndType(@Param("taskId") Long taskId,
                            @Param("issueType") String issueType);

    @Delete("""
            DELETE FROM audio_issue_segment
            WHERE task_id = #{taskId}
              AND issue_type IN ('VOLUME_DROP', 'VOLUME_SPIKE')
            """)
    int deleteVolumeTypesByTask(@Param("taskId") Long taskId);

    @Insert("""
            <script>
            INSERT INTO audio_issue_segment (
                id, task_id, audio_file_id, issue_type,
                start_ms, end_ms, duration_ms, severity,
                metric_json, description, created_at, updated_at
            ) VALUES
            <foreach collection="records" item="item" separator=",">
            (
                #{item.id}, #{item.taskId}, #{item.audioFileId},
                #{item.issueType}, #{item.startMs}, #{item.endMs},
                #{item.durationMs}, #{item.severity},
                #{item.metricJson}, #{item.description},
                #{item.createdAt}, #{item.updatedAt}
            )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("records") List<AudioIssueSegment> records);

    @Select("""
            SELECT id, task_id, audio_file_id, issue_type,
                   start_ms, end_ms, duration_ms, severity,
                   metric_json, description, created_at, updated_at
            FROM audio_issue_segment
            WHERE task_id = #{taskId} AND issue_type = #{issueType}
            ORDER BY start_ms ASC, id ASC
            """)
    List<AudioIssueSegment> selectByTaskAndType(
            @Param("taskId") Long taskId,
            @Param("issueType") String issueType);

    @Select("""
            SELECT id, task_id, audio_file_id, issue_type,
                   start_ms, end_ms, duration_ms, severity,
                   metric_json, description, created_at, updated_at
            FROM audio_issue_segment
            WHERE task_id = #{taskId}
            ORDER BY start_ms ASC, id ASC
            """)
    List<AudioIssueSegment> selectByTask(@Param("taskId") Long taskId);
}
