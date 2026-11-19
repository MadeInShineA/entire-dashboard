<script setup lang="ts">
import { flattenTree, collectFolderPaths } from '~/features/checkpoint/utils/file-tree'
import type { FileEntry } from '../types/file-history.types'
import { ancestorFolders, buildRepoTree, foldersContaining } from '../utils/repo-tree'

const props = defineProps<{
  files: FileEntry[]
  selectedPath: string | null
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [path: string]
}>()

const query = ref('')
const aiOnly = ref(false)

const entryByPath = computed(() => new Map(props.files.map((f) => [f.path, f])))
const visibleFiles = computed(() => {
  const q = query.value.trim().toLowerCase()
  return props.files.filter((f) =>
    (!aiOnly.value || f.sessionCount > 0) && (!q || f.path.toLowerCase().includes(q))
  )
})
const tree = computed(() => buildRepoTree(visibleFiles.value))
const aiFolders = computed(() =>
  foldersContaining(tree.value, visibleFiles.value.filter((f) => f.sessionCount > 0).map((f) => f.path))
)
const aiFileCount = computed(() => props.files.filter((f) => f.sessionCount > 0).length)

// Expanded by default: folders leading to AI-touched files and to the selected file.
// While filtering, everything is expanded.
const expanded = ref<Set<string>>(new Set())
function resetExpansion() {
  const next = new Set(aiFolders.value)
  if (props.selectedPath) ancestorFolders(tree.value, props.selectedPath).forEach((p) => next.add(p))
  expanded.value = next
}
watch(() => props.files, resetExpansion, { immediate: true })
watch(() => props.selectedPath, (p) => {
  if (!p) return
  const next = new Set(expanded.value)
  ancestorFolders(tree.value, p).forEach((f) => next.add(f))
  expanded.value = next
})
const effectiveExpanded = computed(() =>
  query.value.trim() || aiOnly.value ? collectFolderPaths(tree.value) : expanded.value
)
const rows = computed(() => flattenTree(tree.value, effectiveExpanded.value))

function toggle(path: string) {
  const next = new Set(expanded.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  expanded.value = next
}
</script>

<template>
  <div class="flex flex-col min-h-0 rounded-md border border-default">
    <div class="p-2 space-y-2 border-b border-default">
      <UInput v-model="query" icon="i-lucide-search" placeholder="Filter files…" size="sm" class="w-full" />
      <div class="flex items-center justify-between gap-2">
        <USwitch v-model="aiOnly" size="sm" label="Only AI-touched" />
        <span class="text-xs text-muted tabular-nums">{{ aiFileCount }} / {{ files.length }}</span>
      </div>
    </div>

    <div class="flex-1 min-h-0 overflow-auto p-1">
      <div v-if="loading" class="flex justify-center py-6">
        <UIcon name="i-lucide-loader-2" class="w-5 h-5 animate-spin text-primary" />
      </div>
      <div v-else-if="!rows.length" class="text-sm text-muted p-3">No files</div>
      <template v-else>
        <button
          v-for="{ node, depth } in rows"
          :key="(node.isFile ? 'f:' : 'd:') + node.fullPath"
          type="button"
          class="flex items-center gap-1.5 w-full min-w-0 py-1 pr-2 rounded-md text-sm text-left transition-colors"
          :class="node.isFile && selectedPath === node.fullPath
            ? 'bg-primary/10 text-primary'
            : 'hover:bg-elevated text-default'"
          :style="{ paddingLeft: `${8 + depth * 14}px` }"
          :title="node.fullPath"
          @click="node.isFile ? emit('select', node.fullPath) : toggle(node.fullPath)"
        >
          <UIcon
            v-if="!node.isFile"
            :name="effectiveExpanded.has(node.fullPath) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
            class="w-3.5 h-3.5 shrink-0 text-muted"
          />
          <span v-else class="w-3.5 shrink-0" />
          <UIcon
            :name="node.isFile ? 'i-lucide-file' : (effectiveExpanded.has(node.fullPath) ? 'i-lucide-folder-open' : 'i-lucide-folder')"
            class="w-4 h-4 shrink-0 text-muted"
          />
          <span
            class="flex-1 truncate"
            :class="node.isFile && entryByPath.get(node.fullPath)?.existsAtHead === false ? 'line-through text-muted' : ''"
          >{{ node.name }}</span>
          <template v-if="node.isFile">
            <span
              v-if="(entryByPath.get(node.fullPath)?.sessionCount ?? 0) > 0"
              class="shrink-0 inline-flex items-center gap-1 rounded px-1.5 text-xs text-highlighted bg-primary/15 tabular-nums"
              :title="`${entryByPath.get(node.fullPath)!.sessionCount} AI session(s) touched this file`"
            >
              <UIcon name="i-lucide-sparkles" class="w-3 h-3" />{{ entryByPath.get(node.fullPath)!.sessionCount }}
            </span>
          </template>
          <span
            v-else-if="aiFolders.has(node.fullPath)"
            class="shrink-0 size-1.5 rounded-full bg-primary"
            title="Contains AI-touched files"
          />
        </button>
      </template>
    </div>
  </div>
</template>
