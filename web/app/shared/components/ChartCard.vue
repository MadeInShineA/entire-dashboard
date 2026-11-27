<script setup lang="ts">
/**
 * Card around a chart: title + subtitle, optional controls, a legend/footer slot, and a
 * Chart / Table toggle so every plotted value can also be read without the canvas.
 */
export interface ChartTable {
  columns: string[]
  rows: (string | number)[][]
}

const props = defineProps<{
  title: string
  subtitle?: string
  /** One-sentence text alternative for screen readers */
  summary?: string
  table?: ChartTable
  empty?: boolean
  emptyText?: string
}>()

const showTable = ref(false)
const hasTable = computed(() => !!props.table?.rows.length)
function toggleTable() {
  showTable.value = !showTable.value
}
</script>

<template>
  <UCard :ui="{ body: 'p-4 sm:p-5' }">
    <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 mb-3">
      <div class="min-w-0">
        <h3 class="text-base font-semibold text-highlighted">{{ title }}</h3>
        <p v-if="subtitle" class="text-sm text-muted mt-0.5">{{ subtitle }}</p>
      </div>
      <div class="flex items-center gap-2">
        <slot name="actions" />
        <UButton
          v-if="hasTable && !empty"
          size="xs"
          color="neutral"
          variant="ghost"
          :icon="showTable ? 'i-lucide-chart-column' : 'i-lucide-table'"
          :aria-pressed="showTable"
          @click="toggleTable"
        >
          {{ showTable ? 'Chart' : 'Table' }}
        </UButton>
      </div>
    </div>

    <div v-if="empty" class="text-center py-12 text-sm text-muted">
      <UIcon name="i-lucide-chart-no-axes-column" class="w-8 h-8 mx-auto mb-2 opacity-50" />
      {{ emptyText ?? 'No data for this selection' }}
    </div>
    <div v-else-if="showTable && table" class="max-h-96 overflow-auto rounded-md border border-default">
      <table class="w-full text-sm">
        <thead class="bg-muted sticky top-0">
          <tr>
            <th
              v-for="(c, i) in table.columns"
              :key="c"
              class="px-3 py-1.5 font-medium text-default"
              :class="i === 0 ? 'text-left' : 'text-right'"
            >{{ c }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, ri) in table.rows" :key="ri" class="border-t border-default">
            <td
              v-for="(v, ci) in r"
              :key="ci"
              class="px-3 py-1"
              :class="ci === 0 ? 'text-left text-default' : 'text-right tabular-nums text-default'"
            >{{ typeof v === 'number' ? v.toLocaleString() : v }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else role="img" :aria-label="summary || title">
      <slot />
    </div>

    <div v-if="$slots.footer && !empty" class="mt-3 pt-3 border-t border-default">
      <slot name="footer" />
    </div>
  </UCard>
</template>
