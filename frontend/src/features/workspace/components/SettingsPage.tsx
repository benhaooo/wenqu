import { useState, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { useCurrentWorkspace } from '../hooks/useCurrentWorkspace'
import { GeneralSettings } from './GeneralSettings'
import { InviteSection } from './InviteSection'
import { MemberTable } from './MemberTable'

type Tab = 'members' | 'general'

const tabs: { id: Tab; label: string }[] = [
  { id: 'members', label: '成员' },
  { id: 'general', label: '通用' },
]

/** 空间设置：成员与邀请、空间名称。通用设置中其他模块的设置项（如重新索引）由应用层通过 generalSections 传入。 */
export function SettingsPage({ generalSections }: { generalSections?: ReactNode }) {
  const { data: workspace } = useCurrentWorkspace()
  const [tab, setTab] = useState<Tab>('members')

  if (!workspace) return null
  const isAdmin = workspace.myRole === 'ADMIN'
  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-7 px-8 pt-10 pb-20 max-sm:px-4">
      <div className="flex flex-col gap-4">
        <h1 className="text-[26px] font-bold">空间设置</h1>
        <div
          role="tablist"
          aria-label="设置分类"
          className="flex gap-5 border-b border-border-subtle"
        >
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              onClick={() => {
                setTab(item.id)
              }}
              className={cn(
                '-mb-px min-h-10 border-b-2 border-transparent px-1 text-muted-foreground',
                tab === item.id && 'border-primary font-semibold text-primary-strong',
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      {tab === 'members' ? (
        <>
          {isAdmin && <InviteSection workspaceId={workspace.id} />}
          <MemberTable workspaceId={workspace.id} />
        </>
      ) : (
        <>
          <GeneralSettings workspace={workspace} editable={isAdmin} />
          {generalSections}
        </>
      )}
    </div>
  )
}
