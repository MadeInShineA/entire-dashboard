/**
 * File history module - types (mirror server FileListDTO / FileHistoryDTO)
 */
import type { CheckpointDTO } from '~/features/checkpoint/types/checkpoint.types'
import type { SessionDTO } from '~/features/checkpoint/types/session.types'

export interface FileEntry {
  path: string
  /** Number of AI sessions whose files_touched lists this path */
  sessionCount: number
  existsAtHead: boolean
}

export interface FileList {
  branches: string[]
  branch: string | null
  files: FileEntry[]
}

export interface FileSession {
  checkpoint: CheckpointDTO
  session: SessionDTO | null
  /** Session's files_touched lists the file */
  touchedFile: boolean
  /** Lines of the current version attributed to this session */
  currentLines: number
}

export interface FileLine {
  number: number
  content: string
  commitSha: string | null
  authorName: string | null
  commitTime: number
  /** Checkpoint.id (DB) of the commit that last changed the line, null if not an AI checkpoint commit */
  checkpointId: number | null
  /** Session.id (DB) the line is attributed to */
  sessionId: number | null
}

export interface FileHistory {
  path: string
  branch: string | null
  headSha: string | null
  existsAtHead: boolean
  binary: boolean
  truncated: boolean
  totalLines: number
  checkpointLines: number
  sessions: FileSession[]
  lines: FileLine[]
}
