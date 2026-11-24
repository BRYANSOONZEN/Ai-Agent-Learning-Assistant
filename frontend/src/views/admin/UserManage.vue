<script setup>
/*
 * Admin page: user management.
 * - list with role filter + search (username or name), 10 per page, newest first
 * - create a teacher or student account (admin accounts can't be created here)
 * - enable / disable: a disabled user's token stops working on their next request
 * - reset password: also lifts a 5-wrong-passwords lock
 * Every rule is also enforced by the backend (AdminUserService).
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { createUser, getUserCounts, getUsers, resetUserPassword, setUserStatus } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const roleMap = {
  admin: { label: '管理员', type: 'danger' },
  teacher: { label: '老师', type: 'warning' },
  student: { label: '学生', type: 'info' },
}

const query = reactive({ role: '', keyword: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const counts = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [page, c] = await Promise.all([getUsers(query), getUserCounts()])
    list.value = page.records
    total.value = page.total
    counts.value = c
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

const editable = (row) => row.role !== 'admin' && row.id !== userStore.user?.id

async function toggle(row) {
  const next = row.status === 1 ? 0 : 1
  if (next === 0) {
    const ok = await ElMessageBox.confirm(
      `禁用后「${row.name}」马上无法登录，已经打开的页面也会退回登录页。确定禁用吗？`, '禁用账号', { type: 'warning' })
      .catch(() => false)
    if (!ok) return
  }
  await setUserStatus(row.id, next)
  ElMessage.success(next === 0 ? '已禁用' : '已启用')
  load()
}

// ---- create ----
const PASSWORD = /^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$/
const PASSWORD_MSG = '密码为 8～64 位，至少包含一个字母和一个数字'
const createVisible = ref(false)
const createRef = ref()
const saving = ref(false)
const createForm = reactive({ role: 'teacher', username: '', name: '', password: '' })
const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '用户名为 4～32 位字母、数字或下划线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }, { max: 32, message: '不超过 32 个字', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, { pattern: PASSWORD, message: PASSWORD_MSG, trigger: 'blur' }],
}

function openCreate() {
  Object.assign(createForm, { role: 'teacher', username: '', name: '', password: '' })
  createVisible.value = true
}

async function submitCreate() {
  if (!(await createRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    await createUser(createForm)
    ElMessage.success(`已开通${roleMap[createForm.role].label}账号「${createForm.username}」`)
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- reset password ----
async function reset(row) {
  const result = await ElMessageBox.prompt(
    `给「${row.name}」（${row.username}）设一个新密码。重置后锁定也会解除。`, '重置密码', {
      inputType: 'password',
      inputPlaceholder: '8～64 位，含字母和数字',
      inputPattern: PASSWORD,
      inputErrorMessage: PASSWORD_MSG,
    }).catch(() => null)
  if (!result) return
  await resetUserPassword(row.id, result.value)
  ElMessage.success('密码已重置')
  load()
}

const date = (v) => (v ? v.replace('T', ' ').slice(0, 16) : '-')

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="用户管理" description="学生可以自己注册；老师账号在这里开通。禁用后马上生效，重置密码会同时解除锁定。">
      <el-button type="primary" icon="Plus" @click="openCreate">开通账号</el-button>
    </PageHeader>

    <div v-if="counts" class="stats">
      <div class="app-card stat"><span class="label">老师</span><strong>{{ counts.teacher }}</strong></div>
      <div class="app-card stat"><span class="label">学生</span><strong>{{ counts.student }}</strong></div>
      <div class="app-card stat"><span class="label">管理员</span><strong>{{ counts.admin }}</strong></div>
      <div class="app-card stat"><span class="label">已禁用</span><strong>{{ counts.disabled }}</strong></div>
    </div>

    <div class="app-card">
      <div class="app-toolbar">
        <el-radio-group v-model="query.role" @change="search">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="teacher">老师</el-radio-button>
          <el-radio-button value="student">学生</el-radio-button>
          <el-radio-button value="admin">管理员</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名或姓名"
          prefix-icon="Search"
          clearable
          style="max-width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="roleMap[row.role]?.type" size="small" effect="plain">{{ roleMap[row.role]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="info" size="small">已禁用</el-tag>
            <el-tag v-if="row.locked" type="danger" size="small" class="lock-tag">锁定中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ date(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="editable(row)">
              <el-button link :type="row.status === 1 ? 'danger' : 'primary'" @click="toggle(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="primary" @click="reset(row)">重置密码</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="createVisible" title="开通账号" width="440px">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="角色">
          <el-radio-group v-model="createForm.role">
            <el-radio-button value="teacher">老师</el-radio-button>
            <el-radio-button value="student">学生</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="4～32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="createForm.name" placeholder="例如：陈老师" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="8～64 位，含字母和数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">开通</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--app-space-md);
  margin-bottom: var(--app-space-md);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat .label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.stat strong {
  font-size: 22px;
}

.lock-tag {
  margin-left: 4px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.pager {
  margin-top: var(--app-space-md);
  justify-content: flex-end;
}
</style>
