export const navItems = [
  { id: 'dashboard', label: '总览', icon: 'grid' },
  { id: 'learn', label: '学习与提问', icon: 'spark' },
  { id: 'progress', label: '掌握度诊断', icon: 'chart' },
  { id: 'materials', label: '课程资料', icon: 'book' },
  { id: 'teacher', label: '教师审核', icon: 'review', badge: '3' },
]

export const stages = [
  { id: 'engage', label: '引入', short: '01', caption: '连接旧知' },
  { id: 'explore', label: '探究', short: '02', caption: '先自己想' },
  { id: 'explain', label: '解释', short: '03', caption: '整理概念' },
  { id: 'elaborate', label: '迁移', short: '04', caption: '换题练习' },
  { id: 'evaluate', label: '评价', short: '05', caption: '检查掌握' },
]

export const materials = [
  {
    id: 'S1',
    title: '边界值分析：先找边界',
    type: 'principle',
    typeLabel: '原理句',
    detail: '边界值分析关注输入范围的边缘位置，包括边界内、边界上与边界外的值。',
    source: '课件 · 第 12 页',
    visibleToStudent: true,
  },
  {
    id: 'S2',
    title: '年龄输入例题',
    type: 'example',
    typeLabel: '例题答案',
    detail: '包含具体检查值的完整示例。提交作答前不向学生引用。',
    source: '课件 · 第 13 页',
    visibleToStudent: false,
  },
  {
    id: 'S3',
    title: '边界值的组合策略',
    type: 'principle',
    typeLabel: '原理句',
    detail: '对多个输入条件进行组合时，可以先覆盖每个变量的边界，再逐步增加组合。',
    source: '课件 · 第 14 页',
    visibleToStudent: true,
  },
  {
    id: 'S4',
    title: '边界测试的检查清单',
    type: 'principle',
    typeLabel: '原理句',
    detail: '一个完整的边界检查应说明输入值、预期是否接受，以及选择该值的理由。',
    source: '课件 · 第 15 页',
    visibleToStudent: true,
  },
]

export const masteryItems = [
  { title: '边界值分析', score: 68, trend: '+12%', tone: 'blue', note: '正在建立概念' },
  { title: '等价类划分', score: 82, trend: '+8%', tone: 'green', note: '掌握良好' },
  { title: '判定表', score: 46, trend: '待复习', tone: 'orange', note: '需要更多练习' },
]

export const reviewItems = [
  {
    id: 'EV-1048',
    student: '林晓雨',
    avatar: '林',
    issue: '把范围外的值称为有效',
    concept: '边界值分析',
    status: '待审核',
    time: '10 分钟前',
    level: 'L2',
  },
  {
    id: 'EV-1046',
    student: '周子涵',
    avatar: '周',
    issue: '只写了边界，没有说明预期结果',
    concept: '边界值分析',
    status: '待审核',
    time: '26 分钟前',
    level: 'L1',
  },
  {
    id: 'EV-1041',
    student: '张可心',
    avatar: '张',
    issue: '混淆等价类与边界值',
    concept: '测试设计基础',
    status: '已处理',
    time: '1 小时前',
    level: 'L3',
  },
]
