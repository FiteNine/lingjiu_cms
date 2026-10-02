import { http } from './request'
import type { LoginResult, UserProfile } from '@/types'

const AUTH_BASE = '/api/auth'

export interface LoginParams {
  username: string
  password: string
}

export interface UpdatePasswordParams {
  oldPassword: string
  newPassword: string
}

export function login(data: LoginParams) {
  return http.post<LoginResult>(`${AUTH_BASE}/login`, data)
}

export function getProfile() {
  return http.get<UserProfile>(`${AUTH_BASE}/profile`)
}

export function updatePassword(data: UpdatePasswordParams) {
  return http.put<null>(`${AUTH_BASE}/password`, data)
}

export function logout() {
  return http.post<null>(`${AUTH_BASE}/logout`)
}
