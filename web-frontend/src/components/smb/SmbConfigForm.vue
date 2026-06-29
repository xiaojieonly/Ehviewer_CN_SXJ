<template>
  <div class="smb-config-form">
    <div class="form-group">
      <label>服务器地址</label>
      <input v-model="config.host" placeholder="192.168.1.100" />
    </div>
    <div class="form-group">
      <label>端口</label>
      <input v-model.number="config.port" type="number" placeholder="445" />
    </div>
    <div class="form-group">
      <label>共享文件夹</label>
      <input v-model="config.share" placeholder="media" />
    </div>
    <div class="form-group">
      <label>远程路径</label>
      <input v-model="config.path" placeholder="可选，如 EhViewer" />
    </div>
    <div class="form-group">
      <label>登录模式</label>
      <select v-model="config.loginMode">
        <option value="GUEST">访客</option>
        <option value="USER">用户名/密码</option>
      </select>
    </div>
    <div v-if="config.loginMode === 'USER'" class="form-group">
      <label>用户名</label>
      <input v-model="config.username" />
    </div>
    <div v-if="config.loginMode === 'USER'" class="form-group">
      <label>密码</label>
      <input v-model="config.password" type="password" />
    </div>
    <div class="form-group">
      <label>启用 SMB 备份</label>
      <input type="checkbox" v-model="config.enabled" />
    </div>
    <div class="form-actions">
      <button @click="$emit('test')" class="btn-secondary">测试连接</button>
      <button @click="$emit('save')" class="btn-primary">保存配置</button>
    </div>
    <div v-if="testResult" class="test-result" :class="{ success: testResult.success }">
      {{ testResult.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SmbConfig, SmbTestResult } from '../../api/smb'

defineProps<{
  config: SmbConfig & { password?: string }
  testResult: SmbTestResult | null
}>()

defineEmits<{
  save: []
  test: []
}>()
</script>

<style scoped>
.smb-config-form {
  background: white;
  border-radius: 8px;
  padding: 1rem;
}
.form-group {
  margin-bottom: 12px;
}
.form-group label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
}
.form-group input,
.form-group select {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
.form-group input[type="checkbox"] {
  width: auto;
}
.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 1rem;
}
.btn-primary {
  padding: 8px 16px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.btn-secondary {
  padding: 8px 16px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.test-result {
  margin-top: 1rem;
  padding: 8px;
  border-radius: 4px;
  background: #f8d7da;
  color: #721c24;
}
.test-result.success {
  background: #d4edda;
  color: #155724;
}
</style>
