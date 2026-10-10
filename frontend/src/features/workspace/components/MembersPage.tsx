import { useListWorkspaceMembers } from '@/shared/api/generated/workspace/workspace'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Badge } from '@/shared/ui/badge'
import { useCurrentWorkspace } from '../hooks/useCurrentWorkspace'
import { roleLabels } from '../roles'
import { InvitationsPanel } from './InvitationsPanel'

export function MembersPage() {
  const { data: workspace } = useCurrentWorkspace()
  const { data: members = [] } = useListWorkspaceMembers(workspace?.id ?? 0, {
    query: { enabled: workspace !== undefined, select: (result) => result.data },
  })

  if (!workspace) return null
  return (
    <div className="mx-auto grid max-w-3xl gap-10">
      <section>
        <h1 className="text-2xl font-semibold">成员</h1>
        <ul className="mt-4 divide-y rounded-lg border">
          {members.map((member) => (
            <li key={member.userId} className="flex items-center gap-3 px-4 py-3">
              <Avatar className="size-8">
                <AvatarFallback>{member.displayName.slice(0, 1)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">{member.displayName}</div>
                <div className="truncate text-sm text-muted-foreground">{member.email}</div>
              </div>
              <Badge variant="outline">{roleLabels[member.role]}</Badge>
            </li>
          ))}
        </ul>
      </section>
      {workspace.myRole === 'ADMIN' && <InvitationsPanel workspaceId={workspace.id} />}
    </div>
  )
}
