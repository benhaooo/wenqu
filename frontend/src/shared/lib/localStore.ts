/** 浏览器本地存储的安全封装：隐私模式等场景下读写可能抛错，此时视为无数据。 */
export const localStore = {
  get(key: string): string | null {
    try {
      return window.localStorage.getItem(key)
    } catch {
      return null
    }
  },
  set(key: string, value: string) {
    try {
      window.localStorage.setItem(key, value)
    } catch {
      // 写入失败不影响功能
    }
  },
}
