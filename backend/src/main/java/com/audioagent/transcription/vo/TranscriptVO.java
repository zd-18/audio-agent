package com.audioagent.transcription.vo;

import com.audioagent.transcription.entity.AudioTranscript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptVO {
    private String transcriptId;
    private String audioFileId;
    private String audioFileName;
    private String language;
    private String fullText;
    private Long durationMs;
    private Integer speakerCount;
    private Integer segmentCount;
    private List<TranscriptSegmentVO> segments;

    public static TranscriptVO from(AudioTranscript transcript,
                                    String audioFileName,
                                    List<TranscriptSegmentVO> segments) {
        return TranscriptVO.builder()
                .transcriptId(transcript.getId().toString())
                .audioFileId(transcript.getAudioFileId().toString())
                .audioFileName(audioFileName)
                .language(transcript.getLanguage())
                .fullText(transcript.getFullText())
                .durationMs(transcript.getDurationMs())
                .speakerCount(transcript.getSpeakerCount())
                .segmentCount(transcript.getSegmentCount())
                .segments(segments)
                .build();
    }
}
