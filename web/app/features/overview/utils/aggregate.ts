/**
 * Pure aggregations over analytics rows (Entire CLI 0.13.2+ metadata). Everything is computed in the
 * browser's timezone, so "per day" and "hour of day" match what the user reads on the axes.
 */
import { formatDate } from '~/shared/utils/date'
import type { AnalyticsRow, FilterDim, OverviewFilters } from '../types/analytics.types'

export const UNKNOWN = 'Unknown'

export function dimValue(row: AnalyticsRow, dim: FilterDim): string {
  switch (dim) {
    case 'repo': return row.repoName || UNKNOWN
    case 'author': return row.commitAuthorName || UNKNOWN
    case 'agent': return row.agent || UNKNOWN
    case 'model': return row.model || UNKNOWN
  }
}

/** Apply every active filter except `except` (crossfilter: a dimension's own chart still shows its siblings) */
export function applyFilters(rows: AnalyticsRow[], filters: OverviewFilters, except?: FilterDim): AnalyticsRow[] {
  const active = (Object.keys(filters) as FilterDim[]).filter((d) => d !== except && filters[d] != null)
  if (!active.length) return rows
  return rows.filter((r) => active.every((d) => dimValue(r, d) === filters[d]))
}

/** Per-kind token counts; checkpoints without session token data fall back to the checkpoint total as "other" */
export function rowTokenParts(r: AnalyticsRow) {
  const input = r.inputTokens ?? 0
  const output = r.outputTokens ?? 0
  const cacheWrite = r.cacheCreationTokens ?? 0
  const cacheRead = r.cacheReadTokens ?? 0
  const split = input + output + cacheWrite + cacheRead
  return { input, output, cacheWrite, cacheRead, other: split > 0 ? 0 : (r.tokenUsage ?? 0) }
}

/** All tokens the checkpoint's sessions consumed, cache included */
export function rowTokens(r: AnalyticsRow): number {
  const p = rowTokenParts(r)
  return p.input + p.output + p.cacheWrite + p.cacheRead + p.other
}

export function linesChanged(r: AnalyticsRow): number {
  return (r.additions ?? 0) + (r.deletions ?? 0)
}

export interface Totals {
  checkpoints: number
  repos: number
  authors: number
  turns: number
  additions: number
  deletions: number
  tokens: number
  inputTokens: number
  outputTokens: number
  cacheWriteTokens: number
  cacheReadTokens: number
  otherTokens: number
  /** Share of tokens served from the prompt cache, 0..1, null without tokens */
  cacheReadShare: number | null
  /** Tokens per changed line, null without changes */
  tokensPerLine: number | null
}

export function totals(rows: AnalyticsRow[]): Totals {
  const repos = new Set<string>()
  const authors = new Set<string>()
  const t = { turns: 0, additions: 0, deletions: 0, input: 0, output: 0, cacheWrite: 0, cacheRead: 0, other: 0 }
  for (const r of rows) {
    repos.add(dimValue(r, 'repo'))
    if (r.commitAuthorName) authors.add(r.commitAuthorName)
    t.turns += r.turnCount ?? 0
    t.additions += r.additions ?? 0
    t.deletions += r.deletions ?? 0
    const p = rowTokenParts(r)
    t.input += p.input
    t.output += p.output
    t.cacheWrite += p.cacheWrite
    t.cacheRead += p.cacheRead
    t.other += p.other
  }
  const tokens = t.input + t.output + t.cacheWrite + t.cacheRead + t.other
  const lines = t.additions + t.deletions
  return {
    checkpoints: rows.length,
    repos: repos.size,
    authors: authors.size,
    turns: t.turns,
    additions: t.additions,
    deletions: t.deletions,
    tokens,
    inputTokens: t.input,
    outputTokens: t.output,
    cacheWriteTokens: t.cacheWrite,
    cacheReadTokens: t.cacheRead,
    otherTokens: t.other,
    cacheReadShare: tokens > 0 ? t.cacheRead / tokens : null,
    tokensPerLine: lines > 0 ? tokens / lines : null,
  }
}

export interface DayBucket {
  date: string
  checkpoints: number
  turns: number
  additions: number
  deletions: number
  inputTokens: number
  outputTokens: number
  cacheWriteTokens: number
  cacheReadTokens: number
  /** Tokens of checkpoints without a per-session split */
  otherTokens: number
  authors: Set<string>
  repos: Set<string>
}

/** One bucket per calendar day in `dates`, in order (days without data stay at zero) */
export function byDay(rows: AnalyticsRow[], dates: string[]): DayBucket[] {
  const map = new Map<string, DayBucket>(dates.map((date) => [date, {
    date, checkpoints: 0, turns: 0, additions: 0, deletions: 0,
    inputTokens: 0, outputTokens: 0, cacheWriteTokens: 0, cacheReadTokens: 0, otherTokens: 0,
    authors: new Set(), repos: new Set(),
  }]))
  for (const r of rows) {
    const b = map.get(formatDate(new Date(r.commitTime)))
    if (!b) continue
    b.checkpoints++
    b.turns += r.turnCount ?? 0
    b.additions += r.additions ?? 0
    b.deletions += r.deletions ?? 0
    b.repos.add(dimValue(r, 'repo'))
    if (r.commitAuthorName) b.authors.add(r.commitAuthorName)
    const p = rowTokenParts(r)
    b.inputTokens += p.input
    b.outputTokens += p.output
    b.cacheWriteTokens += p.cacheWrite
    b.cacheReadTokens += p.cacheRead
    b.otherTokens += p.other
  }
  return dates.map((d) => map.get(d)!)
}

export function dayTokens(d: DayBucket): number {
  return d.inputTokens + d.outputTokens + d.cacheWriteTokens + d.cacheReadTokens + d.otherTokens
}

export interface GroupStat {
  key: string
  checkpoints: number
  additions: number
  deletions: number
  tokens: number
  turns: number
}

export function groupBy(rows: AnalyticsRow[], dim: FilterDim): GroupStat[] {
  const map = new Map<string, GroupStat>()
  for (const r of rows) {
    const key = dimValue(r, dim)
    let g = map.get(key)
    if (!g) map.set(key, (g = { key, checkpoints: 0, additions: 0, deletions: 0, tokens: 0, turns: 0 }))
    g.checkpoints++
    g.additions += r.additions ?? 0
    g.deletions += r.deletions ?? 0
    g.tokens += rowTokens(r)
    g.turns += r.turnCount ?? 0
  }
  return [...map.values()]
}

/** Checkpoint counts per [weekday (0 = Monday), hour] */
export function punchCard(rows: AnalyticsRow[]): number[][] {
  const grid = Array.from({ length: 7 }, () => new Array<number>(24).fill(0))
  for (const r of rows) {
    const d = new Date(r.commitTime)
    grid[(d.getDay() + 6) % 7]![d.getHours()]!++
  }
  return grid
}

/** Relative change, null when there is no base to compare to */
export function relativeChange(current: number, previous: number): number | null {
  if (!previous) return null
  return (current - previous) / previous
}
