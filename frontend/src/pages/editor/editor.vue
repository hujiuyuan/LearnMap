<template>
  <view v-if="graph">
    <view class="card">
      <view class="title">{{ book ? book.title : '' }} · v{{ current.versionNo }}</view>
      <view class="muted">{{ statusLabel[current.status] }} · {{ current.reviewStatus === 'REVIEWED' ? '已校对' : 'AI 生成、未校对' }}
        · 待校对 {{ pendingCount }} 个</view>
      <view class="actions">
        <button size="mini" type="primary" @click="publish">{{ current.status === 'PUBLISHED' ? '标为已校对' : '发布（已校对）' }}</button>
        <button size="mini" @click="regenerate">重新生成</button>
        <button size="mini" v-if="previous" @click="showDiff">对比 v{{ previous.versionNo }}</button>
      </view>
      <picker v-if="versions.length > 1" :range="versions.map((v) => 'v' + v.versionNo + ' ' + statusLabel[v.status])" @change="pickVersion">
        <view class="muted link">切换版本</view>
      </picker>
    </view>

    <view class="card" v-if="diff">
      <view class="title">和 v{{ diff.fromVersion }} 相比</view>
      <view v-if="diff.empty" class="muted">内容一样</view>
      <view v-for="n in diff.added" :key="'a' + n.id" class="add">+ {{ n.name }}</view>
      <view v-for="n in diff.removed" :key="'r' + n.id" class="del">− {{ n.name }}</view>
      <view v-for="c in diff.changed" :key="'c' + c.name">{{ c.name }} <text class="muted">改了{{ c.fields.join('、') }}</text></view>
      <view v-for="r in diff.addedRelations" :key="'ar' + r" class="add">+ {{ r }}</view>
      <view v-for="r in diff.removedRelations" :key="'dr' + r" class="del">− {{ r }}</view>
    </view>

    <view class="card" v-for="ch in chapters" :key="ch.id">
      <view class="title">{{ ch.title }}</view>
      <view v-for="n in ch.nodes" :key="n.id" class="node" @click="select(n)">
        <view :class="['badge', n.reviewStatus.toLowerCase()]">{{ reviewLabel[n.reviewStatus] }}</view>
        <view class="node-body">
          <view>{{ n.name }} <text class="muted">{{ typeLabel[n.type] }} · 置信度 {{ Math.round(n.sourceConfidence * 100) }}%</text></view>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="title">新增知识点</view>
      <input class="input" v-model="newNode.name" placeholder="名称" />
      <textarea class="input area" v-model="newNode.definition" placeholder="定义" />
      <picker :range="types.map((t) => typeLabel[t])" @change="(e) => (newNode.type = types[e.detail.value])">
        <view class="input">{{ newNode.type ? typeLabel[newNode.type] : '选择类型' }}</view>
      </picker>
      <button size="mini" type="primary" @click="addNode">添加（会自动出题）</button>
    </view>

    <!-- 节点编辑面板 -->
    <view class="mask" v-if="detail" @click.self="detail = null">
      <view class="panel">
        <view class="panel-head"><text class="title">编辑知识点</text><text class="link" @click="detail = null">关闭</text></view>
        <input class="input" v-model="edit.name" />
        <textarea class="input area" v-model="edit.definition" />
        <picker :range="types.map((t) => typeLabel[t])" @change="(e) => (edit.type = types[e.detail.value])">
          <view class="input">类型：{{ typeLabel[edit.type] }}</view>
        </picker>
        <view class="actions">
          <button size="mini" type="primary" @click="save">保存并确认</button>
          <button size="mini" @click="review('CONFIRMED')">确认无误</button>
          <button size="mini" type="warn" @click="review('REJECTED')">驳回（删掉）</button>
        </view>
        <view class="muted" v-if="detail.sources.length">原文出处</view>
        <view v-for="(src, i) in detail.sources" :key="i" class="source">{{ src }}</view>
        <view class="title sub">关系</view>
        <view v-for="r in detail.relations" :key="r.id" class="rel">
          <view>{{ r.incoming ? r.otherName + ' → 本节点' : '本节点 → ' + r.otherName }}（{{ relLabel[r.type] }}）
            <text :class="['badge', r.reviewStatus.toLowerCase()]">{{ reviewLabel[r.reviewStatus] }}</text></view>
          <view class="muted">证据：{{ r.evidence || '无' }} · 置信度 {{ Math.round(r.confidence * 100) }}%</view>
          <view class="actions">
            <button size="mini" @click="reviewRel(r, 'CONFIRMED')">确认</button>
            <button size="mini" @click="reviewRel(r, 'REJECTED')">驳回</button>
            <button size="mini" @click="deleteRel(r)">删除</button>
          </view>
        </view>
        <picker :range="otherNodes.map((n) => n.name)" @change="addPrereq">
          <view class="muted link">+ 添加前置知识点</view>
        </picker>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const typeLabel = { FACT: '事实/术语', CONCEPT: '概念', PRINCIPLE: '原理/公式', METHOD: '方法/技能', SYNTHESIS: '综合应用' }
