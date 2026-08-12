package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioAnalysisReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioAnalysisReportMapper
        extends BaseMapper<AudioAnalysisReport> {

    @Select("""
            SELECT id, task_id, audio_file_id, report_version,
                   quality_score, quality_grade, summary, report_json,
                   created_at, updated_at
            FROM audio_analysis_report
            WHERE task_id = #{taskId}
            """)
    AudioAnalysisReport selectByTaskId(@Param("taskId") Long taskId);

    /**
     * task_id 唯一约束提供最终并发保护。更新时保留原报告 ID 和创建时间，
     * 只替换当前快照内容。
     */
    @Insert("""
            INSERT INTO audio_analysis_report (
                id, task_id, audio_file_id, report_version,
                quality_score, quality_grade, summary, report_json,
                created_at, updated_at
            ) VALUES (
                #{id}, #{taskId}, #{audioFileId}, #{reportVersion},
                #{qualityScore}, #{qualityGrade}, #{summary}, #{reportJson},
                #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                audio_file_id = VALUES(audio_file_id),
                report_version = VALUES(report_version),
                quality_score = VALUES(quality_score),
                quality_grade = VALUES(quality_grade),
                summary = VALUES(summary),
                report_json = VALUES(report_json),
                updated_at = VALUES(updated_at)
            """)
    int upsert(AudioAnalysisReport report);
}
