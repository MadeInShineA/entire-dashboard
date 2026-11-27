<script setup lang="ts">
import CheckpointDetailSlideover from '~/features/checkpoint/components/CheckpointDetailSlideover.vue'
import FileSessionList from '~/features/file-history/components/FileSessionList.vue'
import FileBlameView from '~/features/file-history/components/FileBlameView.vue'
import FileTreePanel from '~/features/file-history/components/FileTreePanel.vue'
import { fileHistoryApi } from '~/features/file-history/api/file-history.api'
import type { CheckpointDTO } from '~/features/checkpoint/types/checkpoint.types'
import type { RepoDTO } from '~/features/repository/types/repoDTO'
import type { FileHistory, FileLine, FileList, FileSession } from '~/features/file-history/types/file-history.types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()

const repos = ref<RepoDTO[]>([])
const repoId = ref<number | undefined>()
const branch = ref<string | undefined>()
const path = ref<string | null>(null)

const fileList = ref<FileList | null>(null)
const history = ref<FileHistory | null>(null)
const loadingFiles = ref(false)
const loadingHistory = ref(false)
const error = ref<string | null>(null)
const selectedKey = ref<number | null>(null)

const detailOpen = ref(false)
const detailCheckpoint = ref<CheckpointDTO | null>(null)
const detailSessionId = ref<number | null>(null)
const detailFocusFile = ref<string | null>(null)

const repoItems = computed(() => repos.value.map((r) => ({ label: r.name, value: r.id })))
const branchItems = computed(() => (fileList.value?.branches ?? []).map((b) => ({ label: b, value: b })))

// ---- keys, labels, colors --------------------------------------------------
// A row is a session; a checkpoint without session metadata is keyed by -checkpoint.id
const keyOf = (row: FileSession) => row.session?.id ?? -row.checkpoint.id
const lineKey = (line: FileLine) =>
  line.sessionId ?? (line.checkpointId != null ? -line.checkpointId : null)

/** Oldest first, so labels stay stable as new sessions are added */
const chronological = computed(() =>
  [...(history.value?.sessions ?? [])].sort(
    (a, b) => a.checkpoint.commitTime - b.checkpoint.commitTime
      || (a.session?.sessionIndex ?? 0) - (b.session?.sessionIndex ?? 0)
  )
)
const labelByKey = computed(() => new Map(chronological.value.map((r, i) => [keyOf(r), `S${i + 1}`])))
/** Color slots only for sessions that still own lines, in fixed chronological order */
const slotByKey = computed(() => {
  const m = new Map<number, number>()
  chronological.value.filter((r) => r.currentLines > 0).forEach((r, i) => m.set(keyOf(r), i))
  return m
})
const rowByKey = computed(() => new Map((history.value?.sessions ?? []).map((r) => [keyOf(r), r])))

const aiShare = computed(() => {
  const h = history.value
  if (!h || !h.totalLines) return null
  return Math.round((h.checkpointLines / h.totalLines) * 100)
})

// ---- URL <-> state ---------------------------------------------------------
function readQuery() {
  const q = route.query
  const qRepo = q.repoId ? Number(q.repoId) : undefined
  if (qRepo != null && !Number.isNaN(qRepo)) repoId.value = qRepo
  branch.value = (q.branch as string) || branch.value
  path.value = (q.path as string) || null
}

function writeQuery() {
  const query = {
    ...(repoId.value != null ? { repoId: String(repoId.value) } : {}),
    ...(branch.value ? { branch: branch.value } : {}),
    ...(path.value ? { path: path.value } : {}),
  }
  if (JSON.stringify(query) !== JSON.stringify(route.query)) router.replace({ query })
}

// ---- loading ---------------------------------------------------------------
async function loadFiles() {
  if (repoId.value == null) return
  loadingFiles.value = true
  error.value = null
  try {
    fileList.value = await fileHistoryApi.listFiles(repoId.value, branch.value)
    branch.value = fileList.value.branch ?? undefined
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'Failed to load files'
    fileList.value = null
  } finally {
    loadingFiles.value = false
  }
}

let historySeq = 0
async function loadHistory() {
  selectedKey.value = null
  const seq = ++historySeq
  if (repoId.value == null || !path.value) {
    history.value = null
    return
  }
  loadingHistory.value = true
  error.value = null
  try {
    const h = await fileHistoryApi.getHistory(repoId.value, path.value, branch.value)
    if (seq === historySeq) history.value = h
  } catch (e: any) {
    if (seq === historySeq) {
      error.value = e?.data?.message || e?.message || 'Failed to load file history'
      history.value = null
    }
  } finally {
    if (seq === historySeq) loadingHistory.value = false
  }
}

function onRepoChange(id: number) {
  if (id === repoId.value) return
  repoId.value = id
  branch.value = undefined
  path.value = null
  history.value = null
  writeQuery()
  loadFiles()
}

async function onBranchChange(b: string) {
  if (b === branch.value) return
  branch.value = b
  writeQuery()
  await loadFiles()
  await loadHistory()
}

function onSelectFile(p: string) {
  if (p === path.value) return
  path.value = p
  writeQuery()
  loadHistory()
}

