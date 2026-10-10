import { useQueryClient } from '@tanstack/react-query'
import { MoreHorizontalIcon, PlusIcon } from 'lucide-react'
import { NavLink, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import {
  getListConversationsQueryKey,
  useDeleteConversation,
  useListConversations,
} from '@/shared/api/generated/chat/chat'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { Skeleton } from '@/shared/ui/skeleton'

/** 问答页侧栏：我的最近会话，可新建和删除。 */
export function ChatSidebar() {
  const { workspaceId } = useWorkspaceAccess()
  const activeId = Number(useParams().conversationId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: conversations } = useListConversations(workspaceId, {
    query: { select: (result) => result.data },
  })
  const remove = useDeleteConversation({
    mutation: {
      onSuccess: (_, { conversationId }) => {
        void queryClient.invalidateQueries({ queryKey: getListConversationsQueryKey(workspaceId) })
        if (conversationId === activeId) void navigate(`/w/${String(workspaceId)}/chat`)
      },
      onError: (error) => toast.error(error.message),
    },
  })

  return (
    <>
      <div className="flex min-h-8 items-center justify-between px-2.5">
        <span className="text-xs font-semibold tracking-[0.04em] text-muted-foreground">
          最近会话
        </span>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="新会话"
          asChild
          className="text-muted-foreground"
        >
          <NavLink to={`/w/${String(workspaceId)}/chat`} end>
            <PlusIcon />
          </NavLink>
        </Button>
      </div>
      {conversations === undefined ? (
        <div className="grid gap-2 px-2.5 pt-1">
          <Skeleton className="h-5" />
          <Skeleton className="h-5 w-2/3" />
        </div>
      ) : conversations.length === 0 ? (
        <p className="px-2.5 py-2 text-[13px] text-muted-foreground">还没有会话</p>
      ) : (
        <ul className="flex min-h-0 flex-col gap-0.5 overflow-auto">
          {conversations.map((conversation) => (
            <li key={conversation.id} className="group flex items-center">
              <NavLink
                to={`/w/${String(workspaceId)}/chat/${String(conversation.id)}`}
                className={({ isActive }) =>
                  cn(
                    'min-h-8 min-w-0 flex-1 truncate rounded-sm px-2.5 leading-8 text-foreground hover:bg-secondary',
                    isActive && 'bg-background font-medium hover:bg-background',
                  )
                }
              >
                {conversation.title}
              </NavLink>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-6 shrink-0 opacity-0 group-hover:opacity-100 data-[state=open]:opacity-100 focus-visible:opacity-100"
                    aria-label={`${conversation.title} 的操作`}
                  >
                    <MoreHorizontalIcon />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  <DropdownMenuItem
                    variant="destructive"
                    onSelect={() => {
                      remove.mutate({ workspaceId, conversationId: conversation.id })
                    }}
                  >
                    删除会话
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
