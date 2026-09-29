<template>
  <view>
    <view class="card">
      <view class="tabs">
        <text :class="['tab', mode === 'login' && 'on']" @click="mode = 'login'">登录</text>
        <text :class="['tab', mode === 'register' && 'on']" @click="mode = 'register'">注册</text>
      </view>
      <input class="input" v-model="username" placeholder="用户名" />
      <input class="input" v-model="password" password placeholder="密码（至少 6 位）" />
      <template v-if="mode === 'register'">
        <input class="input" v-model="displayName" placeholder="怎么称呼你（可不填）" />
        <view class="roles">
          <button size="mini" :type="role === 'LEARNER' ? 'primary' : 'default'" @click="role = 'LEARNER'">我自己学</button>
          <button size="mini" :type="role === 'TUTOR' ? 'primary' : 'default'" @click="role = 'TUTOR'">我辅导别人学</button>
        </view>
      </template>
      <button type="primary" :disabled="busy" @click="submit">{{ mode === 'login' ? '登录' : '注册并登录' }}</button>
    </view>

    <view class="card">
      <view class="title">演示账号</view>
      <view class="muted">本地开发模式下自动创建，密码都是 demo123</view>
      <view class="roles">
        <button v-for="u in demoUsers" :key="u.username" size="mini" @click="demo(u.username)">{{ u.label }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { api, saveSession } from '../../api/client'

const demoUsers = [
  { username: 'learner', label: '学习者 小明' },
  { username: 'tutor', label: '辅导者 王老师' },
  { username: 'ops', label: '运维' },
]

const mode = ref('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const role = ref('LEARNER')
const busy = ref(false)

async function submit() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请填写用户名和密码', icon: 'none' })
    return
  }
  busy.value = true
  try {
    const session = mode.value === 'login'
      ? await api.login(username.value, password.value)
      : await api.register({ username: username.value, password: password.value, displayName: displayName.value, role: role.value })
    saveSession(session)
    uni.reLaunch({ url: '/pages/index/index' })
  } finally {
    busy.value = false
  }
}

function demo(name) {
  mode.value = 'login'
  username.value = name
  password.value = 'demo123'
  submit()
}
</script>

<style>
.tabs { display: flex; gap: 32rpx; margin-bottom: 20rpx; }
.tab { font-size: 32rpx; color: #888; }
.tab.on { color: #0052d9; font-weight: 600; }
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; }
.roles { display: flex; gap: 12rpx; margin: 16rpx 0; flex-wrap: wrap; }
</style>
