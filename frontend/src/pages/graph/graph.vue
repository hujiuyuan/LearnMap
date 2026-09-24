<template>
  <view>
    <view class="card">
      <view class="title">{{ lit }} / {{ total }} 个知识点已点亮</view>
      <view class="muted" v-if="graph && graph.reviewStatus === 'UNREVIEWED'">这张图谱由 AI 生成、未经人工校对，诊断结论仅供参考</view>
    </view>

    <!-- 移动端默认用“按章列表”展示，不画一大团网（产品方案 2.6） -->
    <view class="card" v-for="ch in chapters" :key="ch.id">
      <view class="title">{{ ch.title }}</view>
      <view v-for="n in ch.nodes" :key="n.id" class="node" @click="openNode(n)">
        <view :class="['dot', status(n).key]"></view>
        <view class="node-body">
          <view>{{ n.name }}</view>
          <view class="muted">{{ typeLabel[n.type] }} · {{ status(n).label }}<text v-if="prereqNames(n)"> · 前置：{{ prereqNames(n) }}</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, nodeStatus } from '../../api/client'

const typeLabel = { FACT: '事实/术语', CONCEPT: '概念', PRINCIPLE: '原理/公式', METHOD: '方法/技能', SYNTHESIS: '综合应用' }

const graphId = ref(null)
const bookId = ref(null)
const graph = ref(null)
const sections = ref([])

const status = nodeStatus
const total = computed(() => (graph.value ? graph.value.nodes.length : 0))
const lit = computed(() => (graph.value ? graph.value.nodes.filter((n) => nodeStatus(n).key === 'good').length : 0))

const chapters = computed(() => {
  if (!graph.value) return []
  const byId = Object.fromEntries(sections.value.map((s) => [s.id, s]))
  const chapterOf = (sectionId) => {
    let s = byId[sectionId]
    while (s && s.parentId && byId[s.parentId]) s = byId[s.parentId]
    return s
  }
  const groups = new Map()
  for (const n of graph.value.nodes) {
    const ch = chapterOf(n.sectionId) || { id: 0, title: '其他' }
    if (!groups.has(ch.id)) groups.set(ch.id, { id: ch.id, title: ch.title, nodes: [] })
    groups.get(ch.id).nodes.push(n)
  }
  return [...groups.values()]
})

function prereqNames(node) {
  const names = Object.fromEntries(graph.value.nodes.map((n) => [n.id, n.name]))
  return graph.value.relations
    .filter((r) => r.type === 'PREREQUISITE' && r.toNodeId === node.id && !r.disputed)
    .map((r) => names[r.fromNodeId])
    .join('、')
}

function openNode(n) {
  uni.navigateTo({ url: `/pages/node/node?graphId=${graphId.value}&nodeId=${n.id}` })
}

async function load() {
  if (!graphId.value) return
  graph.value = await api.graph(graphId.value)
  sections.value = await api.sections(bookId.value)
}

onLoad((q) => {
  graphId.value = q.graphId
  bookId.value = q.bookId
})
onShow(load)
</script>

<style>
.node {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
.node-body {
  flex: 1;
  margin-left: 16rpx;
}
.dot {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  box-sizing: border-box;
}
.dot.good { background: #2ba471; }
.dot.learning { background: #f5b400; }
.dot.weak { background: #e34d59; }
.dot.unsure { border: 3rpx dashed #8a8f99; }
.dot.new { background: #d4d7dd; }
</style>
