<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">👁️</div>
        <h2>县域儿童青少年近视<br/>筛查复诊配镜补助平台</h2>
        <p class="sub">筛查只是起点 · 复诊配镜 · 补助发放 · 复查干预 · 全链闭环</p>
      </div>
      <el-form :model="form" @keyup.enter="doLogin">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" size="large" placeholder="密码"
                    prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>

      <el-divider>演示账号（密码均为 123456）</el-divider>
      <div class="accounts">
        <el-tag v-for="a in accounts" :key="a.u" class="acc-tag" :type="a.type" effect="plain"
                @click="quick(a.u)">
          {{ a.label }}：{{ a.u }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'edu', password: '123456' })

const accounts = [
  { u: 'edu', label: '县教体部门', type: 'primary' },
  { u: 'health', label: '县卫健部门', type: 'success' },
  { u: 'doctor1', label: '学校健康老师', type: 'warning' },
  { u: 'teacher1', label: '班主任', type: 'warning' },
  { u: 'screener', label: '筛查机构', type: 'info' },
  { u: 'hospital', label: '县医院', type: 'danger' },
  { u: 'clinic', label: '合作门诊', type: 'danger' },
  { u: 'parent', label: '家长', type: 'success' },
  { u: 'admin', label: '管理员', type: '' }
]

const quick = (u) => { form.username = u; form.password = '123456' }

const doLogin = async () => {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号密码')
  loading.value = true
  try {
    const data = await login(form)
    auth.setLogin(data)
    ElMessage.success(`欢迎，${data.realName}`)
    router.push(data.role === 'PARENT' ? '/my-child' : '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6f8b 0%, #2c8da8 50%, #4fb0c6 100%);
  padding: 20px;
}
.login-card {
  width: 460px;
  max-width: 100%;
  background: #fff;
  border-radius: 14px;
  padding: 34px 38px;
  box-shadow: 0 18px 60px rgba(0,0,0,.22);
}
.brand { text-align: center; margin-bottom: 18px; }
.logo { font-size: 44px; }
.brand h2 { margin: 8px 0 6px; color: #1f3a4d; font-size: 20px; line-height: 1.5; }
.sub { color: #888; font-size: 12.5px; margin: 0; }
.accounts { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.acc-tag { cursor: pointer; }
</style>
