// Labels shared by the teacher exercise pages (docs/模块说明/练习与掌握度-设计.md)

export const QUESTION_TYPES = [
  { value: 'single_choice', label: '单选', hint: '程序判；蒙对概率 1/选项数' },
  { value: 'two_tier', label: '两段式单选', hint: '先选答案再选理由，两段都对才算对；能分出蒙对的' },
  { value: 'multiple', label: '多选', hint: '全对才算对' },
  { value: 'judge', label: '判断', hint: '蒙对概率 1/2，算掌握度时扣掉' },
  { value: 'fill', label: '填空', hint: '答案是一个词或短语，和标准说法对比' },
  { value: 'short_answer', label: '简答', hint: 'AI 判两次，一致才采用，权重最低；不一致交给老师' },
]

export const ASSESS_TYPES = [
  { value: 'concept', label: '概念辨析', hint: '分不分得清概念、有没有常见误解' },
  { value: 'scenario', label: '情境应用', hint: '在新场景里会不会选对方法' },
  { value: 'data', label: '数据分析', hint: '会不会读实验结果、得出结论' },
  { value: 'process', label: '流程步骤', hint: '步骤的先后和每一步做什么' },
]

export const typeLabel = (v) => QUESTION_TYPES.find((t) => t.value === v)?.label || '单选'
export const assessLabel = (v) => ASSESS_TYPES.find((t) => t.value === v)?.label || '概念辨析'
export const SOURCE_LABEL = { manual: '手写', upload: '上传识别', ai: 'AI 生成' }
