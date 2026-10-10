import { useQueryClient } from '@tanstack/react-query'
import { CopyIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import type { WorkspaceRole } from '@/shared/api/generated/model'
import {
  getListInvitationsQueryKey,
  useCreateInvitation,
} from '@/shared/api/generated/workspace/workspace'
import { Alert, AlertDescription } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { roleLabels } from '../roles'

const roles: WorkspaceRole[] = ['EDITOR', 'VIEWER', 'ADMIN']
const validDayOptions = [1, 7, 30]

export function CreateInvitationDialog({
  workspaceId,
  open,
  onOpenChange,
}: {
  workspaceId: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [role, setRole] = useState<WorkspaceRole>('EDITOR')
  const [validDays, setValidDays] = useState(7)
  const [link, setLink] = useState<string | null>(null)
  const createInvitation = useCreateInvitation()

  const close = (next: boolean) => {
    if (!next) {
      setLink(null)
      createInvitation.reset()
    }
    onOpenChange(next)
  }

  const create = () => {
    createInvitation.mutate(
      { workspaceId, data: { role, validDays } },
      {
        onSuccess: (result) => {
          setLink(`${window.location.origin}/invite/${result.data.token}`)
          void queryClient.invalidateQueries({ queryKey: getListInvitationsQueryKey(workspaceId) })
        },
      },
    )
  }

  const copy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      toast.success('链接已复制')
    } catch {
      toast.error('复制失败，请手动复制')
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>创建邀请链接</DialogTitle>
          <DialogDescription>
            {link ? '链接只显示这一次，请复制后发给要邀请的人' : '选择加入后的角色和链接有效期'}
          </DialogDescription>
        </DialogHeader>
        {link ? (
          <div className="flex gap-2">
            <Input
              readOnly
              value={link}
              aria-label="邀请链接"
              onFocus={(e) => {
                e.target.select()
              }}
            />
            <Button
              size="icon"
              variant="outline"
              aria-label="复制链接"
              onClick={() => void copy(link)}
            >
              <CopyIcon />
            </Button>
          </div>
        ) : (
          <div className="grid gap-4">
            {createInvitation.error && (
              <Alert variant="destructive">
                <AlertDescription>{createInvitation.error.message}</AlertDescription>
              </Alert>
            )}
            <div className="grid gap-2">
              <Label>加入后的角色</Label>
              <Select
                value={role}
                onValueChange={(value) => {
                  setRole(value as WorkspaceRole)
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {roles.map((item) => (
                    <SelectItem key={item} value={item}>
                      {roleLabels[item]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>有效期</Label>
              <Select
                value={String(validDays)}
                onValueChange={(value) => {
                  setValidDays(Number(value))
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {validDayOptions.map((days) => (
                    <SelectItem key={days} value={String(days)}>
                      {days} 天
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        )}
        <DialogFooter>
          {link ? (
            <Button
              onClick={() => {
                close(false)
              }}
            >
              完成
            </Button>
          ) : (
            <Button onClick={create} disabled={createInvitation.isPending}>
              生成链接
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
