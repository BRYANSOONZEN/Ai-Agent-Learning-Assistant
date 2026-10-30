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

