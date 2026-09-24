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

    <view class="card" v-if="me && me.role === 'LEARNER'" @click="openPlan">
      <view class="title">今日计划</view>
      <view class="muted" v-if="!plan || !plan.goal">还没有学习目标：打开一本书，点“设学习目标”</view>
      <view class="muted" v-else-if="!plan.totalMinutes">今天没有安排任务</view>
      <view class="muted" v-else>今天 {{ plan.totalMinutes }} 分钟，已完成 {{ plan.doneMinutes }} 分钟</view>
      <view class="warn" v-if="plan && plan.goal && plan.goal.warning">{{ plan.goal.warning }}</view>
    </view>

    <view class="card" v-if="me && me.role === 'LEARNER'" @click="openReview">
      <view class="title">今日复习</view>
      <view class="muted">{{ dueCount ? `有 ${dueCount} 张卡片到期，先回想再翻面` : '今天没有到期的卡片' }}</view>
    </view>

    <view class="card" v-if="retestCount" @click="openRetest">
      <view class="title">延时复测</view>
      <view class="muted">{{ retestCount }} 个知识点到了复测时间，看看还记不记得</view>
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
const dueCount = ref(0)
const retestCount = ref(0)
const plan = ref(null)

async function load() {
  if (!userId.value) return
  me.value = await api.me()
  books.value = await api.books()
  dueCount.value = me.value.role === 'LEARNER' ? (await api.dueCards()).dueCount : 0
  retestCount.value = me.value.role === 'LEARNER' ? (await api.dueRetests()).length : 0
  plan.value = me.value.role === 'LEARNER' ? await api.plan(me.value.id) : null
}

function openPlan() {
  if (plan.value && plan.value.goal) uni.navigateTo({ url: '/pages/plan/plan' })
}

function openRetest() {
  uni.navigateTo({ url: '/pages/retest/retest' })
}

function openReview() {
  if (dueCount.value) uni.navigateTo({ url: '/pages/review/review' })
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
.warn {
  color: #d54941;
  margin-top: 8rpx;
}
.book {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eee;
}
</style>
