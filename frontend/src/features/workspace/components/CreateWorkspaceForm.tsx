import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import {
  getListMyWorkspacesQueryKey,
  useCreateWorkspace,
} from '@/shared/api/generated/workspace/workspace'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'
import { InlineAlert } from '@/shared/ui/inline-alert'

const schema = z.object({
  name: z.string().trim().min(1, '请输入空间名称').max(50, '空间名称最多 50 个字符'),
})

export function CreateWorkspaceForm({ onCreated }: { onCreated?: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const createWorkspace = useCreateWorkspace()
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) })

  const onSubmit = form.handleSubmit((values) => {
    createWorkspace.mutate(
      { data: values },
      {
        onSuccess: (result) => {
          void queryClient.invalidateQueries({ queryKey: getListMyWorkspacesQueryKey() })
          onCreated?.()
          void navigate(`/w/${String(result.data.id)}`)
        },
      },
    )
  })

  return (
    <form className="flex flex-col gap-6" onSubmit={(event) => void onSubmit(event)} noValidate>
      <FormField
        id="workspace-name"
        label="空间名称"
        placeholder="例如：前端团队"
        hint="通常用团队或项目的名字，可以随时修改。"
        error={form.formState.errors.name?.message}
        {...form.register('name')}
      />
      {createWorkspace.error && <InlineAlert>{createWorkspace.error.message}</InlineAlert>}
      <Button type="submit" size="lg" className="self-end" disabled={createWorkspace.isPending}>
        创建空间
      </Button>
    </form>
  )
}
