<script setup lang="ts">
/**
 * Ranked horizontal bars by repository, author, agent or model. Clicking a bar filters the whole page
 * by that value (click again to clear). A dimension's bars ignore that dimension's own filter,
 * so the unselected siblings stay visible (dimmed) and you can switch between them.
 */
import type { EChartsOption } from 'echarts'
import ChartCard from '~/shared/components/ChartCard.vue'
import { VChart } from '~/shared/utils/echarts'
import { agentColor } from '~/shared/utils/chart-palette'
import { formatCompact } from '~/shared/utils/format'
import { groupBy, type GroupStat } from '../utils/aggregate'
import type { AnalyticsRow, FilterDim, OverviewFilters } from '../types/analytics.types'

const props = defineProps<{
  rowsFor: (dim: FilterDim) => AnalyticsRow[]
  filters: OverviewFilters
}>()

const emit = defineEmits<{
  select: [dim: FilterDim, value: string]
}>()

type Metric = 'checkpoints' | 'lines' | 'tokens' | 'turns'
const TOP_N = 8
const OTHER_KEY = '__other__'

const dim = ref<FilterDim>('repo')
const metric = ref<Metric>('checkpoints')

const dimItems = [
  { label: 'Repository', value: 'repo' },
  { label: 'Author', value: 'author' },
  { label: 'Agent', value: 'agent' },
  { label: 'Model', value: 'model' },
]
const metricItems = [
  { label: 'Checkpoints', value: 'checkpoints' },
  { label: 'Lines changed', value: 'lines' },
  { label: 'Tokens', value: 'tokens' },
  { label: 'Turns', value: 'turns' },
]

const { theme, tooltip, xAxisBase, yAxisBase } = useChartTheme()

function metricValue(g: GroupStat): number {
  switch (metric.value) {
    case 'lines': return g.additions + g.deletions
    case 'tokens': return g.tokens
    case 'turns': return g.turns
    default: return g.checkpoints
  }
}

const allGroups = computed(() => groupBy(props.rowsFor(dim.value), dim.value).sort((a, b) => metricValue(b) - metricValue(a)))

/** Top N by the metric, the tail folded into one "Other" bar */
const groups = computed<GroupStat[]>(() => {
  const all = allGroups.value
  if (all.length <= TOP_N + 1) return all
  const other: GroupStat = { key: OTHER_KEY, checkpoints: 0, additions: 0, deletions: 0, tokens: 0, turns: 0 }
  for (const g of all.slice(TOP_N)) {
    other.checkpoints += g.checkpoints
    other.additions += g.additions
    other.deletions += g.deletions
    other.tokens += g.tokens
    other.turns += g.turns
  }
  return [...all.slice(0, TOP_N), other]
})
const label = (key: string) => (key === OTHER_KEY ? `Other (${allGroups.value.length - TOP_N})` : key)

const empty = computed(() => !groups.value.some((g) => metricValue(g) > 0))
const height = computed(() => `${Math.max(160, groups.value.length * 34 + 40)}px`)

