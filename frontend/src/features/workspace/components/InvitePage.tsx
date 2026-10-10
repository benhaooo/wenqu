import { useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import {
  getListMyWorkspacesQueryKey,
  useAcceptInvitation,
  usePreviewInvitation,
} from '@/shared/api/generated/workspace/workspace'
import { BrandMark } from '@/shared/ui/brand-mark'
import { Button } from '@/shared/ui/button'
import { InlineAlert } from '@/shared/ui/inline-alert'
import { roleLabels } from '../roles'

export function InvitePage() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const preview = usePreviewInvitation(token, {
    query: { select: (result) => result.data, retry: false },
  })
  const accept = useAcceptInvitation()

  if (preview.data?.alreadyMember) {
    return <Navigate to={`/w/${String(preview.data.workspaceId)}`} replace />
  }

  const join = () => {
    accept.mutate(
      { token },
      {
        onSuccess: (result) => {
          void queryClient.invalidateQueries({ queryKey: getListMyWorkspacesQueryKey() })
          void navigate(`/w/${String(result.data.id)}`, { replace: true })
        },
      },
    )
  }

  const error = preview.error ?? accept.error
  return (
    <div className="flex min-h-svh items-center justify-center bg-canvas px-4">
      <main className="flex w-full max-w-[420px] flex-col gap-6 rounded-2xl border border-border-subtle bg-background px-8 py-9">
        <BrandMark className="size-10 rounded-[10px] text-lg" />
        {error ? (
          <>
            <h1 className="text-2xl font-bold">无法加入空间</h1>
            <InlineAlert>{error.message}</InlineAlert>
            <Button asChild variant="outline" size="lg">
              <Link to="/">返回首页</Link>
            </Button>
          </>
        ) : (
          preview.data && (
            <>
              <div className="flex flex-col gap-1.5">
                <p className="text-muted-foreground">
                  {preview.data.inviterName} 邀请你以「{roleLabels[preview.data.role]}」身份加入
                </p>
                <h1 className="text-2xl font-bold">{preview.data.workspaceName}</h1>
              </div>
              <Button size="lg" onClick={join} disabled={accept.isPending}>
                {accept.isPending ? '加入中…' : '加入空间'}
              </Button>
            </>
          )
        )}
      </main>
    </div>
  )
}
