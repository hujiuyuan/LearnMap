<template>
  <view>
    <view class="card" v-if="card">
      <view class="muted">剩余 {{ remaining }} 张</view>
      <view class="front">{{ card.front }}</view>
      <view v-if="flipped" class="back">{{ card.back }}</view>
      <button v-if="!flipped" type="primary" @click="flipped = true">翻面</button>
      <view v-else class="row">
        <button v-for="r in ratings" :key="r.key" size="mini" :disabled="busy" @click="rate(r.key)">{{ r.label }}</button>
      </view>
    </view>
    <view class="card" v-else-if="loaded">
      <view class="title">今天的复习完成了</view>
      <view class="muted">系统会按遗忘曲线安排下次复习的时间</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const ratings = [
  { key: 'AGAIN', label: '忘了' },
  { key: 'HARD', label: '困难' },
  { key: 'GOOD', label: '记得' },
  { key: 'EASY', label: '轻松' },
]

const queue = ref([])
const dueCount = ref(0)
const flipped = ref(false)
const busy = ref(false)
const loaded = ref(false)

const card = computed(() => queue.value[0])
const remaining = computed(() => Math.max(dueCount.value, queue.value.length))

async function load() {
  const due = await api.dueCards()
  queue.value = due.cards
  dueCount.value = due.dueCount
  loaded.value = true
}

async function rate(rating) {
  busy.value = true
  try {
    await api.reviewCard(card.value.id, rating)
    queue.value.shift()
    dueCount.value = Math.max(0, dueCount.value - 1)
    flipped.value = false
    if (!queue.value.length && dueCount.value) await load()
  } finally {
    busy.value = false
  }
}

onLoad(load)
</script>

<style>
.front { font-size: 34rpx; margin: 30rpx 0; line-height: 1.6; }
.back { background: #f5f6f8; border-radius: 12rpx; padding: 20rpx; margin-bottom: 24rpx; line-height: 1.7; }
.row { display: flex; gap: 12rpx; justify-content: space-between; }
</style>
