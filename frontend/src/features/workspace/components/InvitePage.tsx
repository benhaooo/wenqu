import { useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import {
  getListMyWorkspacesQueryKey,
  useAcceptInvitation,
  usePreviewInvitation,
} from '@/shared/api/generated/workspace/workspace'
import { Alert, AlertDescription } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
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
    <main className="flex min-h-svh items-center justify-center bg-muted/40 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">加入团队空间</CardTitle>
          {preview.data && (
            <CardDescription>
              {preview.data.inviterName} 邀请你以「{roleLabels[preview.data.role]}」身份加入
            </CardDescription>
          )}
        </CardHeader>
        <CardContent className="grid gap-4">
          {error ? (
            <>
              <Alert variant="destructive">
                <AlertDescription>{error.message}</AlertDescription>
              </Alert>
              <Button asChild variant="outline">
                <Link to="/">返回首页</Link>
              </Button>
            </>
          ) : (
            preview.data && (
              <>
                <p className="text-2xl font-semibold">{preview.data.workspaceName}</p>
                <Button onClick={join} disabled={accept.isPending}>
                  {accept.isPending ? '加入中…' : '加入空间'}
                </Button>
              </>
            )
          )}
        </CardContent>
      </Card>
    </main>
  )
}
