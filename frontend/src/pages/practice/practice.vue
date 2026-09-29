<template>
  <view>
    <view class="card">
      <view class="title">{{ modeLabel[mode] }}</view>
      <view class="muted" v-if="session && session.status === 'IN_PROGRESS'">{{ phaseLabel[session.phase] }} · {{ session.answeredInBatch + 1 }} / {{ session.batchSize }}</view>
      <view class="muted" v-else-if="error">{{ error }}</view>
    </view>

    <view class="card lesson" v-if="session && session.lesson">
      <view class="title">先看讲解：{{ session.lesson.name }}</view>
      <view class="definition">{{ session.lesson.definition }}</view>
    </view>

    <view class="card" v-if="session && session.next">
      <QuestionCard :question="session.next" :disabled="busy" @answer="submit" />
      <view v-if="last && !last.correct" class="wrong">
        上一题答错了，正确答案：{{ last.expectedAnswer }}
      </view>
    </view>

    <view class="card" v-if="session && session.status === 'COMPLETED'">
      <view class="title">练习完成</view>
      <view class="definition">{{ session.summary }}</view>
      <button type="primary" @click="back">返回</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'
import QuestionCard from '../../components/QuestionCard.vue'

const modeLabel = { DELIBERATE: '刻意练习', INTERLEAVED: '交错练习', FIRST_PRINCIPLES: '第一性原理：补基础' }
const phaseLabel = {
  VERIFY: '先验证是不是真的不会', PRACTICE: '专项练习', MIXED: '混合题，不提示考哪个知识点',
  PROBE: '先测一测这些基础会不会', LEARN: '补这个知识点', GOAL: '回到目标本身',
}

const mode = ref('')
const session = ref(null)
const last = ref(null)
const error = ref('')
const busy = ref(false)

async function submit(answer) {
  busy.value = true
  try {
    // 交错练习里做错的题按“方法选错”记错因（后端只在答错时记录）
    const errorType = mode.value === 'INTERLEAVED' ? 'WRONG_METHOD' : null
    const res = await api.answerPractice(session.value.id, session.value.next.id, { answer, errorType })
    last.value = res
    session.value = res.session
  } finally {
    busy.value = false
  }
}

function back() {
  uni.navigateBack()
}

onLoad(async (q) => {
  mode.value = q.mode
  try {
    session.value = await api.startPractice(q.graphId, q.mode, q.nodeId ? Number(q.nodeId) : null)
  } catch (e) {
    error.value = e.message
  }
})
</script>

<style>
.lesson { background: #f0f6ff; }
.definition { margin: 16rpx 0; line-height: 1.7; }
.wrong { color: #e34d59; margin-top: 16rpx; }
.row { display: flex; gap: 10rpx; margin-top: 16rpx; align-items: center; flex-wrap: wrap; }
</style>
