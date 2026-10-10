import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import type { WorkspaceResponse } from '@/shared/api/generated/model'
import {
  getGetWorkspaceQueryKey,
  getListMyWorkspacesQueryKey,
  useRenameWorkspace,
} from '@/shared/api/generated/workspace/workspace'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'
import { InlineAlert } from '@/shared/ui/inline-alert'

const schema = z.object({
  name: z.string().trim().min(1, '请输入空间名称').max(50, '空间名称最多 50 个字符'),
})

export function GeneralSettings({
  workspace,
  editable,
}: {
  workspace: WorkspaceResponse
  editable: boolean
}) {
  const queryClient = useQueryClient()
  const rename = useRenameWorkspace()
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { name: workspace.name },
  })

  const onSubmit = form.handleSubmit((values) => {
    rename.mutate(
      { workspaceId: workspace.id, data: values },
      {
        onSuccess: () => {
          toast.success('空间名称已更新')
          void queryClient.invalidateQueries({ queryKey: getGetWorkspaceQueryKey(workspace.id) })
          void queryClient.invalidateQueries({ queryKey: getListMyWorkspacesQueryKey() })
        },
      },
    )
  })

  return (
    <form
      className="flex max-w-md flex-col gap-4"
      onSubmit={(event) => void onSubmit(event)}
      noValidate
    >
      <FormField
        id="workspace-name"
        label="空间名称"
        disabled={!editable}
        hint={editable ? '通常用团队或项目的名字。' : '只有管理员可以修改空间名称。'}
        error={form.formState.errors.name?.message}
        {...form.register('name')}
      />
      {rename.error && <InlineAlert>{rename.error.message}</InlineAlert>}
      {editable && (
        <Button type="submit" className="self-start" disabled={rename.isPending}>
          保存
        </Button>
      )}
    </form>
  )
}