const types = Object.keys(typeLabel)
const reviewLabel = { PENDING: '待校对', CONFIRMED: '已确认', REJECTED: '已驳回' }
const statusLabel = { DRAFT: '草稿', PUBLISHED: '已发布', SUPERSEDED: '已被新版本取代' }
const relLabel = { PREREQUISITE: '前置', RELATED: '相关', CONTRASTS: '易混淆' }

const bookId = ref(null)
const book = ref(null)
const versions = ref([])
const current = ref(null)
const graph = ref(null)
const sections = ref([])
const detail = ref(null)
const diff = ref(null)
const edit = reactive({ name: '', definition: '', type: 'CONCEPT' })
const newNode = reactive({ name: '', definition: '', type: '' })

const previous = computed(() => versions.value.find((v) => v.versionNo < current.value.versionNo))
const pendingCount = computed(() => graph.value.nodes.filter((n) => n.reviewStatus === 'PENDING').length)
const otherNodes = computed(() => (detail.value ? graph.value.nodes.filter((n) => n.id !== detail.value.id) : []))
const chapters = computed(() => {
  const byId = Object.fromEntries(sections.value.map((s) => [s.id, s]))
  const chapterOf = (id) => {
    let s = byId[id]
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

async function loadGraph() {
  graph.value = await api.graph(current.value.id)
}

async function load(pickId) {
  versions.value = await api.masters(bookId.value)
  current.value = versions.value.find((v) => v.id === pickId) || versions.value[0]
  diff.value = null
  await loadGraph()
}

function pickVersion(e) {
  load(versions.value[e.detail.value].id)
}

async function select(n) {
  detail.value = await api.nodeDetail(current.value.id, n.id)
  Object.assign(edit, { name: detail.value.name, definition: detail.value.definition || '', type: detail.value.type })
}

async function save() {
  detail.value = await api.editNode(current.value.id, detail.value.id, { ...edit })
  await loadGraph()
  uni.showToast({ title: '已保存', icon: 'none' })
}

async function review(status) {
  await api.reviewNode(current.value.id, detail.value.id, status)
  detail.value = null
  await loadGraph()
}

async function reviewRel(r, status) {
  await api.reviewRelation(current.value.id, r.id, status)
  detail.value = await api.nodeDetail(current.value.id, detail.value.id)
}

async function deleteRel(r) {
  await api.deleteRelation(current.value.id, r.id)
  detail.value = await api.nodeDetail(current.value.id, detail.value.id)
  await loadGraph()
}

async function addPrereq(e) {
  const from = otherNodes.value[e.detail.value]
  detail.value = await api.addRelation(current.value.id, { fromNodeId: from.id, toNodeId: detail.value.id, type: 'PREREQUISITE' })
  await loadGraph()
}

async function addNode() {
  if (!newNode.name || !newNode.type) {
    uni.showToast({ title: '请填名称和类型', icon: 'none' })
    return
  }
  await api.addNode(current.value.id, { ...newNode })
  Object.assign(newNode, { name: '', definition: '', type: '' })
  await loadGraph()
}

async function publish() {
  await api.publish(current.value.id, true)
  uni.showToast({ title: '已发布，学习者那边会看到新版本', icon: 'none' })
  await load(current.value.id)
}

async function regenerate() {
  const res = await api.regenerate(bookId.value)
  await load(res.graph.id)
  diff.value = res.diffFromPublished
}

async function showDiff() {
  diff.value = await api.diff(current.value.id, previous.value.id)
}

onLoad(async (q) => {
  bookId.value = q.bookId
  book.value = await api.book(q.bookId)
  sections.value = await api.sections(q.bookId)
  await load()
})
</script>

<style>
.actions { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 12rpx 0; }
.actions uni-button { margin: 0; }
.link { color: #0052d9; padding: 8rpx 0; }
.node { display: flex; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.node-body { flex: 1; margin-left: 16rpx; }
.badge { font-size: 22rpx; padding: 4rpx 10rpx; border-radius: 6rpx; background: #eee; }
.badge.pending { background: #fff4e0; color: #b56b00; }
.badge.confirmed { background: #e6f7ee; color: #2ba471; }
.badge.rejected { background: #fdecee; color: #e34d59; }
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 12rpx; margin: 10rpx 0; }
.area { width: auto; height: 160rpx; }
.add { color: #2ba471; }
.del { color: #e34d59; }
.mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); z-index: 10; display: flex; align-items: flex-end; }
.panel { background: #fff; width: 100%; max-height: 80vh; overflow-y: auto; padding: 24rpx; border-radius: 20rpx 20rpx 0 0; box-sizing: border-box; }
.sub { margin-top: 20rpx; }
.panel-head { display: flex; justify-content: space-between; align-items: center; }
.rel { padding: 12rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.source { font-size: 24rpx; color: #555; background: #f5f6f8; padding: 12rpx; margin-top: 8rpx; border-radius: 8rpx; }
</style>
