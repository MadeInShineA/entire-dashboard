<script setup lang="ts">
/**
 * Lines added (above the axis) and deleted (below) per day in commits linked to a checkpoint.
 * One shared axis, two opposite hues: churn and its direction read at a glance.
 */
import type { EChartsOption } from 'echarts'
import ChartCard from '~/shared/components/ChartCard.vue'
import { VChart } from '~/shared/utils/echarts'
import { shortDayLabel } from '~/shared/utils/date'
import { formatCompact } from '~/shared/utils/format'
import type { DayBucket, Totals } from '../utils/aggregate'

const props = defineProps<{
  days: DayBucket[]
  totals: Totals
}>()

const { theme, tooltip, xAxisBase, yAxisBase } = useChartTheme()

const empty = computed(() => props.totals.additions + props.totals.deletions === 0)

const option = computed<EChartsOption>(() => {
  const t = theme.value
  const bar = { type: 'bar' as const, stack: 'changes', barMaxWidth: 28 }
  return {
    grid: { left: 8, right: 8, top: 12, bottom: 4, containLabel: true },
    tooltip: {
      ...tooltip.value,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: t.splitLine, opacity: 0.6 } },
      formatter: (params: any) => {
        const d = props.days[params[0].dataIndex]!
        return `<b>${shortDayLabel(d.date)}</b><br/>`
          + `Added: <b>+${d.additions.toLocaleString()}</b><br/>`
          + `Deleted: <b>-${d.deletions.toLocaleString()}</b><br/>`
          + `${d.checkpoints} checkpoint${d.checkpoints === 1 ? '' : 's'}`
      },
    },
    xAxis: { ...xAxisBase.value, type: 'category', data: props.days.map((d) => shortDayLabel(d.date)) },
    yAxis: {
      ...yAxisBase.value,
      type: 'value',
      axisLabel: { ...yAxisBase.value.axisLabel, formatter: (v: number) => (v < 0 ? '−' : '') + formatCompact(Math.abs(v)) },
    },
    series: [
      {
        ...bar,
        name: 'Added',
        data: props.days.map((d) => d.additions),
        itemStyle: { color: t.additions, borderRadius: [4, 4, 0, 0] },
      },
      {
        ...bar,
        name: 'Deleted',
        data: props.days.map((d) => -d.deletions),
        itemStyle: { color: t.deletions, borderRadius: [0, 0, 4, 4] },
      },
    ],
  }
})

const table = computed(() => ({
  columns: ['Day', 'Lines added', 'Lines deleted', 'Checkpoints'],
  rows: props.days.map((d) => [d.date, d.additions, d.deletions, d.checkpoints]),
}))
</script>

<template>
  <ChartCard
    title="Code changes"
    subtitle="Lines added and deleted per day in commits with a checkpoint"
    :summary="`Bars of lines added (up) and deleted (down) per day: ${totals.additions} added, ${totals.deletions} deleted in total.`"
    :table="table"
    :empty="empty"
    empty-text="No code changes in this selection"
  >
    <div class="h-72">
      <VChart :option="option" autoresize />
    </div>
    <template #footer>
      <div class="flex flex-wrap gap-x-6 gap-y-1 text-sm">
        <span class="inline-flex items-center gap-2">
          <span class="size-3 rounded-sm" :style="{ background: theme.additions }" />
          <span class="text-default">Added</span>
          <span class="text-muted tabular-nums">+{{ totals.additions.toLocaleString() }}</span>
        </span>
        <span class="inline-flex items-center gap-2">
          <span class="size-3 rounded-sm" :style="{ background: theme.deletions }" />
          <span class="text-default">Deleted</span>
          <span class="text-muted tabular-nums">−{{ totals.deletions.toLocaleString() }}</span>
        </span>
      </div>
    </template>
  </ChartCard>
</template>
