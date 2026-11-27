<script setup lang="ts">
/**
 * Timeline of individual checkpoints: x = commit date, y = hour of day, size = lines changed,
 * color + shape = agent. Click a point to open the checkpoint. The agent legend doubles as
 * the agent filter for the whole page.
 */
import type { EChartsOption } from 'echarts'
import ChartCard from '~/shared/components/ChartCard.vue'
import { VChart } from '~/shared/utils/echarts'
import { agentColor, agentSymbol, agentSymbolSvg } from '~/shared/utils/chart-palette'
import { formatDateTime } from '~/shared/utils/date'
import { dimValue } from '../utils/aggregate'
import type { AnalyticsRow } from '../types/analytics.types'

const props = defineProps<{
  rows: AnalyticsRow[]
  /** Rows used for the legend counts: every filter except the agent one */
  legendRows: AnalyticsRow[]
  selectedAgent: string | null
  start: number
  end: number
}>()

const emit = defineEmits<{
  open: [row: AnalyticsRow]
  toggleAgent: [agent: string]
}>()

const { theme, tooltip, xAxisBase, yAxisBase } = useChartTheme()

const MIN_SIZE = 8
const MAX_SIZE = 36
const changed = (r: AnalyticsRow) => (r.additions ?? 0) + (r.deletions ?? 0)
const maxChanged = computed(() => Math.max(1, ...props.rows.map(changed)))
/** Area-proportional size: diameter grows with the square root of lines changed */
function sizeOf(lines: number): number {
  return MIN_SIZE + (MAX_SIZE - MIN_SIZE) * Math.sqrt(lines / maxChanged.value)
}
const sizeLegend = computed(() => {
  const m = maxChanged.value
  const steps = [Math.max(1, Math.round(m / 25)), Math.round(m / 4), m]
  return [...new Set(steps)].filter((v) => v > 0).map((v) => ({ v, d: sizeOf(v) }))
})

function hourOf(ts: number): number {
  const d = new Date(ts)
  return d.getHours() + d.getMinutes() / 60
}

/** One series per agent; data keeps the row index so tooltip and click resolve the exact checkpoint */
const option = computed<EChartsOption>(() => {
  const t = theme.value
  const byAgent = new Map<string, number[]>()
  props.rows.forEach((r, i) => {
    const a = dimValue(r, 'agent')
    if (!byAgent.has(a)) byAgent.set(a, [])
    byAgent.get(a)!.push(i)
  })
  const series = [...byAgent.entries()].map(([agent, idxs]) => ({
    type: 'scatter' as const,
    name: agent,
    symbol: agentSymbol(agent),
    cursor: 'pointer',
    data: idxs.map((i) => {
      const r = props.rows[i]!
      return { value: [r.commitTime, hourOf(r.commitTime), sizeOf(changed(r))], rowIndex: i }
    }),
    symbolSize: (v: number[]) => v[2]!,
    itemStyle: { color: agentColor(agent, t.mode), opacity: 0.75, borderColor: t.surface, borderWidth: 1 },
    emphasis: { scale: 1.15, itemStyle: { opacity: 1, borderColor: t.text } },
  }))

  return {
    grid: { left: 8, right: 16, top: 12, bottom: 4, containLabel: true },
    tooltip: {
      ...tooltip.value,
      trigger: 'item',
      formatter: (p: any) => {
        const r = props.rows[p.data.rowIndex]
        if (!r) return ''
        const model = r.model ? ` · ${r.model}` : ''
        const turns = r.turnCount ? ` · ${r.turnCount} turn${r.turnCount === 1 ? '' : 's'}` : ''
        const msg = (r.commitMessage ?? '').replace(/</g, '&lt;')
        return `<b>${msg.length > 70 ? msg.slice(0, 70) + '…' : msg || r.checkpointId}</b><br/>`
          + `${formatDateTime(r.commitTime)} · ${r.repoName}<br/>`
          + `${dimValue(r, 'agent')} · ${r.commitAuthorName ?? 'Unknown author'}<br/>`
          + `+${r.additions ?? 0} / -${r.deletions ?? 0}${turns}${model}<br/>`
          + '<span style="opacity:.7">Click to open</span>'
      },
    },
    xAxis: {
      ...xAxisBase.value,
      type: 'time',
      min: props.start,
      max: props.end,
      axisLabel: { ...xAxisBase.value.axisLabel, formatter: '{MMM} {d}' },
    },
    yAxis: {
      ...yAxisBase.value,
      type: 'value',
      min: 0,
      max: 24,
      interval: 3,
      inverse: true,
      axisLabel: { ...yAxisBase.value.axisLabel, formatter: (v: number) => `${String(v).padStart(2, '0')}:00` },
    },
    series,
  }
})

