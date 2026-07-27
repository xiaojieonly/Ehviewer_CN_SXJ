<template>
  <div class="login-container">
    <div class="login-card">
      <h1>EhViewer</h1>
      <p class="subtitle">Web Client</p>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="username">Username</label>
          <input id="username" v-model="username" type="text" required placeholder="Enter username" />
        </div>
        <div class="form-group">
          <label for="password">Password</label>
          <input id="password" v-model="password" type="password" required placeholder="Enter password" />
        </div>
        <div v-if="error" class="error">{{ error }}</div>
        <button type="submit" :disabled="loading" class="btn-primary">
          {{ loading ? 'Loading...' : 'Login' }}
        </button>
        <button type="button" @click="handleRegister" :disabled="loading" class="btn-secondary">
          Register
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  loading.value = true
  error.value = ''
  try {
    const res = await authStore.login(username.value, password.value)
    if (res.success) {
      router.push('/')
    } else {
      error.value = res.message
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Login failed'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true
  error.value = ''
  try {
    const res = await authStore.register(username.value, password.value)
    if (res.success) {
      router.push('/')
    } else {
      error.value = res.message
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Registration failed'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}
.login-card {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}
h1 {
  text-align: center;
  margin-bottom: 0.25rem;
  color: #333;
}
.subtitle {
  text-align: center;
  color: #666;
  margin-bottom: 1.5rem;
}
.form-group {
  margin-bottom: 1rem;
}
label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: #555;
}
input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}
input:focus {
  outline: none;
  border-color: #4a90d9;
}
.error {
  color: #e74c3c;
  margin-bottom: 1rem;
  text-align: center;
}
.btn-primary {
  width: 100%;
  padding: 0.6rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  margin-bottom: 0.5rem;
}
.btn-primary:hover {
  background: #357abd;
}
.btn-primary:disabled {
  background: #b0c4de;
}
.btn-secondary {
  width: 100%;
  padding: 0.6rem;
  background: transparent;
  color: #4a90d9;
  border: 1px solid #4a90d9;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}
.btn-secondary:hover {
  background: #f0f7ff;
}
</style>
