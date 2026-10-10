/** 登录后的跳转目标只允许站内相对路径，防止开放重定向。 */
export function safeRedirect(target: string | null): string {
  if (target?.startsWith('/') && !target.startsWith('//')) return target
  return '/'
}
