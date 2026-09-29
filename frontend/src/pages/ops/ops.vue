<template>
  <view>
    <view class="card">
      <view class="title">常被标“诊断不对”的知识点</view>
      <view class="muted">同一个知识点被多个学习者的辅导者标记，多半是题目或图谱有问题</view>
      <view v-if="!flagged.length" class="muted">还没有标记</view>
      <view v-for="f in flagged" :key="f.masterNodeId" class="row">
        <view>{{ f.nodeName }} <text class="muted">书 {{ f.bookId }}</text></view>
        <view class="muted">{{ f.learners }} 个学习者 · {{ f.flags }} 次</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const flagged = ref([])

onShow(async () => {
  flagged.value = await api.opsDiagnosisFlags()
})
</script>

<style>
.row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
</style>
