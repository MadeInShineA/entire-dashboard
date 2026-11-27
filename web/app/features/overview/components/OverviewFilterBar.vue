<script setup lang="ts">
/**
 * One filter row scoping every chart on the page: the period, and repository / author / agent.
 * Charts set the same filters when you click a bar or a legend entry.
 */
import { dimValue } from '../utils/aggregate'
import type { AnalyticsRow, FilterDim, OverviewFilters, RangePreset } from '../types/analytics.types'
import { formatDate } from '~/shared/utils/date'

const props = defineProps<{
  preset: RangePreset
  filters: OverviewFilters
  /** Current period, unfiltered: the filter options */
  periodRows: AnalyticsRow[]
  hasFilters: boolean
  rangeError: string | null
  pending: boolean
}>()

const customFrom = defineModel<string>('customFrom', { required: true })
const customTo = defineModel<string>('customTo', { required: true })

const emit = defineEmits<{
  preset: [p: RangePreset]
  filter: [dim: FilterDim, value: string | null]
  clear: []
}>()

const PRESETS: { label: string; value: RangePreset }[] = [
  { label: '7 days', value: '7d' },
  { label: '14 days', value: '14d' },
  { label: '30 days', value: '30d' },
  { label: '90 days', value: '90d' },
  { label: 'Custom', value: 'custom' },
]

const DIMS: { dim: FilterDim; label: string; icon: string }[] = [
  { dim: 'repo', label: 'All repositories', icon: 'i-lucide-folder-git' },
  { dim: 'author', label: 'All authors', icon: 'i-lucide-user' },
  { dim: 'agent', label: 'All agents', icon: 'i-lucide-bot' },
  { dim: 'model', label: 'All models', icon: 'i-lucide-cpu' },
]

function options(dim: FilterDim): string[] {
  const counts = new Map<string, number>()
  for (const r of props.periodRows) {
    const v = dimValue(r, dim)
    counts.set(v, (counts.get(v) ?? 0) + 1)
  }
  const list = [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([v]) => v)
  // Keep a filter from the URL selectable even when the period has no rows for it
  const current = props.filters[dim]
  if (current && !counts.has(current)) list.unshift(current)
  return list
}

const today = formatDate(new Date())
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <div class="inline-flex rounded-md ring ring-default overflow-hidden" role="group" aria-label="Period">
      <button
        v-for="p in PRESETS"
        :key="p.value"
        type="button"
        class="px-3 py-1.5 text-sm transition-colors border-r border-default last:border-r-0"
        :class="preset === p.value ? 'bg-inverted text-inverted' : 'text-default hover:bg-elevated'"
        :aria-pressed="preset === p.value"
        @click="emit('preset', p.value)"
      >
        {{ p.label }}
      </button>
    </div>

    <template v-if="preset === 'custom'">
      <UInput v-model="customFrom" type="date" size="sm" :max="customTo || today" aria-label="Start date" />
      <span class="text-muted">–</span>
      <UInput v-model="customTo" type="date" size="sm" :min="customFrom" :max="today" aria-label="End date" />
    </template>

    <USeparator orientation="vertical" class="h-6 mx-1 hidden sm:block" />

    <USelectMenu
      v-for="d in DIMS"
      :key="d.dim"
      :model-value="filters[d.dim] ?? undefined"
      :items="options(d.dim)"
      :placeholder="d.label"
      :icon="d.icon"
      size="sm"
      class="w-40"
      :search-input="{ placeholder: 'Search…' }"
      :aria-label="d.label"
      @update:model-value="emit('filter', d.dim, ($event as string) ?? null)"
    >
      <template v-if="filters[d.dim]" #trailing>
        <UButton
          icon="i-lucide-x"
          size="xs"
          color="neutral"
          variant="link"
          :aria-label="`Clear ${d.dim} filter`"
          @click.stop="emit('filter', d.dim, null)"
        />
      </template>
    </USelectMenu>

    <UButton
      v-if="hasFilters"
      size="sm"
      color="neutral"
      variant="ghost"
      icon="i-lucide-filter-x"
      @click="emit('clear')"
    >
      Clear filters
    </UButton>

    <UIcon v-if="pending" name="i-lucide-loader-2" class="w-4 h-4 animate-spin text-muted ml-1" />
    <span v-if="rangeError" class="text-sm text-error">{{ rangeError }}</span>
  </div>
</template>
