/*
 * The optional parts a teacher picks when creating a course (same keys as CourseService.MODULES).
 * Chapter intro and 预览发布 are always there, so they are not listed.
 */
export const MODULES = [
  { key: 'video', label: '视频', icon: 'VideoCamera', desc: '上传教学视频，一章可以放好几个' },
  { key: 'document', label: '文档', icon: 'Document', desc: '课件、PDF、Word，或直接写一段阅读' },
  { key: 'knowledge', label: '知识点', icon: 'Collection', desc: '整理本章知识点，标重点难点' },
  { key: 'dialogue', label: 'AI 对话', icon: 'ChatDotRound', desc: '学完和 AI 对话，巩固理解' },
  { key: 'exercise', label: '题目', icon: 'EditPen', desc: '单选题，每题关联知识点' },
]

export const ALL_MODULE_KEYS = MODULES.map((m) => m.key)

export const moduleLabel = (key) => MODULES.find((m) => m.key === key)?.label || key

// a course from before modules existed has none saved: treat it as "everything on"
export const has = (course, key) => !course?.modules?.length || course.modules.includes(key)

/*
 * Kinds of chapter items (content a chapter is made of, in learning order).
 * The type key is also the course module that must be on to add it.
 */
export const ITEM_TYPES = [
  { type: 'video', label: '视频', icon: 'VideoCamera', placeholder: '视频：' },
  { type: 'document', label: '文档', icon: 'Document', placeholder: '阅读：' },
  { type: 'exercise', label: '练习', icon: 'EditPen', placeholder: '练习：' },
  { type: 'dialogue', label: 'AI 对话', icon: 'ChatDotRound', placeholder: 'AI 对话：' },
]

export const itemType = (type) => ITEM_TYPES.find((t) => t.type === type)

// one line under an item in the outline, e.g. "3 道题" / "没有文件"
export function itemSummary(item) {
  if (item.type === 'exercise') return item.exerciseCount ? `${item.exerciseCount} 道题` : '还没有题目'
  if (item.type === 'dialogue') return item.topicCount ? `${item.topicCount} 个话题` : '还没有话题'
  if (item.material) return item.material.originalName
  if (item.type === 'document' && item.description?.trim()) return '文字阅读'
  return '还没有文件'
}
