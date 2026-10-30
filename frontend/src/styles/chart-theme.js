// Shared chart palette. Every chart uses ChartCard, which applies this theme,
// so all charts in the app use the same colors.
import * as echarts from 'echarts'

export const CHART_THEME = 'app'

export const chartColors = ['#5c76e9', '#0e7c6b', '#d98a1c', '#8a5cc7', '#d0453a', '#5aa8a0']

echarts.registerTheme(CHART_THEME, {
  color: chartColors,
  backgroundColor: 'transparent',
  textStyle: { fontFamily: 'inherit' },
})
