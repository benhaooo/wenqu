import type { WorkspaceRole } from '@/shared/api/generated/model'

export const roleLabels: Record<WorkspaceRole, string> = {
  ADMIN: '管理员',
  EDITOR: '编辑者',
  VIEWER: '只读成员',
}
