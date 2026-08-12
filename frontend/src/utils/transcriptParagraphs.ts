import type { TranscriptSegment } from '../types/transcription'

const MAX_SEGMENTS_PER_PARAGRAPH = 4
const MAX_PARAGRAPH_SPAN_MS = 30_000
const MAX_ADJACENT_GAP_MS = 5_000
const CHINESE_TERMINAL_PUNCTUATION = /[。！？；，：…]$/u
const LEADING_PUNCTUATION = /^[。！？；，：…,.!?;:)]/u

export interface TranscriptParagraph {
  groupKey: string
  startMs: number
  endMs: number
  text: string
  segments: TranscriptSegment[]
  segmentIds: string[]
  firstSegmentOrder: number
  lastSegmentOrder: number
  segmentCount: number
  speakerLabel: string | null
}

export function getTranscriptSegmentOrder(segment: TranscriptSegment) {
  return segment.segmentOrder
}

function normalizeSegmentText(text: string) {
  return text.trim().replace(/\s+/gu, ' ')
}

export function combineTranscriptSegmentText(segments: TranscriptSegment[]) {
  return segments.reduce((combined, segment) => {
    const nextText = normalizeSegmentText(segment.text)
    if (!nextText) return combined
    if (!combined) return nextText

    const separator = CHINESE_TERMINAL_PUNCTUATION.test(combined)
      || LEADING_PUNCTUATION.test(nextText)
      ? ''
      : ' '
    return `${combined}${separator}${nextText}`
  }, '')
}

function hasExplicitSpeakerChange(
  paragraphSegments: TranscriptSegment[],
  next: TranscriptSegment,
) {
  const nextSpeaker = next.speaker?.trim()
  if (!nextSpeaker) return false

  for (let index = paragraphSegments.length - 1; index >= 0; index -= 1) {
    const previousSpeaker = paragraphSegments[index].speaker?.trim()
    if (previousSpeaker) return previousSpeaker !== nextSpeaker
  }
  return false
}

function shouldStartNewParagraph(
  paragraphSegments: TranscriptSegment[],
  next: TranscriptSegment,
) {
  if (paragraphSegments.length >= MAX_SEGMENTS_PER_PARAGRAPH) return true

  const first = paragraphSegments[0]
  const previous = paragraphSegments[paragraphSegments.length - 1]
  if (getTranscriptSegmentOrder(next) !== getTranscriptSegmentOrder(previous) + 1) {
    return true
  }
  if (next.startMs - previous.endMs > MAX_ADJACENT_GAP_MS) return true
  if (hasExplicitSpeakerChange(paragraphSegments, next)) return true
  return next.endMs - first.startMs > MAX_PARAGRAPH_SPAN_MS
}

function toTranscriptParagraph(segments: TranscriptSegment[]): TranscriptParagraph {
  const first = segments[0]
  const last = segments[segments.length - 1]
  const speakerLabel = segments
    .map((segment) => segment.speaker?.trim())
    .find((speaker): speaker is string => Boolean(speaker)) ?? null

  return {
    groupKey: `${first.segmentId}:${last.segmentId}`,
    startMs: first.startMs,
    endMs: last.endMs,
    text: combineTranscriptSegmentText(segments),
    segments: [...segments],
    segmentIds: segments.map((segment) => segment.segmentId),
    firstSegmentOrder: getTranscriptSegmentOrder(first),
    lastSegmentOrder: getTranscriptSegmentOrder(last),
    segmentCount: segments.length,
    speakerLabel,
  }
}

export function createTranscriptParagraphs(
  sourceSegments: TranscriptSegment[],
): TranscriptParagraph[] {
  const visibleSegments = sourceSegments.filter(
    (segment) => normalizeSegmentText(segment.text).length > 0,
  )
  const paragraphs: TranscriptParagraph[] = []
  let currentSegments: TranscriptSegment[] = []

  for (const segment of visibleSegments) {
    if (
      currentSegments.length > 0
      && shouldStartNewParagraph(currentSegments, segment)
    ) {
      paragraphs.push(toTranscriptParagraph(currentSegments))
      currentSegments = []
    }
    currentSegments.push(segment)
  }

  if (currentSegments.length > 0) {
    paragraphs.push(toTranscriptParagraph(currentSegments))
  }
  return paragraphs
}

export function findParagraphBySegmentOrder(
  paragraphs: TranscriptParagraph[],
  segmentOrder: number,
) {
  return paragraphs.find((paragraph) => paragraph.segments.some(
    (segment) => getTranscriptSegmentOrder(segment) === segmentOrder,
  ))
}

export function findParagraphBySegmentId(
  paragraphs: TranscriptParagraph[],
  segmentId: string,
) {
  return paragraphs.find((paragraph) => paragraph.segmentIds.includes(segmentId))
}
