<script setup lang="ts">
import CheckpointDetailSlideover from '~/features/checkpoint/components/CheckpointDetailSlideover.vue'
import FileSessionList from '~/features/file-history/components/FileSessionList.vue'
import FileBlameView from '~/features/file-history/components/FileBlameView.vue'
import { fileHistoryApi } from '~/features/file-history/api/file-history.api'
import type { CheckpointDTO } from '~/features/checkpoint/types/checkpoint.types'
import type { RepoDTO } from '~/features/repository/types/repoDTO'
import type { FileHistory, FileLine, FileList, FileSession } from '~/features/file-history/types/file-history.types'

definePageMeta({
  layout: 'admin',
})

const route = useRoute()
const router = useRouter()

const repos = ref<RepoDTO[]>([])
const repoId = ref<number | undefined>(route.query.repoId ? Number(route.query.repoId) : undefined)
const branch = ref<string | undefined>((route.query.branch as string) || undefined)
const path = ref<string | undefined>((route.query.path as string) || undefined)
const aiOnly = ref(route.query.aiOnly !== 'false')

const fileList = ref<FileList | null>(null)
const history = ref<FileHistory | null>(null)
const loadingFiles = ref(false)
const loadingHistory = ref(false)
const error = ref<string | null>(null)
const selectedKey = ref<number | null>(null)

const detailOpen = ref(false)
const detailCheckpoint = ref<CheckpointDTO | null>(null)

// ---- options -------------------------------------------------------------
const repoItems = computed(() => repos.value.map((r) => ({ label: r.name, value: r.id })))
const branchItems = computed(() => (fileList.value?.branches ?? []).map((b) => ({ label: b, value: b })))
const fileItems = computed(() =>
  (fileList.value?.files ?? [])
    .filter((f) => !aiOnly.value || f.sessionCount > 0)
    .map((f) => ({
      label: f.path,
      value: f.path,
      suffix: [
        f.sessionCount ? `${f.sessionCount} session${f.sessionCount === 1 ? '' : 's'}` : '',
        f.existsAtHead ? '' : 'deleted',
      ].filter(Boolean).join(' · '),
    }))
)

// ---- keys, labels, colors --------------------------------------------------
// A row is a session; a checkpoint without session metadata is keyed by -checkpoint.id
const keyOf = (row: FileSession) => row.session?.id ?? -row.checkpoint.id
const lineKey = (line: FileLine) =>
  line.sessionId ?? (line.checkpointId != null ? -line.checkpointId : null)

/** Oldest first, so labels are stable as new sessions are added */
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

// ---- loading ---------------------------------------------------------------
async function loadRepos() {
  repos.value = await fileHistoryApi.listRepos()
  if (repoId.value == null && repos.value.length) repoId.value = repos.value[0]!.id
}

async function loadFiles() {
  if (repoId.value == null) return
  loadingFiles.value = true
  error.value = null
  try {
    fileList.value = await fileHistoryApi.listFiles(repoId.value, branch.value)
    branch.value = fileList.value.branch ?? undefined
    if (path.value && !fileList.value.files.some((f) => f.path === path.value)) path.value = undefined
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'Failed to load files'
    fileList.value = null
  } finally {
    loadingFiles.value = false
  }
}

async function loadHistory() {
  selectedKey.value = null
  if (repoId.value == null || !path.value) {
    history.value = null
    return
  }
  loadingHistory.value = true
  error.value = null
  try {
    history.value = await fileHistoryApi.getHistory(repoId.value, path.value, branch.value)
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'Failed to load file history'
    history.value = null
  } finally {
    loadingHistory.value = false
  }
}

function openDetail(checkpoint: CheckpointDTO) {
  detailCheckpoint.value = checkpoint
  detailOpen.value = true
}

watch(repoId, async (id, old) => {
  if (old !== undefined && id !== old) {
    branch.value = undefined
    path.value = undefined
  }
  await loadFiles()
})
watch(branch, async (b, old) => {
  if (old !== undefined && b !== old && fileList.value?.branch !== b) await loadFiles()
})
watch([repoId, branch, path], loadHistory)
watch([repoId, branch, path, aiOnly], () => {
  router.replace({
    query: {
      ...(repoId.value != null ? { repoId: String(repoId.value) } : {}),
      ...(branch.value ? { branch: branch.value } : {}),
      ...(path.value ? { path: path.value } : {}),
      ...(aiOnly.value ? {} : { aiOnly: 'false' }),
    },
  })
})

onMounted(async () => {
  await loadRepos()
  // repoId from the URL doesn't trigger the watcher; one picked by loadRepos() does
  if (repoId.value != null && !fileList.value && !loadingFiles.value) await loadFiles()
  if (path.value && !history.value && !loadingHistory.value) await loadHistory()
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
      <div class="space-y-5 mt-2">
        <!-- Filters: one row -->
        <div class="flex flex-wrap items-center gap-3">
          <USelect
            v-model="repoId"
            :items="repoItems"
            placeholder="Repository"
            class="w-52"
            :loading="!repos.length"
          />
          <USelect
            v-model="branch"
            :items="branchItems"
            placeholder="Branch"
            class="w-40"
            :disabled="!branchItems.length"
          />
          <USelectMenu
            v-model="path"
            :items="fileItems"
            value-key="value"
            placeholder="Choose a file…"
            :search-input="{ placeholder: 'Search files…' }"
            virtualize
            class="w-full sm:w-[28rem]"
            :loading="loadingFiles"
          >
            <template #item-trailing="{ item }">
              <span class="text-xs text-muted whitespace-nowrap">{{ item.suffix }}</span>
            </template>
          </USelectMenu>
          <USwitch v-model="aiOnly" label="Only files touched by AI" />
        </div>

        <UAlert v-if="error" color="error" variant="subtle" icon="i-lucide-circle-alert" :title="error" />

        <div v-if="!path" class="text-sm text-muted py-16 text-center">
          Choose a file to see which AI sessions touched it and where each line comes from.
        </div>

        <div v-else-if="loadingHistory" class="flex justify-center py-12">
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
          </div>

          <div class="grid gap-5 xl:grid-cols-[minmax(20rem,26rem)_1fr]">
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
                Click a session to highlight its lines. Attribution is per commit: a line belongs to a session when
                the commit that last changed it carries that session's checkpoint, even if you edited it by hand
                before committing.
              </p>
            </section>

            <section class="space-y-2 min-w-0">
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
                />
                <p v-if="history.truncated" class="text-xs text-muted">
                  Showing the first {{ history.lines.length }} of a longer file.
                </p>
              </template>
            </section>
          </div>
        </template>
      </div>

      <CheckpointDetailSlideover v-model:open="detailOpen" :checkpoint="detailCheckpoint" />
    </template>
  </UDashboardPanel>
</template>
