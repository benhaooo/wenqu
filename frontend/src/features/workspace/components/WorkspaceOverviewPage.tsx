import { useListWorkspaceMembers } from '@/shared/api/generated/workspace/workspace'
import { Badge } from '@/shared/ui/badge'
import { Card, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { useCurrentWorkspace } from '../hooks/useCurrentWorkspace'
import { roleLabels } from '../roles'

export function WorkspaceOverviewPage() {
  const { data: workspace } = useCurrentWorkspace()
  const { data: members } = useListWorkspaceMembers(workspace?.id ?? 0, {
    query: { enabled: workspace !== undefined, select: (result) => result.data },
  })

  if (!workspace) return null
  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{workspace.name}</h1>
        <Badge variant="secondary">{roleLabels[workspace.myRole]}</Badge>
      </div>
      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader>
            <CardDescription>成员</CardDescription>
            <CardTitle className="text-3xl">{members?.length ?? '–'}</CardTitle>
          </CardHeader>
        </Card>
      </div>
    </div>
  )
}