/** Open a session in the checkpoint panel: on this file's diff, or on the conversation */
function openDetail(row: FileSession, view: 'diff' | 'conversation' = 'diff') {
  detailCheckpoint.value = row.checkpoint
  detailSessionId.value = row.session?.id ?? null
  detailFocusFile.value = view === 'diff' ? path.value : null
  detailOpen.value = true
}

// Links from elsewhere (e.g. the checkpoint panel) change the query while this page is open
watch(() => route.query, async () => {
  const prevRepo = repoId.value
  const prevBranch = branch.value
  const prevPath = path.value
  readQuery()
  if (repoId.value !== prevRepo || branch.value !== prevBranch) {
    await loadFiles()
    await loadHistory()
  } else if (path.value !== prevPath) {
    await loadHistory()
  }
})

onMounted(async () => {
  readQuery()
  repos.value = await fileHistoryApi.listRepos()
  if (repoId.value == null && repos.value.length) repoId.value = repos.value[0]!.id
  writeQuery()
  await loadFiles()
  await loadHistory()
})
</script>

<template>
  <UDashboardPanel id="files">
    <template #header>
      <UDashboardNavbar title="Files">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="flex flex-col gap-4 mt-2 min-h-0 h-full">
        <!-- Filters: one row -->
        <div class="flex flex-wrap items-center gap-3">
          <USelect
            :model-value="repoId"
            :items="repoItems"
            placeholder="Repository"
            class="w-52"
            :loading="!repos.length"
            @update:model-value="onRepoChange($event as number)"
          />
          <USelect
            :model-value="branch"
            :items="branchItems"
            placeholder="Branch"
            class="w-40"
            :disabled="!branchItems.length"
            @update:model-value="onBranchChange($event as string)"
          />
        </div>

        <UAlert v-if="error" color="error" variant="subtle" icon="i-lucide-circle-alert" :title="error" />

        <div class="grid gap-5 lg:grid-cols-[17rem_minmax(0,1fr)] min-h-0 flex-1">
          <FileTreePanel
            :files="fileList?.files ?? []"
            :selected-path="path"
            :loading="loadingFiles"
            class="lg:sticky lg:top-0 max-h-[70vh] lg:max-h-[calc(100vh-11rem)]"
            @select="onSelectFile"
          />

          <div class="min-w-0 space-y-4">
            <div v-if="!path" class="text-sm text-muted py-16 text-center">
              Pick a file in the tree to see which AI sessions touched it and where each line comes from.
              <div class="mt-1 text-xs">Files marked with <UIcon name="i-lucide-sparkles" class="w-3 h-3 align-middle" /> were touched by AI sessions.</div>
            </div>

            <div v-else-if="loadingHistory && !history" class="flex justify-center py-12">
              <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
            </div>

            <template v-else-if="history">
              <!-- Headline -->
              <div class="flex flex-wrap items-baseline gap-x-6 gap-y-1">
                <h2 class="font-mono text-base font-semibold text-highlighted break-all">{{ history.path }}</h2>
                <p v-if="aiShare != null" class="text-sm text-muted">
                  <span class="text-highlighted font-semibold">{{ history.checkpointLines }}</span>
                  of {{ history.totalLines }} lines ({{ aiShare }}%) were last changed in a commit linked to an AI checkpoint
                </p>
                <UIcon v-if="loadingHistory" name="i-lucide-loader-2" class="w-4 h-4 animate-spin text-primary" />
              </div>

              <section class="space-y-2">
                <h3 class="text-sm font-semibold text-highlighted">
                  Sessions that touched this file
                  <span class="text-muted font-normal">({{ history.sessions.length }})</span>
                </h3>
                <FileSessionList
                  :rows="history.sessions"
                  :key-of="keyOf"
                  :slot-by-key="slotByKey"
                  :label-by-key="labelByKey"
                  :selected-key="selectedKey"
                  @select="selectedKey = $event"
                  @open="openDetail"
                />
                <p class="text-xs text-muted">
                  Click a session, or any line it wrote below, to highlight its lines; the panel button opens its transcript and diff for this file.
                  Attribution is per commit: a line belongs to a session when the commit that last changed it carries
                  that session's checkpoint, even if it was edited by hand before committing.
                </p>
              </section>

              <section class="space-y-2">
                <h3 class="text-sm font-semibold text-highlighted">Line by line</h3>
                <div v-if="!history.existsAtHead" class="text-sm text-muted py-6">
                  This file no longer exists on <span class="font-mono">{{ history.branch }}</span>.
                </div>
                <div v-else-if="history.binary" class="text-sm text-muted py-6">Binary file: no line view.</div>
                <template v-else>
                  <FileBlameView
                    :lines="history.lines"
                    :line-key="lineKey"
                    :slot-by-key="slotByKey"
                    :label-by-key="labelByKey"
                    :row-by-key="rowByKey"
                    :selected-key="selectedKey"
                    @select="selectedKey = $event"
                    @open="openDetail($event, 'conversation')"
                  />
                  <p v-if="history.truncated" class="text-xs text-muted">
                    Showing the first {{ history.lines.length }} lines of a longer file.
                  </p>
                </template>
              </section>
            </template>
          </div>
        </div>
      </div>

      <CheckpointDetailSlideover
        v-model:open="detailOpen"
        :checkpoint="detailCheckpoint"
        :focus-session-id="detailSessionId"
        :focus-file="detailFocusFile"
      />
    </template>
  </UDashboardPanel>
</template>
