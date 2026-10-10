import { localStore } from '@/shared/lib/localStore'

const KEY = 'wenqu.lastWorkspaceId'

export const lastWorkspace = {
  get: (): number | null => {
    const value = Number(localStore.get(KEY))
    return Number.isInteger(value) && value > 0 ? value : null
  },
  set: (workspaceId: number) => {
    localStore.set(KEY, String(workspaceId))
  },
}
