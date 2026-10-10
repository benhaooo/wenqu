import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import type { WorkspaceRole } from '@/shared/api/generated/model'
import {
  getListInvitationsQueryKey,
  useCreateInvitation,
  useListInvitations,
  useRevokeInvitation,
} from '@/shared/api/generated/workspace/workspace'
import { Button } from '@/shared/ui/button'
import { InlineAlert } from '@/shared/ui/inline-alert'
import { NativeSelect } from '@/shared/ui/native-select'
import { roleLabels } from '../roles'

const roles: WorkspaceRole[] = ['EDITOR', 'VIEWER', 'ADMIN']
const validDayOptions = [1, 7, 30]
const dateFormat = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' })

/** 管理员生成与撤销邀请链接。链接只在生成时显示一次（服务端只保存哈希）。 */
export function InviteSection({ workspaceId }: { workspaceId: number }) {
  const queryClient = useQueryClient()
  const [role, setRole] = useState<WorkspaceRole>('EDITOR')
  const [validDays, setValidDays] = useState(7)
  const [link, setLink] = useState<{ url: string; days: number } | null>(null)
  const [copied, setCopied] = useState(false)
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: getListInvitationsQueryKey(workspaceId) })
  const { data: invitations = [] } = useListInvitations(workspaceId, {
    query: { select: (result) => result.data },
  })
  const create = useCreateInvitation()
  const revoke = useRevokeInvitation({
    mutation: {
      onSuccess: () => {
        toast.success('邀请链接已撤销，使用该链接将无法加入空间')
        void refresh()
      },
      onError: (error) => toast.error(error.message),
    },
  })

  const generate = () => {
    create.mutate(
      { workspaceId, data: { role, validDays } },
      {
        onSuccess: (result) => {
          setLink({ url: `${window.location.origin}/invite/${result.data.token}`, days: validDays })
          setCopied(false)
          void refresh()
        },
      },
    )
  }

  const copy = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
    } catch {
      toast.error('复制失败，请手动选择链接复制')
    }
  }

  return (
    <section
      aria-labelledby="invite-title"
      className="flex flex-col gap-3.5 rounded-xl border border-border-subtle bg-surface-subtle px-6 py-5"
    >
      <div className="flex flex-wrap items-center gap-3">
        <h2
          id="invite-title"
          className="flex-1 text-base font-semibold whitespace-nowrap max-sm:basis-full"
        >
          邀请链接
        </h2>
        <label htmlFor="invite-role" className="text-[13px] text-muted-foreground">
          加入后的角色
        </label>
        <NativeSelect
          id="invite-role"
          value={role}
          onChange={(event) => {
            setRole(event.target.value as WorkspaceRole)
          }}
        >
          {roles.map((item) => (
            <option key={item} value={item}>
              {roleLabels[item]}
            </option>
          ))}
        </NativeSelect>
        <label htmlFor="invite-days" className="text-[13px] text-muted-foreground">
          有效期
        </label>
        <NativeSelect
          id="invite-days"
          value={validDays}
          onChange={(event) => {
            setValidDays(Number(event.target.value))
          }}
        >
          {validDayOptions.map((days) => (
            <option key={days} value={days}>
              {days} 天
            </option>
          ))}
        </NativeSelect>
        <Button onClick={generate} disabled={create.isPending}>
          生成链接
        </Button>
      </div>

      {create.error && <InlineAlert>{create.error.message}</InlineAlert>}

      {link && (
        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <input
              readOnly
              value={link.url}
              aria-label="邀请链接"
              onFocus={(event) => {
                event.target.select()
              }}
              className="h-10 min-w-0 flex-[1_1_320px] truncate rounded-md border border-border bg-background px-3.5 font-mono text-[13px] text-text-secondary outline-none"
            />
            <Button className="h-10" onClick={() => void copy(link.url)}>
              {copied ? '已复制' : '复制链接'}
            </Button>
          </div>
          <span className="text-[13px] text-muted-foreground">
            {link.days} 天后过期 · 链接只显示这一次，请复制后发给要邀请的人
          </span>
        </div>
      )}

      {invitations.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-border-subtle pt-3.5">
          <span className="text-[13px] text-muted-foreground">有效的邀请链接</span>
          <ul className="flex flex-col gap-1">
            {invitations.map((invitation) => (
              <li key={invitation.id} className="flex flex-wrap items-center gap-3 text-[13px]">
                <span className="font-medium">{roleLabels[invitation.role]}</span>
                <span className="flex-1 text-muted-foreground">
                  {invitation.createdByName} 创建 ·{' '}
                  {dateFormat.format(new Date(invitation.expiresAt))}
                  过期
                </span>
                <Button
                  variant="destructive"
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
        </div>
      )}
    </section>
  )
}
