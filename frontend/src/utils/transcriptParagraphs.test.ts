import { describe, expect, it } from 'vitest'
import type { TranscriptSegment } from '../types/transcription'
import {
  combineTranscriptSegmentText,
  createTranscriptParagraphs,
  findParagraphBySegmentId,
  findParagraphBySegmentOrder,
} from './transcriptParagraphs'

function segment(
  order: number,
  startMs: number,
  endMs: number,
  text = `片段 ${order}`,
  overrides: Partial<TranscriptSegment> = {},
): TranscriptSegment {
  return {
    segmentId: `segment-${order}`,
    order,
    segmentOrder: order,
    startMs,
    endMs,
    text,
    ...overrides,
  }
}

describe('createTranscriptParagraphs', () => {
  it('returns an empty array for no segments', () => {
    expect(createTranscriptParagraphs([])).toEqual([])
  })

  it('creates one paragraph for one segment', () => {
    const result = createTranscriptParagraphs([segment(1, 1_000, 3_000, '单句')])

    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      groupKey: 'segment-1:segment-1',
      startMs: 1_000,
      endMs: 3_000,
      text: '单句',
      segmentCount: 1,
      firstSegmentOrder: 1,
      lastSegmentOrder: 1,
    })
  })

  it('limits a paragraph to four segments without changing their order', () => {
    const source = Array.from({ length: 8 }, (_, index) => (
      segment(index + 1, index * 4_000, (index + 1) * 4_000)
    ))

    const result = createTranscriptParagraphs(source)

    expect(result).toHaveLength(2)
    expect(result.map((paragraph) => paragraph.segmentCount)).toEqual([4, 4])
    expect(result.flatMap((paragraph) => paragraph.segmentIds)).toEqual(
      source.map((item) => item.segmentId),
    )
  })

  it('starts a new paragraph before its time span exceeds 30 seconds', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 10_000),
      segment(2, 10_000, 20_000),
      segment(3, 20_000, 30_001),
    ])

    expect(result.map((paragraph) => paragraph.segmentIds)).toEqual([
      ['segment-1', 'segment-2'],
      ['segment-3'],
    ])
  })

  it('starts a new paragraph for an adjacent gap over five seconds', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 2_000),
      segment(2, 7_001, 9_000),
    ])

    expect(result).toHaveLength(2)
  })

  it('starts a new paragraph when segmentOrder is discontinuous', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 2_000),
      segment(3, 2_000, 4_000),
    ])

    expect(result.map((paragraph) => paragraph.firstSegmentOrder)).toEqual([1, 3])
  })

  it('starts a new paragraph for an explicit speaker change', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 2_000, '甲', { speaker: 'speaker-1' }),
      segment(2, 2_000, 4_000, '乙', { speaker: 'speaker-2' }),
    ])

    expect(result).toHaveLength(2)
    expect(result.map((paragraph) => paragraph.speakerLabel)).toEqual([
      'speaker-1',
      'speaker-2',
    ])
  })

  it('does not split only because one speaker value is empty', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 2_000, '甲', { speaker: 'speaker-1' }),
      segment(2, 2_000, 4_000, '乙', { speaker: null }),
    ])

    expect(result).toHaveLength(1)
    expect(result[0].speakerLabel).toBe('speaker-1')
  })

  it('detects a later explicit speaker change across an empty speaker value', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 2_000, '甲', { speaker: 'speaker-1' }),
      segment(2, 2_000, 4_000, '过渡', { speaker: null }),
      segment(3, 4_000, 6_000, '乙', { speaker: 'speaker-2' }),
    ])

    expect(result.map((paragraph) => paragraph.segmentIds)).toEqual([
      ['segment-1', 'segment-2'],
      ['segment-3'],
    ])
  })

  it('keeps a segment longer than 30 seconds in its own paragraph', () => {
    const result = createTranscriptParagraphs([
      segment(1, 0, 31_000, '长片段'),
      segment(2, 31_000, 33_000, '后续片段'),
    ])

    expect(result.map((paragraph) => paragraph.segmentIds)).toEqual([
      ['segment-1'],
      ['segment-2'],
    ])
  })

  it('omits empty text without reordering the remaining segments', () => {
    const first = segment(1, 0, 2_000, '第一句')
    const empty = segment(2, 2_000, 3_000, ' \n  ')
    const third = segment(3, 3_000, 5_000, '第三句')

    const result = createTranscriptParagraphs([first, empty, third])

    expect(result.flatMap((paragraph) => paragraph.segments)).toEqual([first, third])
    expect(result.flatMap((paragraph) => paragraph.segmentIds)).not.toContain(empty.segmentId)
  })

  it('keeps paragraph boundaries and all original segment ids accurate', () => {
    const source = [
      segment(4, 8_000, 12_000, '第一句'),
      segment(5, 12_000, 16_500, '第二句'),
    ]

    const [paragraph] = createTranscriptParagraphs(source)

    expect(paragraph.startMs).toBe(8_000)
    expect(paragraph.endMs).toBe(16_500)
    expect(paragraph.segmentIds).toEqual(['segment-4', 'segment-5'])
    expect(paragraph.segments).toEqual(source)
    expect(paragraph.groupKey).toBe('segment-4:segment-5')
  })
})

describe('combineTranscriptSegmentText', () => {
  it('joins Chinese sentences directly after Chinese punctuation', () => {
    expect(combineTranscriptSegmentText([
      segment(1, 0, 1_000, '第一句。'),
      segment(2, 1_000, 2_000, '第二句！'),
      segment(3, 2_000, 3_000, '第三句'),
    ])).toBe('第一句。第二句！第三句')
  })

  it('normalizes whitespace and separates English and mixed-language text', () => {
    expect(combineTranscriptSegmentText([
      segment(1, 0, 1_000, '  Hello,   world.  '),
      segment(2, 1_000, 2_000, '中文 mixed'),
      segment(3, 2_000, 3_000, 'text'),
    ])).toBe('Hello, world. 中文 mixed text')
  })

  it('does not insert a space before leading punctuation', () => {
    expect(combineTranscriptSegmentText([
      segment(1, 0, 1_000, '你好'),
      segment(2, 1_000, 2_000, '，世界'),
    ])).toBe('你好，世界')
  })
})

describe('paragraph lookup helpers', () => {
  const paragraphs = createTranscriptParagraphs([
    segment(1, 0, 1_000),
    segment(2, 1_000, 2_000),
    segment(3, 8_000, 9_000),
  ])

  it('finds a paragraph by segmentOrder', () => {
    expect(findParagraphBySegmentOrder(paragraphs, 2)?.segmentIds)
      .toContain('segment-2')
    expect(findParagraphBySegmentOrder(paragraphs, 99)).toBeUndefined()
  })

  it('finds a paragraph by segmentId', () => {
    expect(findParagraphBySegmentId(paragraphs, 'segment-3')?.firstSegmentOrder)
      .toBe(3)
    expect(findParagraphBySegmentId(paragraphs, 'missing')).toBeUndefined()
  })
})
