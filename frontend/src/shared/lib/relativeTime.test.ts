import { describe, expect, it } from 'vitest'
import { relativeTime } from './relativeTime'

const now = new Date('2026-10-10T12:00:00Z')

describe('relativeTime', () => {
  it.each([
    ['2026-10-10T11:59:30Z', '刚刚'],
    ['2026-10-10T11:50:00Z', '10分钟前'],
    ['2026-10-10T09:00:00Z', '3小时前'],
    ['2026-10-09T12:00:00Z', '昨天'],
  ])('formats %s as %s', (iso, expected) => {
    expect(relativeTime(iso, now).replace(/\s/g, '')).toBe(expected)
  })
})
