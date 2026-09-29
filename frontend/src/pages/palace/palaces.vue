<template>
  <view>
    <view class="card intro" @click="go('/pages/palace/demo')">
      <view class="title">第一次用？先花 3 分钟体验一下</view>
      <view class="muted">用一座示例宫殿记 10 样东西，看看是不是真的记得住</view>
    </view>

    <view class="card">
      <view class="title">建一座新宫殿</view>
      <view class="muted">选一个你闭着眼都能走一遍的地方：家、学校、每天上班的路</view>
      <input class="input" v-model="name" placeholder="给宫殿起个名字，例如：我家" />
      <input class="input" v-model="place" placeholder="是什么地方，例如：从大门到阳台" />
      <picker v-if="graphs.length" :range="graphs" range-key="label" @change="(e) => (graphIndex = e.detail.value)">
        <view class="picker">要记哪本书的内容：{{ graphs[graphIndex].label }}</view>
      </picker>
      <button type="primary" size="mini" @click="create">开始建</button>
    </view>

    <view class="card">
      <view class="title">我的宫殿</view>
      <view v-if="!list.length" class="muted">还没有宫殿</view>
      <view v-for="p in list" :key="p.id" class="item" @click="go(`/pages/palace/palace?id=${p.id}`)">
        <view>{{ p.name }}<text class="due" v-if="p.due">该重走了</text></view>
        <view class="muted">{{ statusLabel[p.status] }} · {{ p.loci }} 个地点<text v-if="p.placed"> · 放了 {{ p.placed }} 样</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, currentUserId } from '../../api/client'

const statusLabel = { BUILDING: '在定地点', DRILLING: '在走熟路线', READY: '已建成' }

const name = ref('')
const place = ref('')
const graphs = ref([])
const graphIndex = ref(0)
const list = ref([])

function go(url) {
  uni.navigateTo({ url })
}

async function create() {
  if (!name.value) {
    uni.showToast({ title: '给宫殿起个名字', icon: 'none' })
    return
  }
  const g = graphs.value[graphIndex.value]
  const p = await api.createPalace({ name: name.value, place: place.value, graphId: g ? g.id : null })
  name.value = ''
  place.value = ''
  go(`/pages/palace/palace?id=${p.id}`)
}

onLoad(async (q) => {
  const [gs, books] = await Promise.all([api.learnerGraphs(currentUserId()), api.books()])
  const titles = Object.fromEntries(books.map((b) => [b.id, b.title]))
  graphs.value = gs.map((g) => ({ id: g.id, label: titles[g.bookId] || `图谱 ${g.id}` }))
  if (q.graphId) {
    const i = graphs.value.findIndex((g) => String(g.id) === String(q.graphId))
    if (i >= 0) graphIndex.value = i
  }
})

onShow(async () => {
  list.value = await api.palaces()
})
</script>

<style>
.intro { background: #f0f6ff; }
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 14rpx; margin: 14rpx 0; }
.picker { padding: 14rpx 0; color: #0052d9; }
.item { padding: 18rpx 0; border-bottom: 1rpx solid #eee; }
.due { font-size: 22rpx; color: #fff; background: #e37318; padding: 2rpx 10rpx; border-radius: 6rpx; margin-left: 12rpx; }
</style>
