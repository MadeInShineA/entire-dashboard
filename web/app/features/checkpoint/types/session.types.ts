/**
 * Session module - types
 */

export interface SessionDTO {
  id: number
  checkpointId: number
  sessionId: string
  sessionIndex: number
  strategy?: string
  sessionCreatedAt?: number
  branch?: string
  checkpointsCount?: number
  filesTouchedCount?: number
  filesTouchedJson?: string
  agent?: string
  inputTokens?: number
  outputTokens?: number
  apiCallCount?: number
  agentLines?: number
  humanAdded?: number
  humanModified?: number
  humanRemoved?: number
  totalCommitted?: number
  agentPercentage?: number
  promptPreview?: string
  /** Entire CLI 0.13.2+ */
  cacheCreationTokens?: number
  cacheReadTokens?: number
  model?: string
  turnCount?: number
  createdAt?: number
  updatedAt?: number
}