function onClick(p: any) {
  const r = props.rows[p?.data?.rowIndex]
  if (r) emit('open', r)
}

/** Legend from rows without the agent filter, so other agents stay visible to switch to */
const legend = computed(() => {
  const counts = new Map<string, number>()
  for (const r of props.legendRows) {
    const a = dimValue(r, 'agent')
    counts.set(a, (counts.get(a) ?? 0) + 1)
  }
  const total = props.legendRows.length || 1
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([agent, count]) => ({ agent, count, pct: Math.round((100 * count) / total) }))
})

const table = computed(() => ({
  columns: ['Commit time', 'Agent', 'Repository', 'Author', 'Added', 'Deleted'],
  rows: [...props.rows]
    .sort((a, b) => b.commitTime - a.commitTime)
    .map((r) => [formatDateTime(r.commitTime), dimValue(r, 'agent'), r.repoName, r.commitAuthorName ?? '-', r.additions ?? 0, r.deletions ?? 0]),
}))
</script>

<template>
  <ChartCard
    title="Checkpoint timeline"
    :subtitle="`${rows.length.toLocaleString()} checkpoints · x = commit date, y = hour of day, size = lines changed`"
    :summary="`Scatter plot of ${rows.length} checkpoints by commit date and hour of day, sized by lines changed and colored by agent.`"
    :table="table"
    :empty="!rows.length"
    empty-text="No checkpoints in this range"
  >
    <div class="h-96">
      <VChart :option="option" autoresize @click="onClick" />
    </div>
    <template #footer>
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div class="flex flex-wrap gap-1.5" role="group" aria-label="Filter by agent">
          <button
            v-for="l in legend"
            :key="l.agent"
            type="button"
            class="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm transition-colors border"
            :class="[
              selectedAgent === l.agent ? 'border-default bg-elevated' : 'border-transparent hover:bg-elevated',
              selectedAgent && selectedAgent !== l.agent ? 'opacity-50' : '',
            ]"
            :aria-pressed="selectedAgent === l.agent"
            :title="selectedAgent === l.agent ? 'Clear the agent filter' : `Show only ${l.agent}`"
            @click="emit('toggleAgent', l.agent)"
          >
            <svg viewBox="0 0 12 12" class="size-3" aria-hidden="true">
              <path :d="agentSymbolSvg(l.agent)" :fill="agentColor(l.agent, theme.mode)" />
            </svg>
            <span class="text-default">{{ l.agent }}</span>
            <span class="text-muted tabular-nums">{{ l.count }} · {{ l.pct }}%</span>
          </button>
        </div>
        <div class="flex items-end gap-3 text-xs text-muted" aria-label="Size legend: lines changed">
          <span class="self-center">Lines changed</span>
          <span v-for="s in sizeLegend" :key="s.v" class="inline-flex flex-col items-center gap-1">
            <span
              class="rounded-full border border-current opacity-60"
              :style="{ width: `${s.d}px`, height: `${s.d}px` }"
            />
            <span class="tabular-nums">{{ s.v.toLocaleString() }}</span>
          </span>
        </div>
      </div>
    </template>
  </ChartCard>
</template>
