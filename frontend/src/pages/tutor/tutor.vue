<template>
  <view>
    <view class="card" v-if="!cards.length">
      <view class="muted">名下还没有学习者</view>
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

function pct(v) {
  return v == null ? '—' : `${Math.round(v * 100)}%`
}

function open(c) {
  uni.navigateTo({ url: `/pages/learner/learner?learnerId=${c.learnerId}&name=${encodeURIComponent(c.name)}` })
}

onShow(async () => {
  cards.value = await api.dashboard()
})
</script>

<style>
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
