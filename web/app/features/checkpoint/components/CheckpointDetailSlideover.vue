<script setup lang="ts">
import { sessionApi } from '../api/session.api'
import { parseTranscript } from '../utils/transcript-parser'
import {
  buildFileTree,
  collapseSingleChildChains,
  flattenTree,
  collectFolderPaths,
} from '../utils/file-tree'
import type { CheckpointDTO } from '../types/checkpoint.types'
import type { SessionDTO } from '../types/session.types'
import type { ParsedTranscript } from '../utils/transcript-parser'

const props = defineProps<{
  checkpoint: CheckpointDTO | null
  /** Session (Session.id) to select when opening; defaults to the first one */
  focusSessionId?: number | null
  /** Repo-relative file to show the diff of when opening */
  focusFile?: string | null
}>()

const open = defineModel<boolean>('open', { default: false })

const sessions = ref<SessionDTO[]>([])
const sessionsLoading = ref(false)
const selectedSessionIndex = ref(0)
const transcriptRaw = ref<string | null>(null)
const transcriptLoading = ref(false)
const parsedTranscript = ref<ParsedTranscript | null>(null)
const selectedFile = ref<string | null>(null)
const expandedPaths = ref<Set<string>>(new Set())

const selectedSession = computed(() => {
  const idx = selectedSessionIndex.value
  return sessions.value[idx] ?? null
})

watch(
  () => [props.checkpoint, open.value] as const,
  async ([checkpoint, isOpen]) => {
    if (!checkpoint || !isOpen) return
    sessionsLoading.value = true
    try {
      sessions.value = await sessionApi.list(checkpoint.id)
      const focusIdx = props.focusSessionId != null
        ? sessions.value.findIndex((s) => s.id === props.focusSessionId)
        : -1
      selectedSessionIndex.value = Math.max(focusIdx, 0)
      selectedFile.value = null
      await loadTranscript(sessions.value[selectedSessionIndex.value]?.id)
      if (props.focusFile) {
        selectedFile.value = matchTranscriptFile(props.focusFile)
      }
    } finally {
      sessionsLoading.value = false
    }
  }
)

/// Transcript paths may be absolute or relative to the agent's cwd; repo paths are relative to the git root
function pathsMatch(a: string, b: string): boolean {
  return a === b || a.endsWith('/' + b) || b.endsWith('/' + a)
}

function matchTranscriptFile(repoPath: string): string | null {
  const changes = parsedTranscript.value?.fileChanges ?? []
  return changes.find((c) => c.file === repoPath)?.file
    ?? changes.find((c) => pathsMatch(c.file, repoPath))?.file
    ?? null
}

/// Map a transcript file path to the repo-relative path listed in the session's files_touched
function toRepoPath(transcriptPath: string): string {
  let touched: string[] = []
  try {
    touched = JSON.parse(selectedSession.value?.filesTouchedJson || '[]')
  } catch {
    touched = []
  }
  return touched.find((t) => t === transcriptPath)
    ?? touched.find((t) => pathsMatch(transcriptPath, t))
    ?? transcriptPath.replace(/^\/+/, '')
}

/// From an inline edit in the chat: switch to the session's full diff for that file
function showDiffFor(rawPath: string) {
  const match = matchTranscriptFile(rawPath)
  if (match) selectedFile.value = match
}

function openInFiles(transcriptPath: string) {
  if (!props.checkpoint) return
  open.value = false
  navigateTo({
    path: '/admin/files',
    query: {
      repoId: String(props.checkpoint.repoId),
      path: toRepoPath(transcriptPath),
    },
  })
}

async function loadTranscript(sessionId?: number) {
  if (!sessionId) {
    transcriptRaw.value = null
    parsedTranscript.value = null
    return
  }
  transcriptLoading.value = true
  try {
    try {
      parsedTranscript.value = await sessionApi.getNormalizedTranscript(sessionId)
      transcriptRaw.value = null
    } catch {
      transcriptRaw.value = await sessionApi.getContent(sessionId, 'transcript')
      parsedTranscript.value = parseTranscript(transcriptRaw.value)
    }
  } catch {
    transcriptRaw.value = null
    parsedTranscript.value = null
  } finally {
    transcriptLoading.value = false
  }
}

async function selectSession(idx: number) {
  selectedSessionIndex.value = idx
  const session = sessions.value[idx]
  if (session) {
    await loadTranscript(session.id)
  }
  selectedFile.value = null
}

function selectFile(file: string) {
  selectedFile.value = selectedFile.value === file ? null : file
}

const fileTree = computed(() => {
  const changes = parsedTranscript.value?.fileChanges ?? []
  const tree = buildFileTree(changes)
  return collapseSingleChildChains(tree)
})

const flatNodes = computed(() => flattenTree(fileTree.value, expandedPaths.value))

