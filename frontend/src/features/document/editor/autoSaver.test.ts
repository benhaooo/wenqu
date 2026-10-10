import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AutoSaver, type SaveStatus } from './autoSaver'

class Conflict extends Error {}

function setup(save: (content: string, base: number) => Promise<number>) {
  const statuses: SaveStatus[] = []
  let content = 'v0'
  const saver = new AutoSaver<string>({
    save,
    getContent: () => content,
    initialVersion: 0,
    onStatus: (status) => statuses.push(status),
    isConflict: (error) => error instanceof Conflict,
  })
  return {
    saver,
    statuses,
    edit: (next: string) => {
      content = next
      saver.markDirty()
    },
  }
}

describe('AutoSaver', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('saves 1.5s after the last edit with the latest content', async () => {
    const save = vi.fn((_content: string, base: number) => Promise.resolve(base + 1))
    const { edit, statuses } = setup(save)

    edit('a')
    await vi.advanceTimersByTimeAsync(1000)
    edit('ab')
    await vi.advanceTimersByTimeAsync(1400)
    expect(save).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(100)
    expect(save).toHaveBeenCalledExactlyOnceWith('ab', 0)
    expect(statuses.at(-1)).toBe('saved')
  })

  it('retries with backoff after a failure and succeeds when the network is back', async () => {
    const save = vi
      .fn<(content: string, base: number) => Promise<number>>()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(1)
    const { edit, statuses, saver } = setup(save)

    edit('a')
    await vi.advanceTimersByTimeAsync(1500)
    expect(statuses.at(-1)).toBe('error')
    await vi.advanceTimersByTimeAsync(2000)
    expect(save).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(4000)

    expect(save).toHaveBeenCalledTimes(3)
    expect(statuses.at(-1)).toBe('saved')
    expect(saver.baseVersion).toBe(1)
    expect(saver.hasUnsavedChanges).toBe(false)
  })

  it('stops retrying on version conflict', async () => {
    const save = vi.fn(() => Promise.reject(new Conflict()))
    const { edit, statuses } = setup(save)

    edit('a')
    await vi.advanceTimersByTimeAsync(60_000)

    expect(save).toHaveBeenCalledTimes(1)
    expect(statuses.at(-1)).toBe('conflict')
  })

  it('keeps retrying a snapshot after detach until it is saved', async () => {
    const save = vi
      .fn<(content: string, base: number) => Promise<number>>()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(1)
    const { edit, saver, statuses } = setup(save)

    edit('unsaved')
    saver.detach('snapshot')
    const statusCount = statuses.length
    await vi.advanceTimersByTimeAsync(60_000)

    expect(save).toHaveBeenCalledTimes(3)
    expect(save).toHaveBeenLastCalledWith('snapshot', 0)
    expect(statuses).toHaveLength(statusCount)
  })

  it('stops immediately on detach when everything is saved', async () => {
    const save = vi.fn((_content: string, base: number) => Promise.resolve(base + 1))
    const { saver } = setup(save)

    saver.detach('snapshot')
    await vi.advanceTimersByTimeAsync(60_000)

    expect(save).not.toHaveBeenCalled()
  })

  it('saves edits made during an in-flight save afterwards, based on the new version', async () => {
    let resolveFirst: (version: number) => void = () => undefined
    const save = vi
      .fn<(content: string, base: number) => Promise<number>>()
      .mockImplementationOnce(() => new Promise((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce(2)
    const { edit } = setup(save)

    edit('a')
    await vi.advanceTimersByTimeAsync(1500)
    edit('ab')
    resolveFirst(1)
    await vi.advanceTimersByTimeAsync(1500)

    expect(save).toHaveBeenNthCalledWith(2, 'ab', 1)
  })
})
