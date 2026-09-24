<template>
  <view>
    <view v-if="session && session.status === 'IN_PROGRESS' && session.next">
      <view class="card">
        <view class="muted">第 {{ session.round }} 轮 · {{ roundLabel[session.round] }} · {{ session.answeredInRound + 1 }} / {{ session.totalInRound }}</view>
        <progress :percent="percent" stroke-width="4" activeColor="#2b6ef6" />
      </view>
      <view class="card">
        <view class="stem">{{ session.next.stem }}</view>
        <view class="muted hint">先选一下你有多大把握</view>
        <view class="row">
          <button v-for="c in confidences" :key="c.key" size="mini" :type="confidence === c.key ? 'primary' : 'default'"
                  @click="confidence = c.key">{{ c.label }}</button>
        </view>
        <view v-if="options.length" class="row">
          <button v-for="o in options" :key="o" size="mini" :disabled="busy" @click="submit(o)">{{ o }}</button>
        </view>
        <view v-else class="row">
          <input class="input" v-model="input" placeholder="填写答案" />
          <button size="mini" :disabled="busy" @click="submit(input)">提交</button>
        </view>
      </view>
    </view>

    <view v-else-if="report">
      <view class="card">
        <view class="title">诊断完成</view>
        <view class="muted">以下是根据这次作答推测的结果，建议在学习中继续验证</view>
      </view>
      <view class="card" v-if="report.rootCauses.length">
        <view class="title red">疑似根因</view>
        <view v-for="n in report.rootCauses" :key="n.nodeId" class="item">
          {{ n.name }}<text class="muted" v-if="n.impact"> · 影响 {{ n.impact }} 个后续知识点</text>
        </view>
      </view>
      <view class="card" v-if="report.affected.length">
        <view class="title">受前置影响</view>
        <view class="muted">先补上面的根因，这些可能会跟着好起来</view>
        <view v-for="n in report.affected" :key="n.nodeId" class="item">{{ n.name }}</view>
      </view>
      <view class="card" v-if="report.toObserve.length">
        <view class="title">建议再观察</view>
        <view class="muted">第一次做错、验证时做对了，可能是粗心或蒙错</view>
        <view v-for="n in report.toObserve" :key="n.nodeId" class="item">{{ n.name }}</view>
      </view>
      <view class="card" v-if="report.good.length">
        <view class="title green">已经会了</view>
        <view v-for="n in report.good" :key="n.nodeId" class="item">{{ n.name }}</view>
      </view>
      <view class="card muted" v-if="report.untested">还有 {{ report.untested }} 个知识点这次没有测到</view>
      <view class="card"><button type="primary" @click="back">回到知识地图</button></view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const roundLabel = { 1: '广度扫描', 2: '定向诊断', 3: '验证' }
const confidences = [
  { key: 'SURE', label: '有把握' },
  { key: 'UNSURE', label: '不确定' },
  { key: 'DONT_KNOW', label: '不会' },
]

const session = ref(null)
const report = ref(null)
const confidence = ref('UNSURE')
const input = ref('')
const busy = ref(false)

const percent = computed(() =>
  session.value && session.value.totalInRound ? Math.round((session.value.answeredInRound / session.value.totalInRound) * 100) : 0)
const options = computed(() => {
  try {
    return session.value && session.value.next.optionsJson ? JSON.parse(session.value.next.optionsJson) : []
  } catch (e) {
    return []
  }
})

async function show(s) {
  session.value = s
  if (s.status === 'COMPLETED' || !s.next) {
    report.value = await api.diagnosticReport(s.id)
  }
}

async function submit(answer) {
  if (!answer || busy.value) return
  busy.value = true
  try {
    const res = await api.answerDiagnostic(session.value.id, session.value.next.id, { answer, selfConfidence: confidence.value })
    input.value = ''
    confidence.value = 'UNSURE'
    await show(res.session)
  } finally {
    busy.value = false
  }
}

function back() {
  uni.navigateBack()
}

onLoad(async (q) => {
  await show(await api.startDiagnostic(q.graphId))
})
</script>

<style>
.stem { white-space: pre-wrap; line-height: 1.7; }
.hint { margin-top: 20rpx; }
.row { display: flex; gap: 12rpx; margin-top: 12rpx; align-items: center; }
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 8rpx 12rpx; }
.item { padding: 12rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.red { color: #e34d59; }
.green { color: #2ba471; }
</style>
