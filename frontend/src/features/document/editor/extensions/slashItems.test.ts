import { describe, expect, it } from 'vitest'
import { filterSlashItems, slashItems } from './slashItems'

describe('filterSlashItems', () => {
  it('returns all items for an empty query', () => {
    expect(filterSlashItems('')).toHaveLength(slashItems.length)
  })

  it('matches Chinese titles, English keywords and pinyin initials', () => {
    expect(filterSlashItems('表格').map((i) => i.title)).toEqual(['表格'])
    expect(filterSlashItems('h2').map((i) => i.title)).toEqual(['二级标题'])
    expect(filterSlashItems('dm').map((i) => i.title)).toEqual(['代码块'])
  })
})
