<template>
  <view v-if="node">
    <view class="card">
      <view class="title">{{ node.name }}</view>
      <view class="muted">{{ statusLabel }}<text v-if="node.mastery != null"> · 掌握度 {{ Math.round(node.mastery * 100) }}%</text></view>
      <view class="definition">{{ node.definition }}</view>
      <view v-if="detail && detail.sources.length" class="sources">
        <view class="muted">原文出处</view>
        <view v-for="(src, i) in detail.sources" :key="i" class="source">{{ src }}</view>
      </view>
      <view v-if="detail" class="muted">
        <text v-if="prereqs.length">前置：{{ prereqs.join('、') }}　</text>
        <text v-if="nexts.length">后续：{{ nexts.join('、') }}</text>
      </view>
    </view>

    <view class="card">
      <view class="title">换一种方式学</view>
      <view class="modes">
        <button size="mini" @click="go('feynman')">费曼讲解</button>
        <button size="mini" @click="go('DELIBERATE')">刻意练习</button>
        <button size="mini" @click="go('FIRST_PRINCIPLES')">以它为目标补基础</button>
      </view>
    </view>

    <view class="card">
      <view class="title">小测</view>
      <view v-if="!questions.length" class="muted">这个知识点还没有题目</view>
      <view v-for="q in questions" :key="q.id" class="question">
        <view class="stem">{{ q.stem }}</view>
        <view class="muted">{{ q.source === 'AI' ? 'AI 生成' : '书中原题' }}</view>
        <view v-if="options(q).length" class="options">
          <button v-for="o in options(q)" :key="o" size="mini" :disabled="!!results[q.id]" @click="submit(q, o)">{{ o }}</button>
        </view>
        <view v-else class="options">
          <input class="input" v-model="inputs[q.id]" placeholder="填写答案" :disabled="!!results[q.id]" />
          <button size="mini" :disabled="!!results[q.id]" @click="submit(q, inputs[q.id])">提交</button>
        </view>
        <view v-if="results[q.id]" :class="['result', results[q.id].correct ? 'ok' : 'bad']">
          {{ results[q.id].correct ? '回答正确' : '回答错误，正确答案：' + results[q.id].expectedAnswer }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api, nodeStatus } from '../../api/client'

const graphId = ref(null)
const nodeId = ref(null)
const node = ref(null)
const questions = ref([])
const inputs = reactive({})
const results = reactive({})

const statusLabel = computed(() => (node.value ? nodeStatus(node.value).label : ''))

function options(q) {
  try {
    return q.optionsJson ? JSON.parse(q.optionsJson) : []
  } catch (e) {
    return []
  }
}

function go(mode) {
  const q = `graphId=${graphId.value}&nodeId=${nodeId.value}`
  if (mode === 'feynman') {
    uni.navigateTo({ url: `/pages/feynman/feynman?${q}&name=${encodeURIComponent(node.value.name)}` })
  } else {
    uni.navigateTo({ url: `/pages/practice/practice?${q}&mode=${mode}` })
  }
}

const detail = ref(null)
const prereqs = computed(() => (detail.value ? detail.value.relations.filter((r) => r.incoming && r.type === 'PREREQUISITE' && !r.disputed).map((r) => r.otherName) : []))
const nexts = computed(() => (detail.value ? detail.value.relations.filter((r) => !r.incoming && r.type === 'PREREQUISITE' && !r.disputed).map((r) => r.otherName) : []))

async function load() {
  detail.value = await api.nodeDetail(graphId.value, nodeId.value)
  const graph = await api.graph(graphId.value)
  node.value = graph.nodes.find((n) => String(n.id) === String(nodeId.value))
  questions.value = await api.questions(graphId.value, nodeId.value)
}

async function submit(q, answer) {
  if (!answer) return
  const res = await api.answer({ questionId: q.id, activityType: 'QUIZ', answer })
  results[q.id] = res
  const update = res.updates.find((u) => String(u.nodeId) === String(nodeId.value))
  if (update) {
    node.value = { ...node.value, mastery: update.mastery, confidence: update.confidence }
  }
}

onLoad((q) => {
  graphId.value = q.graphId
  nodeId.value = q.nodeId
  load()
})
</script>

<style>
.modes {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}
.sources {
  margin: 12rpx 0;
}
.source {
  font-size: 24rpx;
  color: #555;
  background: #f5f6f8;
  padding: 12rpx;
  margin-top: 8rpx;
  border-radius: 8rpx;
}
.definition {
  margin-top: 16rpx;
  line-height: 1.7;
}
.question {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
.stem {
  white-space: pre-wrap;
  line-height: 1.6;
}
.options {
  display: flex;
  gap: 12rpx;
  margin-top: 12rpx;
  align-items: center;
}
.input {
  flex: 1;
  border: 1rpx solid #ddd;
  border-radius: 8rpx;
  padding: 8rpx 12rpx;
}
.result { margin-top: 10rpx; }
.result.ok { color: #2ba471; }
.result.bad { color: #e34d59; }
</style>
