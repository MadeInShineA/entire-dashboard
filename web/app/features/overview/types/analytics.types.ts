/**
 * Overview analytics - mirrors server OverviewAnalyticsDTO
 */

export interface AnalyticsRow {
  id: number
  checkpointId: string
  repoId: number
  repoName: string
  branch: string
  commitSha: string
  commitMessage: string | null
  commitAuthorName: string | null
  commitTime: number
  agent: string | null
  filesTouched: number
  additions: number
  deletions: number
  tokenUsage: number
  sessionCount: number
  inputTokens: number
  outputTokens: number
  apiCallCount: number
  /** Prompt-cache writes / reads (Entire CLI 0.13.2+) */
  cacheCreationTokens: number
  cacheReadTokens: number
  /** Prompts in the checkpoint's sessions */
  turnCount: number
  model: string | null
}

export interface AnalyticsResponse {
  rows: AnalyticsRow[]
  truncated: boolean
  limit: number
}

export type RangePreset = '7d' | '14d' | '30d' | '90d' | 'custom'

/** Dimensions the page can be cross-filtered by */
export type FilterDim = 'repo' | 'author' | 'agent' | 'model'

export type OverviewFilters = Record<FilterDim, string | null>
