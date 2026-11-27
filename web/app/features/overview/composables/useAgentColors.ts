/**
 * Agent colors for HTML (badges, legends). Same fixed agent -> palette slot mapping as the charts
 * (shared/utils/chart-palette.ts), resolved for the current color mode.
 */
import { agentColor, agentSymbolSvg } from '~/shared/utils/chart-palette'

export function useAgentColors() {
  const colorMode = useColorMode()
  const mode = computed(() => (colorMode.value === 'dark' ? 'dark' : 'light') as 'light' | 'dark')

  function getAgentColor(agent: string | null | undefined): string {
    return agentColor(agent, mode.value)
  }

  /** Badge: neutral text (text never wears the series color), identity carried by a colored border + symbol */
  function getAgentBadgeStyle(agent: string | null | undefined): Record<string, string> {
    const c = getAgentColor(agent)
    return { backgroundColor: `${c}1f`, borderColor: `${c}66` }
  }

  return { getAgentColor, getAgentBadgeStyle, agentSymbolSvg }
}
