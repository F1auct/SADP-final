<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Role, type User } from '../api'
const roles: Role[] = ['COLLECTOR', 'REVIEWER', 'FIELD_WORKER', 'ADMIN']
const names: Record<Role, string> = { COLLECTOR: '采集者', REVIEWER: '核验者', FIELD_WORKER: '第三方工作人员', ADMIN: '管理员' }
const accounts = ref<User[]>([])
const form = ref({ username: '', displayName: '', initialPassword: '', roles: [] as Role[] })
const error = ref('')
const notice = ref('')
async function refresh() { accounts.value = await api<User[]>('/admin/users') }
onMounted(() => { refresh().catch(() => { error.value = '无法加载账号列表' }) })
async function create() {
  error.value = ''; notice.value = ''
  try {
    await api('/admin/users', { method: 'POST', body: JSON.stringify(form.value) })
    form.value = { username: '', displayName: '', initialPassword: '', roles: [] }
    await refresh(); notice.value = '账号已创建，初次登录需修改密码'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '创建失败' }
}
async function save(account: User) {
  error.value = ''; notice.value = ''
  try {
    await api(`/admin/users/${account.id}`, { method: 'PATCH', body: JSON.stringify({ displayName: account.displayName, enabled: account.enabled, roles: account.roles }) })
    await refresh(); notice.value = '账号已更新'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '更新失败'; await refresh() }
}
</script>
<template>
  <section class="page-heading"><div class="eyebrow">团队管理</div><h1>用户账号</h1><p>每个人使用独立账号。业务角色可以兼任。</p></section>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="notice" class="success">{{ notice }}</p>
  <section class="card admin-create"><h2>创建账号</h2><form @submit.prevent="create" class="admin-form"><label>登录名<input v-model="form.username" required pattern="[A-Za-z0-9._-]{3,64}" /></label><label>姓名<input v-model="form.displayName" required /></label><label>初始密码<input v-model="form.initialPassword" type="password" minlength="10" required /></label><fieldset><legend>角色</legend><label v-for="role in roles" :key="role" class="check"><input v-model="form.roles" type="checkbox" :value="role" />{{ names[role] }}</label></fieldset><button class="primary-button">创建账号</button></form></section>
  <section class="account-list"><h2>已有账号</h2><article v-for="account in accounts" :key="account.id" class="card account-row"><div><strong>{{ account.username }}</strong><span class="mono tiny">{{ account.id }}</span></div><label>姓名<input v-model="account.displayName" /></label><fieldset><legend>角色</legend><label v-for="role in roles" :key="role" class="check"><input v-model="account.roles" type="checkbox" :value="role" />{{ names[role] }}</label></fieldset><label class="check"><input v-model="account.enabled" type="checkbox" />启用账号</label><button class="secondary-button" @click="save(account)">保存修改</button></article></section>
</template>
