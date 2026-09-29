<template>
  <view>
    <view class="card" v-if="!cards.length">
      <view class="muted">名下还没有学习者</view>
    </view>
    <view class="card" v-if="heat && heat.columns.length">
      <view class="title">班级热力图</view>
      <scroll-view scroll-x>
        <view class="grid">
          <view class="grid-row">
            <view class="grid-name"></view>
            <view v-for="c in heat.columns" :key="c" class="grid-cell head">{{ c }}</view>
          </view>
          <view class="grid-row" v-for="r in heat.rows" :key="r.learnerId">
            <view class="grid-name">{{ r.name }}</view>
            <view v-for="(v, i) in r.values" :key="i" class="grid-cell" :style="{ background: color(v) }">{{ v == null ? '' : pct(v) }}</view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="card" v-for="c in cards" :key="c.learnerId" @click="open(c)">
      <view class="title">{{ c.name }}</view>
      <view class="stats">
        <view><text class="num">{{ c.lit }}/{{ c.total }}</text><text class="muted"> 已点亮</text></view>
        <view><text class="num">{{ c.weak }}</text><text class="muted"> 疑似薄弱</text></view>
        <view><text class="num">{{ c.streak }}</text><text class="muted"> 天连续</text></view>
        <view><text class="num">{{ pct(c.planCompletionRate) }}</text><text class="muted"> 计划完成</text></view>
      </view>
      <view class="muted">最近学习：{{ c.lastActiveAt ? c.lastActiveAt.slice(0, 16).replace('T', ' ') : '还没开始' }}</view>
      <view v-for="a in c.alerts" :key="a" class="alert">{{ a }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const cards = ref([])
const heat = ref(null)

// 红 → 黄 → 绿
function color(v) {
  if (v == null) return '#f5f6f8'
  if (v >= 0.8) return '#b7e4c7'
  if (v >= 0.5) return '#ffe8a3'
  return '#f8c4c8'
}

function pct(v) {
  return v == null ? '—' : `${Math.round(v * 100)}%`
}

function open(c) {
  uni.navigateTo({ url: `/pages/learner/learner?learnerId=${c.learnerId}&name=${encodeURIComponent(c.name)}` })
}

onShow(async () => {
  cards.value = await api.dashboard()
  heat.value = await api.heatmap()
})
</script>

<style>
.grid-row {
  display: flex;
}
.grid-name {
  width: 120rpx;
  flex-shrink: 0;
  padding: 10rpx 0;
}
.grid-cell {
  width: 160rpx;
  flex-shrink: 0;
  padding: 10rpx;
  margin: 2rpx;
  font-size: 24rpx;
  text-align: center;
  box-sizing: border-box;
}
.grid-cell.head {
  font-size: 22rpx;
  color: #666;
}
.stats {
  display: flex;
  justify-content: space-between;
  margin: 12rpx 0;
}
.num {
  font-size: 34rpx;
  font-weight: 600;
}
.alert {
  color: #d54941;
  margin-top: 8rpx;
}
</style>