const option = computed<EChartsOption>(() => {
  const t = theme.value
  const selected = props.filters[dim.value]
  const opacity = (key: string) => (selected != null && key !== selected ? 0.35 : 1)
  const base = { type: 'bar' as const, barMaxWidth: 18, cursor: 'pointer' }
  const endLabel = {
    show: true,
    position: 'right' as const,
    color: t.subText,
    fontSize: 11,
    formatter: (p: any) => formatCompact(metricValue(groups.value[p.dataIndex]!)),
  }

  let series: any[]
  if (metric.value === 'lines') {
    series = [
      {
        ...base, name: 'Added', stack: 'l',
        data: groups.value.map((g) => ({ value: g.additions, itemStyle: { opacity: opacity(g.key) } })),
        itemStyle: { color: t.additions, borderColor: t.surface, borderWidth: 1 },
      },
      {
        ...base, name: 'Deleted', stack: 'l', label: endLabel,
        data: groups.value.map((g) => ({ value: g.deletions, itemStyle: { opacity: opacity(g.key) } })),
        itemStyle: { color: t.deletions, borderColor: t.surface, borderWidth: 1, borderRadius: [0, 4, 4, 0] },
      },
    ]
  } else {
    // Color follows the entity: agents keep their own color, other dimensions use one series color
    series = [{
      ...base, name: metricItems.find((m) => m.value === metric.value)!.label, label: endLabel,
      data: groups.value.map((g) => ({
        value: metricValue(g),
        itemStyle: {
          opacity: opacity(g.key),
          color: g.key === OTHER_KEY ? t.neutral : dim.value === 'agent' ? agentColor(g.key, t.mode) : t.accent,
        },
      })),
      itemStyle: { borderRadius: [0, 4, 4, 0] },
    }]
  }

  return {
    grid: { left: 8, right: 56, top: 4, bottom: 4, containLabel: true },
    tooltip: {
      ...tooltip.value,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: t.splitLine, opacity: 0.6 } },
      formatter: (params: any) => {
        const g = groups.value[params[0].dataIndex]!
        const hint = g.key === OTHER_KEY ? '' : `<br/><span style="opacity:.7">Click to ${props.filters[dim.value] === g.key ? 'clear the filter' : 'filter by it'}</span>`
        return `<b>${label(g.key)}</b><br/>`
          + `Checkpoints: <b>${g.checkpoints.toLocaleString()}</b> · turns: <b>${g.turns.toLocaleString()}</b><br/>`
          + `Lines: <b>+${g.additions.toLocaleString()}</b> / <b>-${g.deletions.toLocaleString()}</b><br/>`
          + `Tokens: <b>${g.tokens.toLocaleString()}</b>${hint}`
      },
    },
    xAxis: {
      ...yAxisBase.value,
      type: 'value',
      axisLabel: { ...yAxisBase.value.axisLabel, formatter: (v: number) => formatCompact(v) },
    },
    yAxis: {
      ...xAxisBase.value,
      type: 'category',
      inverse: true,
      data: groups.value.map((g) => label(g.key)),
      axisLabel: { ...xAxisBase.value.axisLabel, color: t.text, width: 140, overflow: 'truncate' },
    },
    series,
  }
})

function onClick(p: any) {
  const g = groups.value[p.dataIndex]
  if (g && g.key !== OTHER_KEY) emit('select', dim.value, g.key)
}

const table = computed(() => ({
  columns: [dimItems.find((d) => d.value === dim.value)!.label, 'Checkpoints', 'Turns', 'Lines added', 'Lines deleted', 'Tokens'],
  rows: allGroups.value.map((g) => [g.key, g.checkpoints, g.turns, g.additions, g.deletions, g.tokens]),
}))
</script>

<template>
  <ChartCard
    title="Breakdown"
    subtitle="Click a bar to filter the dashboard"
    :summary="`Ranked bars of ${metric} by ${dim}.`"
    :table="table"
    :empty="empty"
  >
    <template #actions>
      <USelect v-model="dim" :items="dimItems" size="xs" class="w-32" aria-label="Group by" />
      <USelect v-model="metric" :items="metricItems" size="xs" class="w-36" aria-label="Metric" />
    </template>
    <div :style="{ height }">
      <VChart :option="option" autoresize @click="onClick" />
    </div>
    <template v-if="metric === 'lines'" #footer>
      <div class="flex flex-wrap gap-x-6 text-sm">
        <span class="inline-flex items-center gap-2"><span class="size-3 rounded-sm" :style="{ background: theme.additions }" />Added</span>
        <span class="inline-flex items-center gap-2"><span class="size-3 rounded-sm" :style="{ background: theme.deletions }" />Deleted</span>
      </div>
    </template>
  </ChartCard>
</template>
