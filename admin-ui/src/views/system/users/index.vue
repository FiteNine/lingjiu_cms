<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="用户名">
          <el-input
            v-model="query.username"
            placeholder="用户名"
            clearable
            style="width: 180px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button v-permission="'sys:user:add'" type="primary" :icon="Plus" @click="openDialog()">
          新增用户
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="110" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role.id" size="small" class="row-tag">
              {{ role.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              v-permission="'sys:user:edit'"
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val: boolean | string | number) => onStatusChange(row, Number(val))"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:user:edit'" link type="primary" @click="openDialog(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'sys:user:reset'"
              link
              type="warning"
              @click="openResetDialog(row)"
            >
              重置密码
            </el-button>
            <el-button v-permission="'sys:user:delete'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onSizeChange"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑用户' : '新增用户'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改密码' : '请输入密码'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleIds"
            multiple
            collapse-tags
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option v-for="role in roleOptions" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="站点">
          <el-select
            v-model="form.siteIds"
            multiple
            collapse-tags
            placeholder="可切换访问的站点；不选则仅默认站点"
            style="width: 100%"
          >
            <el-option v-for="site in siteChoices" :key="site.id" :label="site.name" :value="site.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            @update:model-value="(val: boolean | string | number) => (form.status = Number(val))"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px" destroy-on-close>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password placeholder="新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onResetPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  allRoles,
  createUser,
  deleteUser,
  listUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus,
  type UserBody,
} from '@/api/system'
import { siteOptions } from '@/api/cms'
import type { RoleItem, SiteOption, SysUser, TableRow } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<SysUser[]>([])
const total = ref(0)
const roleOptions = ref<RoleItem[]>([])
// 站点下拉复用 sites/options：它只返回当前操作者可访问的站点，所以授权不会超出自己的可见范围
const siteChoices = ref<SiteOption[]>([])

const query = reactive({
  page: 1,
  size: 20,
  username: '',
  status: '' as number | '',
})

async function load() {
  loading.value = true
  try {
    const data = await listUsers({ ...query })
    rows.value = data.records
    total.value = data.total
  } catch {
    // 失败提示由请求拦截器统一给出，这里只保证 loading 被复位
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

/** 改每页条数时回到第 1 页，否则可能停在一个越界的空白页上 */
function onSizeChange() {
  query.page = 1
  load()
}

function onReset() {
  query.username = ''
  query.status = ''
  onSearch()
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: 0,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1,
  remark: '',
  roleIds: [] as number[],
  siteIds: [] as number[],
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!form.id && !value) {
          callback(new Error('请输入密码'))
        } else if (value && value.trim().length < 6) {
          // 与「重置密码」保持同一强度，避免新增出 1 位或纯空白的弱密码
          callback(new Error('密码长度至少 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function openDialog(row?: TableRow) {
  form.id = row ? Number(row.id) : 0
  form.username = row ? String(row.username) : ''
  form.password = ''
  form.nickname = row ? String(row.nickname) : ''
  form.email = row ? String(row.email ?? '') : ''
  form.phone = row ? String(row.phone ?? '') : ''
  form.status = row ? Number(row.status) : 1
  form.remark = row ? String(row.remark ?? '') : ''
  form.roleIds = row ? ((row.roleIds as number[]) || []) : []
  // 后端把 Long 序列化成字符串，站点选项的 id 是 number，不转就对不上
  form.siteIds = row ? ((row.siteIds as number[]) || []).map(Number) : []
  dialogVisible.value = true
}

async function onSave() {
  const instance = formRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  saving.value = true
  try {
    const body: UserBody = {
      username: form.username,
      password: form.password || undefined,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      status: form.status,
      remark: form.remark,
      roleIds: form.roleIds,
      siteIds: form.siteIds,
    }
    if (form.id) {
      await updateUser(form.id, body)
    } else {
      await createUser(body)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

/* ---------------- 重置密码 ---------------- */

const resetVisible = ref(false)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive({ id: 0, password: '' })
const resetRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
}

function openResetDialog(row: TableRow) {
  resetForm.id = Number(row.id)
  resetForm.password = ''
  resetVisible.value = true
}

async function onResetPassword() {
  const instance = resetFormRef.value
  if (!instance) return
  try {
    await instance.validate()
  } catch {
    // 校验失败是正常分支，不往事件处理器外抛
    return
  }
  saving.value = true
  try {
    await resetUserPassword(resetForm.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch {
    // 失败提示由请求拦截器统一给出，这里保持弹窗打开便于重试
  } finally {
    saving.value = false
  }
}

/* ---------------- 状态 / 删除 ---------------- */

async function onStatusChange(row: TableRow, status: number) {
  try {
    await updateUserStatus(Number(row.id), status)
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已停用')
  } catch {
    row.status = status === 1 ? 0 : 1
  }
}

function onDelete(row: TableRow) {
  ElMessageBox.confirm(`确认删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteUser(Number(row.id))
      ElMessage.success('删除成功')
      load()
    })
    .catch((error) => {
      // 用户取消（'cancel'/'close'）无需处理，接口失败必须区分出来
      if (error !== 'cancel' && error !== 'close') {
        ElMessage.error('删除失败，请稍后重试')
      }
    })
}

onMounted(() => {
  load()
  allRoles()
    .then((data) => {
      roleOptions.value = data
    })
    .catch(() => {
      // 失败提示由请求拦截器统一给出；下拉为空时仍能保存用户，只是不能改角色
    })
  siteOptions()
    .then((data) => {
      siteChoices.value = data
    })
    .catch(() => {
      // 失败提示由请求拦截器统一给出；下拉为空时不改站点授权
    })
})
</script>

<style scoped>
.row-tag {
  margin-right: 4px;
}
</style>
