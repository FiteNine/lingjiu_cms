import { http } from './request'
import type {
  DictItem,
  DictType,
  MenuNode,
  MenuType,
  OpLog,
  PageResult,
  RoleItem,
  RoleRow,
  SysUser,
} from '@/types'

const USER_BASE = '/api/system/users'
const ROLE_BASE = '/api/system/roles'
const MENU_BASE = '/api/system/menus'
const DICT_BASE = '/api/system/dict'

/* ---------------- 用户 ---------------- */

export interface UserQuery {
  page: number
  size: number
  username?: string
  status?: number | ''
}

export interface UserBody {
  username: string
  password?: string
  nickname: string
  email?: string | null
  phone?: string | null
  status: number
  remark?: string | null
  roleIds: number[]
  /** 可切换访问的站点 id；不选表示仅默认站点（admin 角色不受限制） */
  siteIds: number[]
}

export function listUsers(params: UserQuery) {
  return http.get<PageResult<SysUser>>(USER_BASE, params)
}

export function createUser(data: UserBody) {
  return http.post<null>(USER_BASE, data)
}

export function updateUser(id: number, data: UserBody) {
  return http.put<null>(`${USER_BASE}/${id}`, data)
}

export function deleteUser(id: number) {
  return http.delete<null>(`${USER_BASE}/${id}`)
}

export function updateUserStatus(id: number, status: number) {
  return http.put<null>(`${USER_BASE}/${id}/status`, { status })
}

export function resetUserPassword(id: number, password: string) {
  return http.put<null>(`${USER_BASE}/${id}/password`, { password })
}

/* ---------------- 角色 ---------------- */

export interface RoleQuery {
  page: number
  size: number
  name?: string
}

export interface RoleBody {
  code: string
  name: string
  sort: number
  status: number
  remark?: string | null
}

export function listRoles(params: RoleQuery) {
  return http.get<PageResult<RoleRow>>(ROLE_BASE, params)
}

export function allRoles() {
  return http.get<RoleItem[]>(`${ROLE_BASE}/all`)
}

export function createRole(data: RoleBody) {
  return http.post<null>(ROLE_BASE, data)
}

export function updateRole(id: number, data: RoleBody) {
  return http.put<null>(`${ROLE_BASE}/${id}`, data)
}

export function deleteRole(id: number) {
  return http.delete<null>(`${ROLE_BASE}/${id}`)
}

export function getRoleMenus(id: number) {
  return http.get<{ menuIds: number[] }>(`${ROLE_BASE}/${id}/menus`)
}

export function updateRoleMenus(id: number, menuIds: number[]) {
  return http.put<null>(`${ROLE_BASE}/${id}/menus`, { menuIds })
}

/* ---------------- 菜单 ---------------- */

export interface MenuBody {
  parentId: number
  name: string
  path: string
  component?: string | null
  icon?: string | null
  perms?: string | null
  type: MenuType
  sort: number
  visible: number
  status: number
}

export function menuTree() {
  return http.get<MenuNode[]>(`${MENU_BASE}/tree`)
}

export function menuList() {
  return http.get<MenuNode[]>(MENU_BASE)
}

export function createMenu(data: MenuBody) {
  return http.post<null>(MENU_BASE, data)
}

export function updateMenu(id: number, data: MenuBody) {
  return http.put<null>(`${MENU_BASE}/${id}`, data)
}

export function deleteMenu(id: number) {
  return http.delete<null>(`${MENU_BASE}/${id}`)
}

/* ---------------- 字典 ---------------- */

export interface DictTypeQuery {
  page: number
  size: number
  code?: string
  name?: string
}

export interface DictTypeBody {
  code: string
  name: string
  remark?: string | null
}

export function listDictTypes(params: DictTypeQuery) {
  return http.get<PageResult<DictType>>(`${DICT_BASE}/types`, params)
}

export function createDictType(data: DictTypeBody) {
  return http.post<null>(`${DICT_BASE}/types`, data)
}

export function updateDictType(id: number, data: DictTypeBody) {
  return http.put<null>(`${DICT_BASE}/types/${id}`, data)
}

export function deleteDictType(id: number) {
  return http.delete<null>(`${DICT_BASE}/types/${id}`)
}

export interface DictItemBody {
  typeId: number
  label: string
  value: string
  sort: number
  status: number
}

export function listDictItems(code: string) {
  return http.get<DictItem[]>(`${DICT_BASE}/items`, { code })
}

export function createDictItem(data: DictItemBody) {
  return http.post<null>(`${DICT_BASE}/items`, data)
}

export function updateDictItem(id: number, data: DictItemBody) {
  return http.put<null>(`${DICT_BASE}/items/${id}`, data)
}

export function deleteDictItem(id: number) {
  return http.delete<null>(`${DICT_BASE}/items/${id}`)
}

/* ---------------- 操作日志 ---------------- */

export interface LogQuery {
  page: number
  size: number
  username?: string
  action?: string
}

export function listLogs(params: LogQuery) {
  return http.get<PageResult<OpLog>>('/api/system/logs', params)
}
