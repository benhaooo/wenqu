import { ChevronsUpDownIcon, LogOutIcon } from 'lucide-react'
import { useGetCurrentUser } from '@/shared/api/generated/auth/auth'
import { Avatar } from '@/shared/ui/avatar-initial'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { useSignOut } from '../hooks/useSignOut'

/** 侧栏底部的当前用户：昵称与副标题（如在当前空间的角色），点击展开账号菜单。 */
export function UserMenu({ subtitle }: { subtitle?: string }) {
  const { data: user } = useGetCurrentUser({ query: { select: (result) => result.data } })
  const signOut = useSignOut()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="账号菜单"
        className="flex min-h-11 w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left outline-none hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring aria-expanded:bg-secondary"
      >
        <Avatar name={user?.displayName ?? ''} id={user?.id ?? 0} />
        <span className="flex min-w-0 flex-1 flex-col leading-tight">
          <span className="truncate font-medium">{user?.displayName}</span>
          {subtitle && <span className="text-xs text-muted-foreground">{subtitle}</span>}
        </span>
        <ChevronsUpDownIcon className="size-4 text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" side="top" className="w-56">
        <DropdownMenuLabel className="font-normal text-muted-foreground">
          {user?.email}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void signOut()}>
          <LogOutIcon />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
