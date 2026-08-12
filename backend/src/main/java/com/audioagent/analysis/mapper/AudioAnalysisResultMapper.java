package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AudioAnalysisResultMapper extends BaseMapper<AudioAnalysisResult> {

    @Insert("""
            INSERT INTO audio_analysis_result (
                id, task_id, audio_file_id, format_name, codec_name,
                duration_ms, sample_rate, channels, bit_rate, file_size,
                issue_count, silence_count, total_silence_duration_ms,
                silence_ratio, integrated_loudness_lufs,
                loudness_range_lu, sample_peak_dbfs, true_peak_dbfs,
                created_at, updated_at
            ) VALUES (
                #{id}, #{taskId}, #{audioFileId}, #{formatName}, #{codecName},
                #{durationMs}, #{sampleRate}, #{channels}, #{bitRate},
                #{fileSize}, #{issueCount}, #{silenceCount},
                #{totalSilenceDurationMs}, #{silenceRatio},
                #{integratedLoudnessLufs}, #{loudnessRangeLu},
                #{samplePeakDbfs}, #{truePeakDbfs},
                #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                audio_file_id = VALUES(audio_file_id),
                format_name = VALUES(format_name),
                codec_name = VALUES(codec_name),
                duration_ms = VALUES(duration_ms),
                sample_rate = VALUES(sample_rate),
                channels = VALUES(channels),
                bit_rate = VALUES(bit_rate),
                file_size = VALUES(file_size),
                issue_count = VALUES(issue_count),
                silence_count = VALUES(silence_count),
                total_silence_duration_ms =
                    VALUES(total_silence_duration_ms),
                silence_ratio = VALUES(silence_ratio),
                integrated_loudness_lufs =
                    VALUES(integrated_loudness_lufs),
                loudness_range_lu = VALUES(loudness_range_lu),
                sample_peak_dbfs = VALUES(sample_peak_dbfs),
                true_peak_dbfs = VALUES(true_peak_dbfs),
                updated_at = VALUES(updated_at)
            """)
    int upsert(AudioAnalysisResult result);
}
