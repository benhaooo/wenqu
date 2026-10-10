import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { useLogin, useRegister } from '@/shared/api/generated/auth/auth'
import { setAccessToken } from '@/shared/api/session'
import { cn } from '@/shared/lib/cn'
import { safeRedirect } from '@/shared/lib/redirect'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'
import { InlineAlert } from '@/shared/ui/inline-alert'
import { BrandPanel } from './BrandPanel'

const loginSchema = z.object({
  email: z.string().trim().min(1, '请输入邮箱'),
  password: z.string().min(1, '请输入密码'),
})

// 与后端 RegisterRequest 的校验规则一致
const registerSchema = z.object({
  displayName: z.string().trim().min(1, '请输入昵称').max(30, '昵称最多 30 个字符'),
  email: z.email('邮箱格式不正确'),
  password: z.string().min(8, '密码长度为 8～72 位').max(72, '密码长度为 8～72 位'),
})

type Mode = 'login' | 'register'

/** 登录与注册：左侧产品介绍，右侧表单，两种模式通过顶部切换。 */
export function AuthPage({ mode }: { mode: Mode }) {
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const query = redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''

  return (
    <div className="flex min-h-svh flex-wrap">
      <BrandPanel />
      <main className="flex flex-[1_1_480px] items-center justify-center px-8 py-14">
        <div className="flex w-full max-w-[380px] flex-col gap-6">
          <nav aria-label="登录或注册" className="flex gap-1 rounded-lg bg-muted p-1">
            <ModeLink to={`/login${query}`} active={mode === 'login'}>
              登录
            </ModeLink>
            <ModeLink to={`/register${query}`} active={mode === 'register'}>
              注册
            </ModeLink>
          </nav>
          <h2 className="text-2xl font-bold">{mode === 'login' ? '欢迎回来' : '创建你的账号'}</h2>
          {mode === 'login' ? (
            <LoginForm redirect={redirect} />
          ) : (
            <RegisterForm redirect={redirect} />
          )}
          <p className="text-[13px] text-muted-foreground">
            收到了邀请链接？登录后会自动加入对应的团队空间。
          </p>
        </div>
      </main>
    </div>
  )
}

function ModeLink({ to, active, children }: { to: string; active: boolean; children: string }) {
  return (
    <Link
      to={to}
      replace
      aria-current={active ? 'page' : undefined}
      className={cn(
        'flex h-10 flex-1 items-center justify-center rounded-md text-muted-foreground',
        active && 'bg-background font-semibold text-primary-strong shadow-sm',
      )}
    >
      {children}
    </Link>
  )
}

function LoginForm({ redirect }: { redirect: string | null }) {
  const navigate = useNavigate()
  const login = useLogin()
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) })

  const onSubmit = form.handleSubmit((values) => {
    login.mutate(
      { data: values },
      {
        onSuccess: (result) => {
          setAccessToken(result.data.accessToken)
          void navigate(safeRedirect(redirect), { replace: true })
        },
      },
    )
  })

  return (
    <form className="flex flex-col gap-4" onSubmit={(event) => void onSubmit(event)} noValidate>
      <FormField
        id="email"
        label="邮箱"
        type="email"
        autoComplete="email"
        placeholder="name@company.com"
        error={form.formState.errors.email?.message}
        {...form.register('email')}
      />
      <FormField
        id="password"
        label="密码"
        type="password"
        autoComplete="current-password"
        error={form.formState.errors.password?.message}
        {...form.register('password')}
      />
      {login.error && <InlineAlert>{login.error.message}</InlineAlert>}
      <Button type="submit" size="lg" disabled={login.isPending}>
        {login.isPending ? '登录中…' : '登录'}
      </Button>
    </form>
  )
}

function RegisterForm({ redirect }: { redirect: string | null }) {
  const navigate = useNavigate()
  const register = useRegister()
  const form = useForm<z.infer<typeof registerSchema>>({ resolver: zodResolver(registerSchema) })

  const onSubmit = form.handleSubmit((values) => {
    register.mutate(
      { data: values },
      {
        onSuccess: (result) => {
          setAccessToken(result.data.accessToken)
          void navigate(safeRedirect(redirect), { replace: true })
        },
      },
    )
  })

  return (
    <form className="flex flex-col gap-4" onSubmit={(event) => void onSubmit(event)} noValidate>
      <FormField
        id="displayName"
        label="昵称"
        autoComplete="nickname"
        placeholder="团队里大家怎么称呼你"
        error={form.formState.errors.displayName?.message}
        {...form.register('displayName')}
      />
      <FormField
        id="email"
        label="邮箱"
        type="email"
        autoComplete="email"
        placeholder="name@company.com"
        error={form.formState.errors.email?.message}
        {...form.register('email')}
      />
      <FormField
        id="password"
        label="密码"
        type="password"
        autoComplete="new-password"
        placeholder="至少 8 位"
        error={form.formState.errors.password?.message}
        {...form.register('password')}
      />
      {register.error && <InlineAlert>{register.error.message}</InlineAlert>}
      <Button type="submit" size="lg" disabled={register.isPending}>
        {register.isPending ? '创建中…' : '创建账号'}
      </Button>
    </form>
  )
}
