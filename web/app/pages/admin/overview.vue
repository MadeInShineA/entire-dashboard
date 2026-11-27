<script setup lang="ts">
import OverviewFilterBar from '~/features/overview/components/OverviewFilterBar.vue'
import CodeChangesChart from '~/features/overview/components/CodeChangesChart.vue'
import TokenUsageChart from '~/features/overview/components/TokenUsageChart.vue'
import ContributionChart from '~/features/overview/components/ContributionChart.vue'
import BreakdownChart from '~/features/overview/components/BreakdownChart.vue'
import ActivityHeatmap from '~/features/overview/components/ActivityHeatmap.vue'
import CheckpointsList from '~/features/overview/components/CheckpointsList.vue'
import CheckpointDetailSlideover from '~/features/checkpoint/components/CheckpointDetailSlideover.vue'
import KpiCard from '~/shared/components/KpiCard.vue'
import { useOverviewAnalytics } from '~/features/overview/composables/useOverviewAnalytics'
import { byDay, dayTokens, relativeChange, totals } from '~/features/overview/utils/aggregate'
import type { AnalyticsRow, FilterDim } from '~/features/overview/types/analytics.types'
import type { CheckpointDTO } from '~/features/checkpoint/types/checkpoint.types'
import { formatCompact, formatPercent } from '~/shared/utils/format'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const {
  preset,
  customFrom,
  customTo,
  setPreset,
  range,
  rangeError,
  dates,
  filters,
  toggleFilter,
  clearFilters,
  hasFilters,
  periodRows,
  current,
  previous,
  currentExcept,
  truncated,
  pending,
  loaded,
  error,
} = useOverviewAnalytics()

const { theme } = useChartTheme()

const cur = computed(() => totals(current.value))
const prev = computed(() => totals(previous.value))
const days = computed(() => byDay(current.value, dates.value))

const kpis = computed(() => {
  const c = cur.value
  const p = prev.value
  const d = days.value
  const hasPrev = previous.value.length > 0
  return [
    {
      title: 'Checkpoints',
      icon: 'i-lucide-git-commit',
      value: c.checkpoints.toLocaleString(),
      delta: hasPrev ? relativeChange(c.checkpoints, p.checkpoints) : null,
      series: d.map((x) => x.checkpoints),
      hint: `across ${c.repos} repositor${c.repos === 1 ? 'y' : 'ies'}`,
    },
    {
      title: 'Agent turns',
      icon: 'i-lucide-messages-square',
      value: c.turns.toLocaleString(),
      delta: hasPrev ? relativeChange(c.turns, p.turns) : null,
      series: d.map((x) => x.turns),
      hint: c.checkpoints ? `${(c.turns / c.checkpoints).toFixed(1)} prompts per checkpoint` : undefined,
    },
    {
      title: 'Lines changed',
      icon: 'i-lucide-file-diff',
      value: formatCompact(c.additions + c.deletions),
      delta: hasPrev ? relativeChange(c.additions + c.deletions, p.additions + p.deletions) : null,
      series: d.map((x) => x.additions + x.deletions),
      hint: `+${c.additions.toLocaleString()} / −${c.deletions.toLocaleString()}`,
    },
    {
      title: 'Tokens',
      icon: 'i-lucide-coins',
      value: formatCompact(c.tokens),
      delta: hasPrev ? relativeChange(c.tokens, p.tokens) : null,
      series: d.map(dayTokens),
      hint: c.cacheReadShare != null ? `${formatPercent(c.cacheReadShare)} served from cache` : undefined,
    },
    {
      title: 'Contributors',
      icon: 'i-lucide-users',
      value: c.authors.toLocaleString(),
      delta: hasPrev ? relativeChange(c.authors, p.authors) : null,
      series: d.map((x) => x.authors.size),
    },
  ]
})

function onFilter(dim: FilterDim, value: string | null) {
  if (value == null) filters[dim] = null
  else toggleFilter(dim, value)
}

// Checkpoint detail (from the timeline or the table)
const detailOpen = ref(false)
const detailCheckpoint = ref<CheckpointDTO | null>(null)
function openCheckpoint(row: AnalyticsRow) {
  detailCheckpoint.value = row as unknown as CheckpointDTO
  detailOpen.value = true
}
</script>

<template>
  <UDashboardPanel id="dashboard">
    <template #header>
      <UDashboardNavbar title="Overview">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="space-y-5">
        <OverviewFilterBar
          v-model:custom-from="customFrom"
          v-model:custom-to="customTo"
          :preset="preset"
          :filters="filters"
          :period-rows="periodRows"
          :has-filters="hasFilters"
          :range-error="rangeError"
          :pending="pending"
          @preset="setPreset"
          @filter="onFilter"
          @clear="clearFilters"
        />

        <UAlert v-if="error" color="error" variant="soft" icon="i-lucide-alert-circle" :title="error" />
        <UAlert
          v-if="truncated"
          color="warning"
          variant="soft"
          icon="i-lucide-triangle-alert"
          title="Too many checkpoints: only the most recent ones are included. Pick a shorter period."
        />

        <div v-if="!loaded && pending" class="flex items-center justify-center py-24">
          <UIcon name="i-lucide-loader-2" class="w-10 h-10 animate-spin text-primary" />
        </div>

        <!-- On refetch, keep the previous render (dimmed) instead of flashing a spinner -->
        <div
          v-else-if="loaded && range"
          class="space-y-5 transition-opacity"
          :class="pending ? 'opacity-60' : ''"
        >
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
            <KpiCard
              v-for="k in kpis"
              :key="k.title"
              :title="k.title"
              :icon="k.icon"
              :value="k.value"
              :delta="k.delta"
              :series="k.series"
              :hint="k.hint"
              :color="theme.accent"
            />
          </div>

          <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
            <CodeChangesChart :days="days" :totals="cur" />
            <TokenUsageChart :days="days" :totals="cur" />
          </div>

          <ContributionChart
            :rows="current"
            :legend-rows="currentExcept('agent')"
            :selected-agent="filters.agent"
            :start="range.start"
            :end="range.end"
            @open="openCheckpoint"
            @toggle-agent="(a) => toggleFilter('agent', a)"
          />

          <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
            <BreakdownChart :rows-for="currentExcept" :filters="filters" @select="toggleFilter" />
            <ActivityHeatmap :rows="current" />
          </div>

          <CheckpointsList :rows="current" @open="openCheckpoint" />
        </div>
      </div>

      <CheckpointDetailSlideover v-model:open="detailOpen" :checkpoint="detailCheckpoint" />
    </template>
  </UDashboardPanel>
</template>
