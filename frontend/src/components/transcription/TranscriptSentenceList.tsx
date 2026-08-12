import { FileTextOutlined } from '@ant-design/icons'
import { memo } from 'react'
import type { TranscriptSegment } from '../../types/transcription'
import { formatDuration } from '../../utils/formatters'
import { getTranscriptSegmentOrder } from '../../utils/transcriptParagraphs'

interface TranscriptSentenceListProps {
  segments: TranscriptSegment[]
  highlightedSegmentOrder: number | null
  loading: boolean
  onPlaySegment: (segment: TranscriptSegment) => void
}

function confidenceLabel(value?: number | null) {
  return typeof value === 'number' ? `${Math.round(value * 100)}%` : null
}

function TranscriptSentenceList({
  segments,
  highlightedSegmentOrder,
  loading,
  onPlaySegment,
}: TranscriptSentenceListProps) {
  return (
    <div className="transcript-segment-list" aria-live="polite">
      {segments.map((segment) => {
        const segmentOrder = getTranscriptSegmentOrder(segment)
        const active = segmentOrder === highlightedSegmentOrder
        const confidence = confidenceLabel(segment.confidence)
        return (
          <button
            key={segment.segmentId}
            type="button"
            data-segment-id={segment.segmentId}
            data-segment-order={segmentOrder}
            className={`transcript-segment${active ? ' is-active' : ''}`}
            aria-current={active ? 'true' : undefined}
            onClick={() => onPlaySegment(segment)}
          >
            <span className="transcript-segment__identity">
              <b>#{segmentOrder}</b>
              <span className="transcript-segment__time">
                {formatDuration(segment.startMs)} - {formatDuration(segment.endMs)}
              </span>
            </span>
            <span className="transcript-segment__text">{segment.text}</span>
            <span className="transcript-segment__meta">
              {segment.speaker && <span>{segment.speaker}</span>}
              {confidence && <span>置信度 {confidence}</span>}
            </span>
          </button>
        )
      })}
      {!loading && segments.length === 0 && (
        <div className="transcript-segment-empty">
          <FileTextOutlined />
          <strong>暂无文字片段</strong>
          <span>当前结果没有可展示的时间片段，可以尝试刷新结果。</span>
        </div>
      )}
    </div>
  )
}

export default memo(TranscriptSentenceList)
