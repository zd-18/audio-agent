import {
  CaretDownOutlined,
  CaretRightOutlined,
  FileTextOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { memo } from 'react'
import type { TranscriptSegment } from '../../types/transcription'
import { formatDuration } from '../../utils/formatters'
import type { TranscriptParagraph } from '../../utils/transcriptParagraphs'
import { getTranscriptSegmentOrder } from '../../utils/transcriptParagraphs'

interface TranscriptParagraphListProps {
  paragraphs: TranscriptParagraph[]
  expandedGroupKeys: ReadonlySet<string>
  highlightedSegmentOrder: number | null
  loading: boolean
  onPlayParagraph: (paragraph: TranscriptParagraph) => void
  onPlaySegment: (segment: TranscriptSegment) => void
  onToggleParagraph: (groupKey: string) => void
}

function TranscriptParagraphList({
  paragraphs,
  expandedGroupKeys,
  highlightedSegmentOrder,
  loading,
  onPlayParagraph,
  onPlaySegment,
  onToggleParagraph,
}: TranscriptParagraphListProps) {
  return (
    <div className="transcript-paragraph-list" aria-live="polite">
      {paragraphs.map((paragraph, index) => {
        const expanded = expandedGroupKeys.has(paragraph.groupKey)
        const active = paragraph.segments.some(
          (segment) => getTranscriptSegmentOrder(segment) === highlightedSegmentOrder,
        )
        const detailsId = `transcript-paragraph-${paragraph.groupKey.replace(/[^a-zA-Z0-9_-]/g, '-')}`

        return (
          <article
            key={paragraph.groupKey}
            className={`transcript-paragraph${active ? ' is-active' : ''}`}
            data-paragraph-key={paragraph.groupKey}
            aria-current={active ? 'true' : undefined}
          >
            <div className="transcript-paragraph__summary">
              <button
                type="button"
                className="transcript-paragraph__play"
                aria-label={`播放段落 ${index + 1}，${formatDuration(paragraph.startMs)} 至 ${formatDuration(paragraph.endMs)}`}
                onClick={() => onPlayParagraph(paragraph)}
              >
                <PlayCircleOutlined aria-hidden="true" />
                <span>
                  <b>段落 {index + 1}</b>
                  <span>{formatDuration(paragraph.startMs)} - {formatDuration(paragraph.endMs)}</span>
                </span>
              </button>

              <div className="transcript-paragraph__body">
                <p>{paragraph.text}</p>
                <div className="transcript-paragraph__meta">
                  <span>包含 {paragraph.segmentCount} 个片段</span>
                  {paragraph.speakerLabel && <span>{paragraph.speakerLabel}</span>}
                  <button
                    type="button"
                    aria-expanded={expanded}
                    aria-controls={detailsId}
                    onClick={() => onToggleParagraph(paragraph.groupKey)}
                  >
                    {expanded ? <CaretDownOutlined /> : <CaretRightOutlined />}
                    {expanded ? '收起明细' : '展开明细'}
                  </button>
                </div>
              </div>
            </div>

            {expanded && (
              <div id={detailsId} className="transcript-paragraph__details">
                {paragraph.segments.map((segment) => {
                  const segmentOrder = getTranscriptSegmentOrder(segment)
                  const segmentActive = segmentOrder === highlightedSegmentOrder
                  return (
                    <button
                      key={segment.segmentId}
                      type="button"
                      data-segment-id={segment.segmentId}
                      data-segment-order={segmentOrder}
                      className={`transcript-paragraph-segment${segmentActive ? ' is-active' : ''}`}
                      aria-current={segmentActive ? 'true' : undefined}
                      onClick={() => onPlaySegment(segment)}
                    >
                      <span className="transcript-paragraph-segment__identity">
                        <b>#{segmentOrder}</b>
                        <span>{formatDuration(segment.startMs)} - {formatDuration(segment.endMs)}</span>
                      </span>
                      <span>{segment.text}</span>
                    </button>
                  )
                })}
              </div>
            )}
          </article>
        )
      })}

      {!loading && paragraphs.length === 0 && (
        <div className="transcript-segment-empty">
          <FileTextOutlined />
          <strong>暂无文字片段</strong>
          <span>当前结果没有可展示的时间片段，可以尝试刷新结果。</span>
        </div>
      )}
    </div>
  )
}

export default memo(TranscriptParagraphList)
