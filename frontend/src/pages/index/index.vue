<template>
  <view>
    <view class="card">
      <view class="title">当前身份</view>
      <view class="muted">登录还没做，先用演示账号切换（后端 dev 模式会自动创建）</view>
      <view class="users">
        <button v-for="u in demoUsers" :key="u.id" size="mini"
                :type="userId === u.id ? 'primary' : 'default'" @click="switchUser(u.id)">
          {{ u.label }}
        </button>
      </view>
      <view v-if="me" class="muted">{{ me.displayName }} · {{ roleLabel[me.role] }}</view>
    </view>

    <view class="card">
      <view class="title">书架</view>
      <view v-if="!books.length" class="muted">还没有可以看的书</view>
      <view v-for="b in books" :key="b.id" class="book" @click="openBook(b)">
        <view>{{ b.title }}</view>
        <view class="muted">{{ visibilityLabel[b.visibility] }} · {{ b.status === 'READY' ? '图谱已生成' : b.status }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api, currentUserId, setCurrentUserId } from '../../api/client'

const demoUsers = [
  { id: 3, label: '学习者 小明' },
  { id: 2, label: '辅导者 王老师' },
  { id: 1, label: '运维' },
]
const roleLabel = { LEARNER: '学习者', TUTOR: '辅导者', OPS: '平台运维' }
const visibilityLabel = { PRIVATE: '我上传的', TUTOR_GROUP: '辅导者的书', PUBLIC: '平台预置' }

const userId = ref(currentUserId())
const me = ref(null)
const books = ref([])

async function load() {
  if (!userId.value) return
  me.value = await api.me()
  books.value = await api.books()
}

function switchUser(id) {
  setCurrentUserId(id)
  userId.value = id
  load()
}

async function openBook(book) {
  if (me.value.role !== 'LEARNER') {
    uni.showToast({ title: '辅导者和运维的页面还在开发中', icon: 'none' })
    return
  }
  // 学习者打开书时拿到自己的图谱副本（第一次打开会复制一份）
  const graph = await api.assign(book.id)
  uni.navigateTo({ url: `/pages/graph/graph?graphId=${graph.id}&bookId=${book.id}` })
}

onShow(load)
</script>

<style>
.users {
  display: flex;
  gap: 12rpx;
  margin: 16rpx 0;
}
.book {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eee;
}
</style>
