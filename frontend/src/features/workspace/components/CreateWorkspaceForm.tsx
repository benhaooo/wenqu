import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import {
  getListMyWorkspacesQueryKey,
  useCreateWorkspace,
} from '@/shared/api/generated/workspace/workspace'
import { Alert, AlertDescription } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'

const schema = z.object({
  name: z.string().trim().min(1, '请输入空间名称').max(50, '空间名称最多 50 个字符'),
})

type FormValues = z.infer<typeof schema>

export function CreateWorkspaceForm({ onCreated }: { onCreated?: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const createWorkspace = useCreateWorkspace()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

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
    <form className="grid gap-4" onSubmit={(event) => void onSubmit(event)} noValidate>
      {createWorkspace.error && (
        <Alert variant="destructive">
          <AlertDescription>{createWorkspace.error.message}</AlertDescription>
        </Alert>
      )}
      <FormField
        id="workspace-name"
        label="空间名称"
        placeholder="例如：后端组"
        error={form.formState.errors.name?.message}
        {...form.register('name')}
      />
      <Button type="submit" disabled={createWorkspace.isPending}>
        创建空间
      </Button>
    </form>
  )
}
