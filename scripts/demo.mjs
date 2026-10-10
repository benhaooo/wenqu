// 演示数据与检索评测。依赖本地运行中的后端（make deps && make dev-backend）。
//   node scripts/demo.mjs seed       创建演示账号与「示例团队」空间，导入 samples/knowledge-base 并等待索引完成
//   node scripts/demo.mjs evaluate   用 samples/evaluation.json 评测检索命中率，结果输出为 Markdown 表格
//                                    expected 为 null 的问题与知识库无关，检索结果应为空（低于相似度阈值）
import { readFileSync, readdirSync } from 'node:fs'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const env = Object.fromEntries(
  readFileSync(join(root, '.env'), 'utf8')
    .split('\n')
    .filter((line) => /^[A-Z_]+=/.test(line))
    .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]),
)
const api = `http://localhost:${env.BACKEND_PORT ?? '8080'}/api`

// 仅用于本地演示的账号，见 samples/README.md
const DEMO = { email: 'demo@example.test', password: 'wenqu-demo-2026', displayName: '演示用户' }
const WORKSPACE_NAME = '示例团队'

async function call(path, { token, method = 'GET', body, form } = {}) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: form ?? (body ? JSON.stringify(body) : undefined),
  })
  const result = await response.json()
  if (result.code !== 0) throw new Error(`${method} ${path}: ${result.message}`)
  return result.data
}

async function login() {
  try {
    return (await call('/auth/login', { method: 'POST', body: DEMO })).accessToken
  } catch {
    return (await call('/auth/register', { method: 'POST', body: DEMO })).accessToken
  }
}

async function demoWorkspace(token) {
  const existing = (await call('/workspaces', { token })).find((w) => w.name === WORKSPACE_NAME)
  return existing ?? call('/workspaces', { token, method: 'POST', body: { name: WORKSPACE_NAME } })
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function seed() {
  const token = await login()
  const workspace = await demoWorkspace(token)
  const titles = new Set((await call(`/workspaces/${workspace.id}/nodes`, { token })).map((n) => n.title))
  const directory = join(root, 'samples', 'knowledge-base')
  for (const file of readdirSync(directory).sort()) {
    if (titles.has(basename(file, '.md'))) continue
    const form = new FormData()
    form.append('file', new Blob([readFileSync(join(directory, file))]), file)
    await call(`/workspaces/${workspace.id}/imports`, { token, method: 'POST', form })
    console.log(`导入 ${file}`)
  }
  for (;;) {
    const jobs = await call(`/workspaces/${workspace.id}/imports`, { token })
    const unfinished = jobs.filter((job) => !['SUCCEEDED', 'FAILED'].includes(job.status))
    if (unfinished.length === 0) {
      for (const job of jobs.filter((job) => job.status === 'FAILED')) {
        console.log(`失败 ${job.fileName}：${job.errorMessage}`)
      }
      break
    }
    await sleep(1000)
  }
  console.log(`完成：空间「${WORKSPACE_NAME}」（id ${workspace.id}），账号 ${DEMO.email}`)
}

/** 提问并在收到 retrieval 事件后停止生成，只取检索结果，不等待模型回答。 */
async function retrieve(token, workspaceId, question) {
  const conversation = await call(`/workspaces/${workspaceId}/conversations`, { token, method: 'POST' })
  const controller = new AbortController()
  const response = await fetch(`${api}/workspaces/${workspaceId}/conversations/${conversation.id}/messages`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, scopeType: 'WORKSPACE' }),
    signal: controller.signal,
  })
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) return []
      buffer += value
      const match = /data:(.*)\n/.exec(buffer)
      if (match) {
        const event = JSON.parse(match[1])
        if (event.type === 'RETRIEVAL') return event.documents.map((d) => d.title)
      }
    }
  } finally {
    controller.abort()
    await call(`/workspaces/${workspaceId}/conversations/${conversation.id}`, { token, method: 'DELETE' }).catch(
      () => {},
    )
  }
}

async function evaluate() {
  const token = await login()
  const workspace = await demoWorkspace(token)
  const cases = JSON.parse(readFileSync(join(root, 'samples', 'evaluation.json'), 'utf8'))
  const rows = []
  let hits = 0
  let reciprocalRank = 0
  let rejected = 0
  const relevant = cases.filter((item) => item.expected !== null)
  for (const { question, expected } of cases) {
    const documents = await retrieve(token, workspace.id, question)
    if (expected === null) {
      if (documents.length === 0) rejected++
      rows.push(`| ${question} | （无关问题） | ${documents.length === 0 ? '正确拒答' : '误检索'} | ${documents.join('、') || '（无）'} |`)
      continue
    }
    const rank = documents.indexOf(expected) + 1
    if (rank > 0) {
      hits++
      reciprocalRank += 1 / rank
    }
    rows.push(`| ${question} | ${expected} | ${rank > 0 ? `第 ${rank} 位` : '未命中'} | ${documents.join('、') || '（无）'} |`)
  }
  console.log('| 问题 | 正确文档 | 结果 | 检索到的文档（按相关度） |')
  console.log('|---|---|---|---|')
  console.log(rows.join('\n'))
  console.log(
    `\n命中率 ${hits}/${relevant.length}（${((hits / relevant.length) * 100).toFixed(0)}%），MRR ${(reciprocalRank / relevant.length).toFixed(2)}，无关问题拒答 ${rejected}/${cases.length - relevant.length}`,
  )
}

const command = process.argv[2]
if (command === 'seed') await seed()
else if (command === 'evaluate') await evaluate()
else console.log('用法：node scripts/demo.mjs seed | evaluate')
