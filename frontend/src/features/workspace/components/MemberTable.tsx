import { useState } from 'react'
import { useGetCurrentUser } from '@/shared/api/generated/auth/auth'
import { useListWorkspaceMembers } from '@/shared/api/generated/workspace/workspace'
import { Avatar } from '@/shared/ui/avatar-initial'
import { roleLabels } from '../roles'

const dateFormat = new Intl.DateTimeFormat('sv-SE')

export function MemberTable({ workspaceId }: { workspaceId: number }) {
  const [keyword, setKeyword] = useState('')
  const { data: me } = useGetCurrentUser({ query: { select: (result) => result.data } })
  const { data: members = [] } = useListWorkspaceMembers(workspaceId, {
    query: { select: (result) => result.data },
  })
  const q = keyword.trim().toLowerCase()
  const visible = q
    ? members.filter(
        (member) =>
          member.displayName.toLowerCase().includes(q) || member.email.toLowerCase().includes(q),
      )
    : members

  return (
    <section aria-labelledby="member-title" className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <h2 id="member-title" className="flex-1 text-base font-semibold">
          成员 · {members.length}
        </h2>
        <label htmlFor="member-search" className="sr-only">
          搜索成员
        </label>
        <input
          id="member-search"
          type="search"
          value={keyword}
          placeholder="搜索姓名或邮箱"
          onChange={(event) => {
            setKeyword(event.target.value)
          }}
          className="h-9 w-[220px] rounded-md border border-input px-3 outline-none focus-visible:border-primary focus-visible:ring-3 focus-visible:ring-primary-soft max-sm:w-40"
        />
      </div>
      <div className="overflow-x-auto rounded-lg border border-border-subtle">
        <table className="w-full min-w-[560px] border-collapse">
          <thead>
            <tr className="bg-canvas text-left text-[13px] text-muted-foreground">
              <th scope="col" className="px-4 py-2.5 font-medium">
                成员
              </th>
              <th scope="col" className="w-40 px-4 py-2.5 font-medium">
                角色
              </th>
              <th scope="col" className="w-32 px-4 py-2.5 font-medium">
                加入时间
              </th>
            </tr>
          </thead>
          <tbody>
            {visible.map((member) => (
              <tr key={member.userId} className="border-t border-border-subtle">
                <td className="px-4 py-3">
                  <span className="flex items-center gap-2.5">
                    <Avatar name={member.displayName} id={member.userId} className="size-8" />
                    <span className="flex flex-col leading-snug">
                      <span className="font-medium">
                        {member.displayName}
                        {member.userId === me?.id && '（你）'}
                      </span>
                      <span className="text-xs text-muted-foreground">{member.email}</span>
                    </span>
                  </span>
                </td>
                <td className="px-4 py-3 text-text-secondary">{roleLabels[member.role]}</td>
                <td className="px-4 py-3 text-muted-foreground">
                  {dateFormat.format(new Date(member.joinedAt))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {visible.length === 0 && (
          <p className="px-4 py-6 text-center text-muted-foreground">没有匹配的成员</p>
        )}
      </div>
      <p className="text-[13px] text-muted-foreground">
        管理员：管理成员与设置 · 编辑者：创建和编辑文档 · 只读成员：查看文档
      </p>
    </section>
  )
}