function toggleExpand(path: string) {
  const next = new Set(expandedPaths.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  expandedPaths.value = next
}

watch(
  () => parsedTranscript.value?.fileChanges,
  (changes) => {
    if (!changes?.length) {
      expandedPaths.value = new Set()
      return
    }
    const tree = buildFileTree(changes)
    const collapsed = collapseSingleChildChains(tree)
    expandedPaths.value = collectFolderPaths(collapsed)
  },
  { immediate: true }
)

const stepsLabel = (idx: number) => {
  const session = sessions.value[idx]
  if (!session) return ''
  if (parsedTranscript.value && idx === selectedSessionIndex.value) {
    return `${parsedTranscript.value.stepsCount ?? 0} steps`
  }
  return ''
}
</script>

<template>
  <USlideover
    v-model:open="open"
    :ui="{
      content: 'right-0 inset-y-0 w-[1200px] max-w-[96vw]',
      header: 'flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700',
      body: 'p-0 overflow-hidden flex flex-col',
    }"
  >
    <template #header>
      <div class="flex items-center gap-2 min-w-0">
        <h2 class="text-base font-medium truncate">
          {{ checkpoint?.checkpointId ?? 'Checkpoint' }}
        </h2>
        <span v-if="checkpoint?.repoName" class="text-sm text-gray-500 truncate">
          {{ checkpoint.repoName }}
        </span>
      </div>
      <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
    </template>

    <template #body>
      <div v-if="!checkpoint" class="p-6 text-gray-500">
        Select a checkpoint
      </div>

      <div v-else class="flex flex-1 min-h-0">
        <!-- Left sidebar: Sessions + Files -->
        <div class="w-64 shrink-0 border-r border-gray-200 dark:border-gray-700 flex flex-col overflow-hidden">
          <div class="p-3 border-b border-gray-200 dark:border-gray-700">
            <div class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">Sessions</div>
            <div v-if="sessionsLoading" class="flex justify-center py-4">
              <UIcon name="i-lucide-loader-2" class="w-5 h-5 animate-spin text-primary" />
            </div>
            <div v-else class="space-y-1">
              <button
                v-for="(s, i) in sessions"
                :key="s.id"
                type="button"
                class="w-full text-left px-3 py-2 rounded-md text-sm transition-colors"
                :class="[
                  selectedSessionIndex === i
                    ? 'bg-primary/10 text-primary'
                    : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-default',
                ]"
                @click="selectSession(i)"
              >
                <div class="font-medium truncate">{{ s.promptPreview || `Session ${i}` }}</div>
                <div class="text-xs text-gray-500 mt-0.5">
                  {{ stepsLabel(i) || 'OpenCode' }}
                </div>
              </button>
            </div>
          </div>

          <div class="flex-1 p-3 overflow-auto min-h-0">
            <div class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
              Files {{ parsedTranscript?.fileChanges?.length ?? 0 }}
            </div>
            <div v-if="transcriptLoading" class="text-sm text-gray-500">Loading...</div>
            <div v-else-if="flatNodes.length" class="space-y-0">
              <div
                v-for="{ node, depth } in flatNodes"
                :key="node.fullPath || node.name"
                class="group flex items-center min-w-0"
              >
              <component
                :is="node.isFile ? 'button' : 'div'"
                :type="node.isFile ? 'button' : undefined"
                class="flex items-center gap-1.5 py-1 px-2 rounded-md text-sm min-w-0 w-full text-left transition-colors"
                :class="[
                  depth > 0 && 'border-l border-gray-200 dark:border-gray-600',
                  node.isFile && selectedFile === node.fullPath && 'bg-primary/10 text-primary',
                  node.isFile && selectedFile !== node.fullPath && 'hover:bg-gray-100 dark:hover:bg-gray-800',
                  !node.isFile && 'cursor-pointer',
                ]"
                :style="{ paddingLeft: `${12 + depth * 16}px` }"
                @click="node.isFile ? selectFile(node.fullPath) : toggleExpand(node.fullPath)"
              >
                <span
                  v-if="!node.isFile"
                  class="shrink-0"
                >
                  <UIcon
                    :name="expandedPaths.has(node.fullPath) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
                    class="w-3.5 h-3.5 text-gray-500"
                  />
                </span>
                <span v-else class="w-4 shrink-0" />
                <UIcon
                  :name="node.isFile ? 'i-lucide-file-code' : (expandedPaths.has(node.fullPath) ? 'i-lucide-folder-open' : 'i-lucide-folder')"
                  class="w-4 h-4 shrink-0 text-gray-500"
                />
                <span class="flex-1 truncate">{{ node.name }}</span>
                <span
                  v-if="node.isFile && (node.additions !== undefined || node.deletions !== undefined)"
                  class="shrink-0 text-xs text-gray-500 tabular-nums"
                >
                  +{{ node.additions ?? 0 }} -{{ node.deletions ?? 0 }}
                </span>
              </component>
              <UTooltip v-if="node.isFile" text="Open in Files: sessions and line-by-line history">
                <UButton
                  icon="i-lucide-file-search"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  class="shrink-0 opacity-60 group-hover:opacity-100"
                  aria-label="Open in Files"
                  @click="openInFiles(node.fullPath)"
                />
              </UTooltip>
              </div>
            </div>
            <div v-else-if="!transcriptLoading" class="text-sm text-gray-500">
              No files
            </div>
          </div>
        </div>

        <!-- Right: Transcript or Diff -->
        <div class="flex-1 min-w-0 flex flex-col overflow-hidden">
          <div v-if="transcriptLoading" class="flex-1 flex items-center justify-center p-8">
            <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
          </div>

          <div v-else-if="selectedFile && parsedTranscript" class="flex-1 overflow-auto p-4 space-y-3">
            <UButton
              size="xs"
              color="neutral"
              variant="soft"
              icon="i-lucide-arrow-left"
              label="Back to conversation"
              @click="selectedFile = null"
            />
            <SessionFileDiffView
              :parsed="parsedTranscript"
              :file="selectedFile"
            />
          </div>

          <div v-else-if="parsedTranscript" class="flex-1 overflow-auto p-4">
            <SessionTranscriptViewer
              :parsed="parsedTranscript"
              @show-file-diff="showDiffFor"
              @open-in-files="openInFiles"
            />
          </div>

          <div v-else class="flex-1 flex items-center justify-center p-8 text-gray-500">
            No transcript
          </div>
        </div>
      </div>
    </template>
  </USlideover>
</template>
