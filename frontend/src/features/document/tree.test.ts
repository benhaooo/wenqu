import { describe, expect, it } from 'vitest'
import type { NodeResponse } from '@/shared/api/generated/model'
import { buildIndex, pathTo, ROOT_ID } from './tree'

function node(id: number, parentId: number | null, position: string): NodeResponse {
  return { id, parentId, position, type: 'FOLDER', title: `n${String(id)}`, updatedAt: '' }
}

describe('buildIndex', () => {
  it('groups children under parents ordered by position', () => {
    const index = buildIndex([node(1, null, 'a1'), node(2, null, 'a0'), node(3, 1, 'a0V')])

    expect(index.children.get(ROOT_ID)).toEqual(['2', '1'])
    expect(index.children.get('1')).toEqual(['3'])
  })

  it('compares positions by code point, matching the backend collation', () => {
    const index = buildIndex([node(1, null, 'a'), node(2, null, 'Z')])

    expect(index.children.get(ROOT_ID)).toEqual(['2', '1'])
  })

  it('builds the path from root to a node', () => {
    const index = buildIndex([node(1, null, 'a0'), node(2, 1, 'a0'), node(3, 2, 'a0')])

    expect(pathTo(index, 3).map((n) => n.id)).toEqual([1, 2, 3])
  })
})
