<template>
  <view v-if="plan">
    <view class="card">
      <view class="title">今天 {{ plan.totalMinutes }} 分钟 · 已完成 {{ plan.doneMinutes }} 分钟</view>
      <view class="muted" v-if="plan.goal">目标：{{ scopeLabel[plan.goal.scope] }}，{{ plan.goal.deadline }} 前学完</view>
      <view class="warn" v-if="plan.goal && plan.goal.warning">{{ plan.goal.warning }}</view>
      <view class="actions">
        <button size="mini" @click="replan">按最新掌握情况重排</button>
      </view>
    </view>

    <!-- 番茄钟：计时在前端，开始 / 结束记到后端，供行为画像使用 -->
    <view class="card timer" v-if="timer">
      <view class="title">{{ timerLabel }}</view>
      <view class="clock">{{ mmss }}</view>
      <view class="actions">
        <button size="mini" @click="interrupt">被打断了 {{ timer.interruptions }}</button>
        <button size="mini" type="warn" @click="stopTimer(false)">放弃</button>
      </view>
    </view>

    <view class="card" v-for="b in plan.blocks" :key="b.blockNo">
      <view class="block-head">
        <view class="title">第 {{ b.blockNo }} {{ blockUnit }} · {{ b.minutes }} 分钟</view>
        <button size="mini" v-if="!timer && !blockDone(b)" @click="startTimer(b)">开始计时</button>
      </view>
      <view v-for="t in b.tasks" :key="t.id" :class="['task', t.status.toLowerCase()]">
        <view class="task-body">
          <view>{{ taskLabel(t) }} <text class="muted">{{ t.minutes }} 分钟</text></view>
          <view class="muted">{{ t.reason }}</view>
        </view>
        <view class="task-actions" v-if="t.status === 'TODO'">
          <button size="mini" type="primary" @click="go(t)">去做</button>
          <button size="mini" @click="mark(t, 'DONE')">完成</button>
          <button size="mini" @click="mark(t, 'SKIPPED')">跳过</button>
        </view>
        <view class="muted" v-else>{{ t.status === 'DONE' ? '已完成' : '已跳过' }}</view>
      </view>
    </view>

    <view class="card" v-if="!plan.blocks.length">
      <view class="muted">今天没有安排任务</view>
    </view>

    <view class="card" v-if="plan.upcoming.length">
      <view class="title">接下来几天</view>
      <view v-for="d in plan.upcoming" :key="d.date" class="day">
        <view>{{ d.date }}</view>
        <view class="muted">{{ d.tasks }} 项 · {{ d.minutes }} 分钟</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { api, currentUserId } from '../../api/client'

const scopeLabel = { BOOK: '整本书', CHAPTERS: '指定章节', WEAK: '补薄弱点' }
const typeLabel = {
  REVIEW_CARDS: '复习卡片',
  RETEST: '延时复测',
  NEW_LEARN: '初学',
  QUIZ: '小测',
  FEYNMAN: '费曼讲解',
  DELIBERATE: '刻意练习',
  INTERLEAVED: '交错练习',
  FIRST_PRINCIPLES: '第一性原理',
}

const plan = ref(null)
const timer = ref(null)
const now = ref(Date.now())
let tick = null

const blockUnit = computed(() => {
  const r = plan.value && plan.value.goal && plan.value.goal.rhythm
  return r === 'POMODORO' ? '个番茄' : '块'
})
const remaining = computed(() => (timer.value ? Math.max(0, timer.value.endsAt - now.value) : 0))
const mmss = computed(() => {
  const s = Math.ceil(remaining.value / 1000)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})
const timerLabel = computed(() => (timer.value ? `第 ${timer.value.blockNo} ${blockUnit.value}进行中` : ''))

function taskLabel(t) {
  if (t.type === 'REVIEW_CARDS') return `复习 ${t.cardCount} 张卡片`
  return t.nodeName ? `${typeLabel[t.type]}：${t.nodeName}` : typeLabel[t.type]
}

function blockDone(b) {
  return b.tasks.every((t) => t.status !== 'TODO')
}

async function load() {
  plan.value = await api.plan(currentUserId())
}

async function replan() {
  plan.value = await api.replan(plan.value.goal.learnerId)
  uni.showToast({ title: '已重排', icon: 'none' })
}

async function mark(t, status) {
  await api.markTask(t.id, status)
  await load()
}

function go(t) {
  const g = plan.value.goal.graphId
  const q = `graphId=${g}&nodeId=${t.nodeId}`
  const urls = {
    REVIEW_CARDS: '/pages/review/review',
    RETEST: '/pages/retest/retest',
    NEW_LEARN: `/pages/node/node?${q}`,
    QUIZ: `/pages/node/node?${q}`,
    FEYNMAN: `/pages/feynman/feynman?${q}&name=${encodeURIComponent(t.nodeName || '')}`,
    DELIBERATE: `/pages/practice/practice?${q}&mode=DELIBERATE`,
    FIRST_PRINCIPLES: `/pages/practice/practice?${q}&mode=FIRST_PRINCIPLES`,
    INTERLEAVED: `/pages/practice/practice?graphId=${g}&mode=INTERLEAVED`,
  }
  uni.navigateTo({ url: urls[t.type] })
}

async function startTimer(b) {
  const firstTodo = b.tasks.find((t) => t.status === 'TODO')
  const rhythm = plan.value.goal.rhythm
  const minutes = rhythm === 'LONG' ? b.minutes : rhythm === 'MICRO' ? 10 : 25
  const s = await api.startPomodoro(firstTodo ? firstTodo.id : null, minutes)
  timer.value = { id: s.id, blockNo: b.blockNo, endsAt: Date.now() + minutes * 60000, interruptions: 0 }
  tick = setInterval(() => {
    now.value = Date.now()
    if (remaining.value <= 0) stopTimer(true)
  }, 1000)
}

function interrupt() {
  timer.value.interruptions++
}

async function stopTimer(completed) {
  if (!timer.value) return
  const t = timer.value
  timer.value = null
  clearInterval(tick)
  await api.finishPomodoro(t.id, completed, t.interruptions)
  uni.showToast({ title: completed ? '这一块完成了，休息 5 分钟吧' : '已放弃这一块', icon: 'none' })
}

onShow(load)
onUnload(() => clearInterval(tick))
</script>

<style>
.warn {
  color: #d54941;
  margin-top: 8rpx;
}
.actions {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}
.timer {
  background: #fff4f0;
  text-align: center;
}
.clock {
  font-size: 80rpx;
  font-weight: 600;
  margin: 12rpx 0;
}
.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
.task.done .task-body,
.task.skipped .task-body {
  opacity: 0.5;
}
.task-actions {
  display: flex;
  gap: 10rpx;
  margin-top: 10rpx;
}
.task-actions uni-button {
  margin: 0;
}
.day {
  display: flex;
  justify-content: space-between;
  padding: 10rpx 0;
}
</style>
