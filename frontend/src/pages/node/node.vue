<template>
  <view v-if="node">
    <view class="card">
      <view class="title">{{ node.name }}</view>
      <view class="muted">{{ statusLabel }}<text v-if="node.mastery != null"> · 掌握度 {{ Math.round(node.mastery * 100) }}%</text></view>
      <view class="definition">{{ node.definition }}</view>
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

async function load() {
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
