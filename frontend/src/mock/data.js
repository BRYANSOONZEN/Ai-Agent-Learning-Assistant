// Fake data used while VITE_USE_MOCK=true. Keep the shapes identical to the API contract.

export const mockUsers = {
  student: { id: 1001, name: '张同学', role: 'student' },
  teacher: { id: 2001, name: '李老师', role: 'teacher' },
  admin: { id: 1, name: '管理员', role: 'admin' },
}

export const mockCourses = [
  { id: 1, name: '软件工程导论', teacher: '李老师', status: 'published', chapterCount: 12, studentCount: 86, description: '软件生命周期、过程模型与项目管理基础。', createdAt: '2026-09-01' },
  { id: 2, name: '需求工程', teacher: '王老师', status: 'published', chapterCount: 8, studentCount: 64, description: '需求获取、分析、建模与验证。', createdAt: '2026-09-03' },
  { id: 3, name: '软件设计与体系结构', teacher: '李老师', status: 'draft', chapterCount: 10, studentCount: 0, description: '设计原则、设计模式与架构风格。', createdAt: '2026-09-10' },
  { id: 4, name: '软件测试', teacher: '赵老师', status: 'published', chapterCount: 9, studentCount: 72, description: '黑盒、白盒测试与自动化测试。', createdAt: '2026-09-12' },
  { id: 5, name: '软件项目管理', teacher: '王老师', status: 'archived', chapterCount: 7, studentCount: 40, description: '进度、成本、风险与质量管理。', createdAt: '2026-03-01' },
  { id: 6, name: 'UML 建模', teacher: '赵老师', status: 'published', chapterCount: 6, studentCount: 58, description: '用例图、类图、时序图与状态图。', createdAt: '2026-09-15' },
]

export const mockProfile = {
  student: { id: 1001, name: '张同学', className: '软工 2301 班' },
  stats: { studyHours: 46.5, exercisesDone: 312, accuracy: 0.78, weakPoints: 4 },
  mastery: [
    { name: '需求分析', value: 0.82 },
    { name: '系统设计', value: 0.64 },
    { name: '设计模式', value: 0.48 },
    { name: '软件测试', value: 0.75 },
    { name: '项目管理', value: 0.58 },
    { name: 'UML 建模', value: 0.88 },
  ],
  weeklyTrend: {
    weeks: ['第1周', '第2周', '第3周', '第4周', '第5周', '第6周'],
    accuracy: [0.62, 0.66, 0.7, 0.69, 0.75, 0.78],
    studyHours: [5.5, 7, 8.5, 6, 9.5, 10],
  },
}
