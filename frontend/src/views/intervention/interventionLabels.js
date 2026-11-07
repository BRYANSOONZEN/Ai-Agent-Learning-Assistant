/*
 * Labels for 干预 (docs/计划-干预A和B.md). Two layers per student x knowledge point:
 * concept = AI dialogue topics, application = exercise mastery. Their combination is the alert type.
 */
export const ALERT_TYPES = {
  A: { label: 'A 应用问题', short: '应用', tag: 'orange', hint: '对话讲得出，换个情境做题就错' },
  B: { label: 'B 概念问题', short: '概念', tag: 'purple', hint: '对话里没讲出道理，或跳过了不作答' },
  AB: { label: 'A + B 两层都不好', short: '两层', tag: 'red', hint: '概念没懂，题也做不对：先补概念再练习' },
}

export const CONCEPT = {
  good: { label: '自己答出', tag: 'teal' },
  fair: { label: '提示后答出', tag: 'teal' },
  bad: { label: '讲解后仍不会或跳过', tag: 'red' },
  none: { label: '还没做对话', tag: 'gray' },
}

export const APPLICATION = {
  good: { label: '掌握', tag: 'teal' },
  bad: { label: '不好', tag: 'red' },
  none: { label: '还没做够 3 次', tag: 'gray' },
}

export const INTERVENTION_TYPES = {
  PRACTICE: '变式练习',
  REVIEW: '复习 + 重做 AI 对话',
  REVIEW_PRACTICE: '先复习 + 重做对话，再做练习',
}

/** before / after values: mastery as "0.22" for practice, a concept state for review */
export function valueText(type, value) {
  if (value == null) return '-'
  if (type === 'REVIEW') return CONCEPT[value]?.label || value
  return Math.round(Number(value) * 100) + '%'
}
