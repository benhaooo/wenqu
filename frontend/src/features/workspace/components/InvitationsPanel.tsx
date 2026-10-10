import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import {
  getListInvitationsQueryKey,
  useListInvitations,
  useRevokeInvitation,
} from '@/shared/api/generated/workspace/workspace'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { roleLabels } from '../roles'
import { CreateInvitationDialog } from './CreateInvitationDialog'

const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

export function InvitationsPanel({ workspaceId }: { workspaceId: number }) {
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const { data: invitations = [] } = useListInvitations(workspaceId, {
    query: { select: (result) => result.data },
  })
  const revoke = useRevokeInvitation({
    mutation: {
      onSuccess: () => {
        toast.success('邀请链接已撤销')
        void queryClient.invalidateQueries({ queryKey: getListInvitationsQueryKey(workspaceId) })
      },
      onError: (error) => toast.error(error.message),
    },
  })

  return (
    <section>
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">邀请链接</h2>
          <p className="text-sm text-muted-foreground">
            拿到链接的人登录后即可加入空间，链接在过期或撤销前可多次使用
          </p>
        </div>
        <Button
          onClick={() => {
            setCreating(true)
          }}
        >
          创建邀请链接
        </Button>
      </div>
      {invitations.length === 0 ? (
        <p className="mt-4 rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
          没有有效的邀请链接
        </p>
      ) : (
        <ul className="mt-4 divide-y rounded-lg border">
          {invitations.map((invitation) => (
            <li key={invitation.id} className="flex items-center gap-3 px-4 py-3 text-sm">
              <Badge variant="outline">{roleLabels[invitation.role]}</Badge>
              <span className="flex-1 text-muted-foreground">
                {invitation.createdByName} 创建，{dateFormat.format(new Date(invitation.expiresAt))}{' '}
                过期
              </span>
              <Button
                variant="ghost"
                size="sm"
                disabled={revoke.isPending}
                onClick={() => {
                  revoke.mutate({ workspaceId, invitationId: invitation.id })
                }}
              >
                撤销
              </Button>
            </li>
          ))}
        </ul>
      )}
      <CreateInvitationDialog
        workspaceId={workspaceId}
        open={creating}
        onOpenChange={setCreating}
      />
    </section>
  )
}
