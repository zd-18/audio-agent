import { describe, expect, it } from 'vitest'
import { getUsernameInitial } from './userAvatar'

describe('getUsernameInitial', () => {
  it('uses the first Chinese character', () => {
    expect(getUsernameInitial('测试用户')).toBe('测')
  })

  it('uppercases the first English letter', () => {
    expect(getUsernameInitial('audio-user')).toBe('A')
  })

  it('ignores surrounding whitespace', () => {
    expect(getUsernameInitial('  beta  ')).toBe('B')
  })

  it('uses a stable fallback for a missing username', () => {
    expect(getUsernameInitial('')).toBe('用')
    expect(getUsernameInitial(null)).toBe('用')
  })
})
