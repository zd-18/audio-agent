import { describe, expect, it } from 'vitest'
import { getTranscriptionProgressText } from './transcription'

describe('getTranscriptionProgressText', () => {
  it.each([
    [0, '等待处理'],
    [9, '等待处理'],
    [10, '正在准备音频'],
    [29, '正在准备音频'],
    [30, '正在转换音频格式'],
    [49, '正在转换音频格式'],
    [50, '正在识别语音'],
    [84, '正在识别语音'],
    [85, '正在保存文字稿'],
    [99, '正在保存文字稿'],
    [100, '转写完成'],
  ])('maps %i percent to %s', (progress, expected) => {
    expect(getTranscriptionProgressText(progress)).toBe(expected)
  })
})
