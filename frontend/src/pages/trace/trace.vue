<template>
  <view v-if="t">
    <view class="card">
      <view class="title">{{ t.name }}</view>
      <view class="muted">{{ statusLabel[t.status] }} · 掌握度 {{ pct(t.mastery) }} · 可信度 {{ pct(t.confidence) }}</view>
      <view class="definition" v-if="t.definition">{{ t.definition }}</view>
      <view class="explain">{{ t.explanation }}</view>
    </view>

    <view class="card">
      <view class="title">前置知识点</view>
      <view v-if="!t.prerequisites.length" class="muted">没有前置</view>
      <view v-for="p in t.prerequisites" :key="p.nodeId" class="prereq" :style="{ paddingLeft: (p.depth - 1) * 32 + 'rpx' }" @click="open(p.nodeId)">
        <view :class="['dot', p.status.toLowerCase()]"></view>
        <view>{{ p.name }} <text class="muted">{{ statusLabel[p.status] }} {{ pct(p.mastery) }}</text>
          <text v-if="t.rootCauses.includes(p.name)" class="root"> 最可能的根因</text></view>
      </view>
    </view>

    <view class="card">
      <view class="title">最近的作答</view>
      <view v-if="!t.recent.length" class="muted">还没有作答记录</view>
      <view v-for="(e, i) in t.recent" :key="i" class="ev">
        <view>
          <text :class="e.result === 'CORRECT' ? 'ok' : e.result === 'INCORRECT' ? 'bad' : ''">{{ resultLabel[e.result] }}</text>
          · {{ activityLabel[e.activity] || e.activity }}<text v-if="e.errorType"> · {{ errorLabel[e.errorType] }}</text>
          <text class="muted"> {{ e.at ? e.at.slice(5, 16).replace('T', ' ') : '' }}</text>
        </view>
        <view class="muted stem" v-if="e.questionStem">{{ e.questionStem }}</view>
      </view>
    </view>

    <view class="card" v-if="t.flags.length">
      <view class="title">标记记录</view>
      <view v-for="f in t.flags" :key="f.id" class="muted">{{ flagLabel[f.kind] }}{{ f.note ? '：' + f.note : '' }}{{ f.resolved ? '（已处理）' : '' }}</view>
    </view>

    <view class="card" v-if="isTutor">
      <view class="title">你的判断</view>
      <input class="note" v-model="note" placeholder="说明（可不填），例如：课上答得很好" />
      <view class="actions">
        <button size="mini" @click="flag('DIAGNOSIS_WRONG')">诊断不对，他其实会</button>
        <button size="mini" type="warn" @click="flag('REALLY_WEAK')">确实不会</button>
      </view>
      <view class="muted">标“诊断不对”后会马上给他安排一次复测来验证</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const statusLabel = { UNTOUCHED: '未学', UNSURE: '数据不足', MASTERED: '已掌握', LEARNING: '学习中', WEAK: '疑似薄弱' }
const resultLabel = { CORRECT: '对', INCORRECT: '错', PARTIAL: '部分对' }
const activityLabel = { DIAGNOSTIC: '诊断', PRACTICE: '练习', CARD: '卡片', FEYNMAN: '费曼', INTERLEAVED: '交错', QUIZ: '小测', RETEST: '复测' }
const errorLabel = { KNOWLEDGE_GAP: '知识不会', CONCEPT_CONFUSION: '概念混淆', CALCULATION: '计算', READING: '审题', CARELESS: '粗心', WRONG_METHOD: '方法选错' }
const flagLabel = { STUCK: '补了两轮还卡住', DIAGNOSIS_WRONG: '诊断不对', REALLY_WEAK: '确实不会' }

const t = ref(null)
const learnerId = ref(null)
const isTutor = ref(false)
const note = ref('')

function pct(v) {
  return v == null ? '—' : `${Math.round(v * 100)}%`
}

function open(nodeId) {
  uni.navigateTo({ url: `/pages/trace/trace?learnerId=${learnerId.value}&nodeId=${nodeId}` })
}

async function flag(kind) {
  await api.flag(learnerId.value, t.value.nodeId, kind, note.value || null)
  uni.showToast({ title: '已标记', icon: 'none' })
  t.value = await api.trace(learnerId.value, t.value.nodeId)
}

onLoad(async (q) => {
  learnerId.value = q.learnerId
  isTutor.value = (await api.me()).role === 'TUTOR'
  t.value = await api.trace(q.learnerId, q.nodeId)
})
</script>

<style>
.definition { margin: 12rpx 0; line-height: 1.7; }
.explain { background: #f0f6ff; padding: 16rpx; border-radius: 8rpx; margin-top: 12rpx; }
.prereq { display: flex; align-items: center; gap: 12rpx; padding: 12rpx 0; }
.root { color: #d54941; font-weight: 600; }
.ev { padding: 12rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.stem { font-size: 24rpx; }
.ok { color: #2ba471; }
.bad { color: #e34d59; }
.note { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 12rpx; margin: 12rpx 0; }
.actions { display: flex; gap: 12rpx; margin: 12rpx 0; }
.actions uni-button { margin: 0; }
.dot { width: 22rpx; height: 22rpx; border-radius: 50%; box-sizing: border-box; flex-shrink: 0; }
.dot.mastered { background: #2ba471; }
.dot.learning { background: #f5b400; }
.dot.weak { background: #e34d59; }
.dot.unsure { border: 3rpx dashed #8a8f99; }
.dot.untouched { background: #d4d7dd; }
</style>
