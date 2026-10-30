/*
 * Mastery levels, same thresholds as the backend (MasteryService.level / docs/api.md):
 * weak < 0.6 <= medium < 0.8 <= good; none = not practised yet.
 * A level is always shown with its label, never by color alone.
 */
export const LEVELS = {
  good: { label: '掌握', cls: 'good' },
  medium: { label: '一般', cls: 'medium' },
  weak: { label: '薄弱', cls: 'weak' },
  none: { label: '未练习', cls: 'none' },
}

export const THRESHOLDS = [0.6, 0.8]

export const percent = (m) => (m == null ? '-' : Math.round(m * 100) + '%')

// AI dialogue topic results (dialogue TopicResult)
export const TOPIC_RESULTS = {
  SELF_CORRECT: { label: '自己答出', cls: 'good' },
  HINT_CORRECT: { label: '提示后答出', cls: 'medium' },
  REVEALED: { label: '看了讲解', cls: 'weak' },
  SKIPPED_IRRELEVANT: { label: '跳过', cls: 'none' },
}
