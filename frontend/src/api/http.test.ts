import { describe, expect, it } from 'vitest'
import { ApiError, parseApiResponse, unwrapApiResponse } from './http'

describe('API response handling', () => {
  it('preserves long resource IDs as strings even when JSON contains numbers', () => {
    const response = parseApiResponse<{
      taskId: string
      audioFileId: string
      transcriptId: string
    }>(
      '{"code":0,"message":"ok","data":{"taskId":9007199254740995,"audioFileId":9007199254740993,"transcriptId":9007199254740997}}',
    )

    expect(response.data).toEqual({
      taskId: '9007199254740995',
      audioFileId: '9007199254740993',
      transcriptId: '9007199254740997',
    })
  })

  it('rejects HTTP 200 envelopes whose business code is not zero', () => {
    expect(() => unwrapApiResponse({
      code: 40901,
      message: '音频文件当前不可用于转写',
    })).toThrowError(ApiError)
  })
})
