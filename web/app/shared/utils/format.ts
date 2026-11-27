/**
 * Formatting utility functions
 */

/**
 * Format date
 */
export function formatDate(date: string | Date, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return ''
  
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * Format file size
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/**
 * Format number (thousands separator)
 */
export function formatNumber(num: number): string {
  return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/**
 * Format token count with locale (e.g. 1,234). Handles null/undefined/NaN as '-'.
 */
export function formatTokenCount(value?: number | string | null): string {
  if (value == null) return '-'
  const num = typeof value === 'string' ? parseInt(value, 10) : Number(value)
  if (Number.isNaN(num)) return '-'
  return num.toLocaleString()
}

/**
 * Truncate text
 */
export function truncate(text: string, length: number, suffix = '...'): string {
  if (text.length <= length) return text
  return text.substring(0, length) + suffix
}

/**
 * Compact number for tiles and axes: 1234 -> "1.2k", 3400000 -> "3.4M"
 */
export function formatCompact(value?: number | null): string {
  if (value == null || Number.isNaN(value)) return '-'
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

/**
 * Ratio (0..1) as a percentage string; '-' when null
 */
export function formatPercent(ratio?: number | null, digits = 0): string {
  if (ratio == null || Number.isNaN(ratio)) return '-'
  return `${(ratio * 100).toFixed(digits)} %`
}
