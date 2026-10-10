export type SaveStatus = 'saved' | 'dirty' | 'saving' | 'error' | 'conflict'

export interface AutoSaverOptions<T> {
  /** 保存并返回新版本号；失败时抛出异常。 */
  save: (content: T, baseVersion: number) => Promise<number>
  getContent: () => T
  initialVersion: number
  onStatus: (status: SaveStatus) => void
  /** 判断异常是否为版本冲突（冲突时不再重试）。 */
  isConflict: (error: unknown) => boolean
  debounceMs?: number
  retryDelaysMs?: number[]
}

const DEFAULT_RETRY_DELAYS_MS = [2000, 4000, 8000, 16000, 30000]

/**
 * 自动保存：停止编辑 debounceMs 后保存；同一时刻只有一个保存请求，保存期间的修改在完成后再保存；
 * 失败按退避间隔重试，版本冲突时停止并等待用户处理。与框架无关，便于测试。
 */
export class AutoSaver<T> {
  private readonly options: Required<AutoSaverOptions<T>>
  private version: number
  private pending = false
  private inFlight = false
  private conflict = false
  private failures = 0
  private timer: ReturnType<typeof setTimeout> | null = null
  private disposed = false
  private detached = false

  constructor(options: AutoSaverOptions<T>) {
    this.options = { debounceMs: 1500, retryDelaysMs: DEFAULT_RETRY_DELAYS_MS, ...options }
    this.version = options.initialVersion
  }

  /** 最近一次保存成功后的版本，作为下一次保存的 baseVersion。 */
  get baseVersion(): number {
    return this.version
  }

  /** 是否有尚未确认保存的修改（包括正在保存的）。 */
  get hasUnsavedChanges(): boolean {
    return this.pending || this.inFlight
  }

  markDirty() {
    this.pending = true
    if (this.conflict) return
    this.options.onStatus('dirty')
    this.schedule(this.options.debounceMs)
  }

  /** 立即保存（例如离开文档或网络恢复时）。 */
  flush() {
    this.clearTimer()
    void this.run()
  }

  dispose() {
    this.disposed = true
    this.clearTimer()
  }

  /**
   * 编辑器卸载时调用：没有未保存修改则直接结束；否则以内容快照继续在后台保存（含重试），保存成功后结束。
   * 状态不再通知界面。
   */
  detach(snapshot: T) {
    this.options.getContent = () => snapshot
    this.options.onStatus = () => undefined
    if (!this.hasUnsavedChanges || this.conflict) {
      this.dispose()
      return
    }
    this.detached = true
    this.flush()
  }

  private hasNewChanges(): boolean {
    return this.pending
  }

  private isActive(): boolean {
    return !this.disposed
  }

  private schedule(delayMs: number) {
    this.clearTimer()
    this.timer = setTimeout(() => {
      this.timer = null
      void this.run()
    }, delayMs)
  }

  private clearTimer() {
    if (this.timer) clearTimeout(this.timer)
    this.timer = null
  }

  private async run() {
    if (this.inFlight || !this.pending || this.conflict) return
    this.pending = false
    this.inFlight = true
    this.options.onStatus('saving')
    try {
      this.version = await this.options.save(this.options.getContent(), this.version)
      this.failures = 0
      if (!this.isActive()) return
      if (this.detached) {
        this.dispose()
        return
      }
      // 保存期间可能有新的修改（markDirty 在 await 期间被调用）
      if (this.hasNewChanges()) {
        this.options.onStatus('dirty')
        this.schedule(this.options.debounceMs)
      } else {
        this.options.onStatus('saved')
      }
    } catch (error) {
      this.pending = true
      if (!this.isActive()) return
      if (this.options.isConflict(error)) {
        this.conflict = true
        this.options.onStatus('conflict')
        return
      }
      const delays = this.options.retryDelaysMs
      const delay = delays[Math.min(this.failures, delays.length - 1)] ?? 30000
      this.failures += 1
      this.options.onStatus('error')
      this.schedule(delay)
    } finally {
      this.inFlight = false
    }
  }
}
