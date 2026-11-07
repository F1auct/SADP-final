<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, user, type User } from '../api'
const router = useRouter()
const oldPassword = ref('')
const newPassword = ref('')
const confirm = ref('')
const error = ref('')
async function save() {
  if (newPassword.value !== confirm.value) { error.value = '两次输入的新密码不同'; return }
  try {
    user.value = await api<User>('/me/password', { method: 'POST', body: JSON.stringify({ oldPassword: oldPassword.value, newPassword: newPassword.value }) })
    await router.push('/')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '修改失败' }
}
</script>
<template>
  <section class="card narrow-card"><div class="eyebrow">账号安全</div><h1>设置新密码</h1><p class="muted">首次登录后修改初始密码，再进入工作台。</p>
    <form @submit.prevent="save"><label>初始密码<input v-model="oldPassword" type="password" autocomplete="current-password" required /></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" minlength="10" required /></label><label>再次输入新密码<input v-model="confirm" type="password" autocomplete="new-password" required /></label><p v-if="error" class="error" role="alert">{{ error }}</p><button class="primary-button">保存并进入工作台</button></form>
  </section>
</template>
