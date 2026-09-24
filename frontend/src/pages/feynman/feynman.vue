<template>
  <view>
    <view class="card">
      <view class="title">把「{{ name }}」讲给小学生听</view>
      <view class="muted">用自己的话讲清楚它是什么、为什么、举个例子。讲完 AI 会对照要点指出讲漏和讲错的地方。</view>
      <textarea class="area" v-model="text" :placeholder="parent ? '回答上面的追问' : '开始讲解…'" maxlength="-1" />
      <view class="row">
        <button type="primary" size="mini" :disabled="busy || !text" @click="submit">{{ parent ? '提交回答' : '提交讲解' }}</button>
        <button size="mini" :disabled="busy" @click="photo">拍手写讲解</button>
      </view>
    </view>

    <view class="card" v-if="result">
      <view class="title">得分 {{ Math.round(result.score * 100) }}</view>
      <view v-if="result.covered.length"><view class="label green">讲到了</view><view v-for="p in result.covered" :key="p">· {{ p }}</view></view>
      <view v-if="result.missing.length"><view class="label red">讲漏了</view><view v-for="p in result.missing" :key="p">· {{ p }}</view></view>
      <view v-if="result.incorrect.length"><view class="label red">讲错了</view><view v-for="p in result.incorrect" :key="p">· {{ p }}</view></view>
      <view v-if="result.followUps.length">
        <view class="label">追问</view>
        <view v-for="p in result.followUps" :key="p">· {{ p }}</view>
      </view>
      <view class="muted" v-if="result.needsReview">这次讲解会交给辅导者复核</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const graphId = ref(null)
const nodeId = ref(null)
const name = ref('')
const text = ref('')
const result = ref(null)
const parent = ref(null)
const busy = ref(false)

async function send(body) {
  busy.value = true
  try {
    const res = await api.feynman(graphId.value, nodeId.value, { ...body, parentAttemptId: parent.value })
    result.value = res
    // 有追问就接着回答追问
    parent.value = res.followUps.length ? res.id : null
    text.value = ''
  } finally {
    busy.value = false
  }
}

function submit() {
  send({ explanation: text.value })
}

function photo() {
  uni.chooseImage({
    count: 1,
    success: (r) => {
      const path = r.tempFilePaths[0]
      // H5 下读成 base64；小程序 / App 用 getFileSystemManager
      fetch(path).then((resp) => resp.blob()).then((blob) => {
        const reader = new FileReader()
        reader.onload = () => send({ imageBase64: String(reader.result).split(',')[1], imageMimeType: blob.type })
        reader.readAsDataURL(blob)
      })
    },
  })
}

onLoad((q) => {
  graphId.value = q.graphId
  nodeId.value = q.nodeId
  name.value = decodeURIComponent(q.name || '')
})
</script>

<style>
.area { width: 100%; min-height: 240rpx; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 12rpx; margin-top: 16rpx; box-sizing: border-box; }
.row { display: flex; gap: 12rpx; margin-top: 16rpx; }
.label { margin-top: 16rpx; font-weight: 600; }
.green { color: #2ba471; }
.red { color: #e34d59; }
</style>
