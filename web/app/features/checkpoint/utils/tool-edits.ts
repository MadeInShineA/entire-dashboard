/**
 * Turn file-editing tool calls (Edit, MultiEdit, Write, ...) into line diffs for inline display.
 */
import type { ToolUse } from './transcript-parser'

export type DiffLineKind = 'ctx' | 'del' | 'add'
export interface DiffLine {
  kind: DiffLineKind
  text: string
}

export interface ToolEdit {
  /** Raw path from the tool input (may be absolute) */
  rawPath: string
  /** One block per edit (MultiEdit has several) */
  blocks: DiffLine[][]
  additions: number
  deletions: number
  /** Whole-file write (Write/Create) rather than a replacement */
  isWrite: boolean
}

const FILE_PATH_KEYS = ['file_path', 'path', 'file', 'target_file', 'notebook_path'] as const
const EDIT_TOOLS = new Set(['Edit', 'str_replace', 'search_replace', 'replace'])
const MULTI_EDIT_TOOLS = new Set(['MultiEdit'])
const WRITE_TOOLS = new Set(['Write', 'Create', 'create_file'])
const NOTEBOOK_TOOLS = new Set(['NotebookEdit'])

/** Above this many old×new lines, skip the LCS and show all removed then all added */
const LCS_LIMIT = 250_000

function str(obj: Record<string, unknown>, key: string): string {
  const v = obj[key]
  return typeof v === 'string' ? v : ''
}

function splitLines(text: string): string[] {
  if (!text) return []
  const lines = text.split(/\r?\n/)
  if (lines.length > 1 && lines[lines.length - 1] === '') lines.pop()
  return lines
}

/** Line diff via longest common subsequence */
export function diffLines(before: string, after: string): DiffLine[] {
  const a = splitLines(before)
  const b = splitLines(after)
  if (a.length * b.length > LCS_LIMIT) {
    return [
      ...a.map((text) => ({ kind: 'del' as const, text })),
      ...b.map((text) => ({ kind: 'add' as const, text })),
    ]
  }
  const n = a.length
  const m = b.length
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i]![j] = a[i] === b[j] ? dp[i + 1]![j + 1]! + 1 : Math.max(dp[i + 1]![j]!, dp[i]![j + 1]!)
    }
  }
  const out: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ kind: 'ctx', text: a[i]! })
      i++
      j++
    } else if (dp[i + 1]![j]! >= dp[i]![j + 1]!) {
      out.push({ kind: 'del', text: a[i++]! })
    } else {
      out.push({ kind: 'add', text: b[j++]! })
    }
  }
  while (i < n) out.push({ kind: 'del', text: a[i++]! })
  while (j < m) out.push({ kind: 'add', text: b[j++]! })
  return out
}

export function toolFilePath(t: ToolUse): string | null {
  const input = t.input
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null
  const obj = input as Record<string, unknown>
  for (const k of FILE_PATH_KEYS) {
    const v = obj[k]
    if (typeof v === 'string' && v.length > 0) return v
  }
  return null
}

/** Diff view of a file-editing tool call, or null for other tools */
export function toolEdit(t: ToolUse): ToolEdit | null {
  const rawPath = toolFilePath(t)
  if (!rawPath || !t.tool) return null
  const input = t.input as Record<string, unknown>
  let blocks: DiffLine[][] = []
  let isWrite = false

  if (EDIT_TOOLS.has(t.tool)) {
    blocks = [diffLines(str(input, 'old_string'), str(input, 'new_string'))]
  } else if (MULTI_EDIT_TOOLS.has(t.tool) && Array.isArray(input.edits)) {
    blocks = (input.edits as unknown[])
      .filter((e): e is Record<string, unknown> => !!e && typeof e === 'object')
      .map((e) => diffLines(str(e, 'old_string'), str(e, 'new_string')))
  } else if (WRITE_TOOLS.has(t.tool)) {
    isWrite = true
    blocks = [splitLines(str(input, 'content')).map((text) => ({ kind: 'add' as const, text }))]
  } else if (NOTEBOOK_TOOLS.has(t.tool)) {
    blocks = [splitLines(str(input, 'new_source')).map((text) => ({ kind: 'add' as const, text }))]
  } else {
    return null
  }

  blocks = blocks.filter((b) => b.length > 0)
  if (!blocks.length) return null
  let additions = 0
  let deletions = 0
  for (const b of blocks) {
    for (const l of b) {
      if (l.kind === 'add') additions++
      else if (l.kind === 'del') deletions++
    }
  }
  return { rawPath, blocks, additions, deletions, isWrite }
}
