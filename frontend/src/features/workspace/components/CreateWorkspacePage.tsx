import { UserMenu } from '@/features/auth'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { CreateWorkspaceForm } from './CreateWorkspaceForm'

export function CreateWorkspacePage() {
  return (
    <main className="min-h-svh bg-muted/40 px-4">
      <div className="flex justify-end py-3">
        <UserMenu />
      </div>
      <Card className="mx-auto mt-16 w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">创建团队空间</CardTitle>
          <CardDescription>空间是团队共享文档的地方，创建后可以邀请成员加入</CardDescription>
        </CardHeader>
        <CardContent>
          <CreateWorkspaceForm />
        </CardContent>
      </Card>
    </main>
  )
}
