<template>
  <view>
    <view class="card">
      <view class="title">{{ nodeName ? `「${nodeName}」的笔记` : '我的笔记' }}</view>
      <view class="muted">笔记挂在知识点上：康奈尔笔记的线索会变成复习卡片，卡片盒用 [[标题]] 把想法连起来，内容地图统领一章。</view>
      <view class="tabs">
        <text v-for="t in tabs" :key="t.key" :class="['tab', kind === t.key && 'on']" @click="switchKind(t.key)">{{ t.label }}</text>
      </view>
      <view class="row" v-if="!readonly">
        <button size="mini" type="primary" @click="create('ZETTEL')">写一张卡片</button>
        <button size="mini" @click="create('CORNELL')">康奈尔笔记</button>
      </view>
    </view>

    <view class="card" v-if="!readonly && graphId && chapters.length">
      <view class="title">内容地图（MOC）</view>
      <view class="muted">按一章的知识点和你挂在上面的笔记生成初稿，你再改成自己的</view>
      <view class="row">
        <button v-for="c in chapters" :key="c.id" size="mini" @click="openMoc(c.id)">{{ c.title }}</button>
      </view>
    </view>

    <view class="card">
      <view v-if="!list.length" class="muted">还没有笔记</view>
      <view v-for="n in list" :key="n.id" class="item" @click="open(n)">
        <view>
          <text class="badge">{{ kindLabel[n.kind] }}</text>
          <text>{{ n.title }}</text>
        </view>
        <view class="muted" v-if="n.excerpt">{{ n.excerpt }}</view>
        <view class="muted">
          <text v-if="n.nodeNames.length">{{ n.nodeNames.join('、') }}</text>
          <text v-if="n.links"> · {{ n.links }} 个链接</text>
          <text v-if="n.bookTitle"> · {{ n.bookTitle }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, currentUserId } from '../../api/client'

const tabs = [
  { key: '', label: '全部' },
  { key: 'ZETTEL', label: '卡片盒' },
  { key: 'CORNELL', label: '康奈尔' },
  { key: 'MOC', label: '内容地图' },
]
const kindLabel = { ZETTEL: '卡片', CORNELL: '康奈尔', MOC: '内容地图' }

const learnerId = ref(null)
const readonly = ref(false)
const graphId = ref(null)
const bookId = ref(null)
const nodeId = ref(null)
const nodeName = ref('')
const kind = ref('')
const list = ref([])
const chapters = ref([])

async function load() {
  list.value = await api.notes(learnerId.value, { kind: kind.value, nodeId: nodeId.value })
}

function switchKind(k) {
  kind.value = k
  load()
}

function create(k) {
  const q = [`kind=${k}`]
  if (graphId.value) q.push(`graphId=${graphId.value}`)
  if (nodeId.value) q.push(`nodeId=${nodeId.value}`)
  uni.navigateTo({ url: `/pages/notes/edit?${q.join('&')}` })
}

function openMoc(sectionId) {
  uni.navigateTo({ url: `/pages/notes/edit?kind=MOC&graphId=${graphId.value}&sectionId=${sectionId}` })
}

function open(n) {
  uni.navigateTo({ url: `/pages/notes/edit?id=${n.id}${graphId.value ? `&graphId=${graphId.value}` : ''}` })
}

onLoad(async (q) => {
  learnerId.value = q.learnerId || currentUserId()
  readonly.value = String(learnerId.value) !== String(currentUserId())
  graphId.value = q.graphId || null
  bookId.value = q.bookId || null
  nodeId.value = q.nodeId || null
  nodeName.value = q.nodeName ? decodeURIComponent(q.nodeName) : ''
  if (bookId.value) {
    chapters.value = (await api.sections(bookId.value)).filter((s) => !s.parentId)
  }
})

onShow(() => {
  if (learnerId.value) load()
})
</script>

<style>
.tabs { display: flex; gap: 28rpx; margin: 16rpx 0; }
.tab { color: #888; }
.tab.on { color: #0052d9; font-weight: 600; }
.row { display: flex; gap: 12rpx; margin-top: 12rpx; flex-wrap: wrap; }
.item { padding: 18rpx 0; border-bottom: 1rpx solid #eee; }
.badge { font-size: 22rpx; color: #0052d9; background: #eef4ff; padding: 2rpx 10rpx; border-radius: 6rpx; margin-right: 10rpx; }
</style>
