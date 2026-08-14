import { describe, expect, it } from 'vitest'
import { sha256File } from './sha256File'

describe('sha256File', () => {
  it('hashes known content incrementally', async () => {
    const source = new Uint8Array([97, 98, 99])
    const file = {
      size: source.byteLength,
      slice: (start?: number, end?: number) => ({
        arrayBuffer: async () => source.slice(start, end).buffer,
      }),
    } as unknown as Blob
    const result = await sha256File(file, { readSize: 1 })
    expect(result).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
  })

  it('hashes empty content', async () => {
    const result = await sha256File(new Blob([]))
    expect(result).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
  })

  it('hashes across multiple SHA-256 read blocks', async () => {
    const source = Uint8Array.from({ length: 257 }, (_, index) => index % 251)
    const file = {
      size: source.byteLength,
      slice: (start?: number, end?: number) => ({
        arrayBuffer: async () => source.slice(start, end).buffer,
      }),
    } as unknown as Blob
    expect(await sha256File(file, { readSize: 13 }))
      .toBe('a2c6cad2ffa699b14538231fef914f45d30440389e6f8d79091efba836165a2b')
  })
})
