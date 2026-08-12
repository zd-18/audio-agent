import { describe, expect, it } from 'vitest'
import { formatDuration } from './formatters'

describe('formatDuration', () => {
  it.each([
    [0, '00:00'],
    [5616, '00:05'],
    [76558, '01:16'],
    [3661000, '01:01:01'],
  ])('formats %i milliseconds as %s', (value, expected) => {
    expect(formatDuration(value)).toBe(expected)
  })
})
