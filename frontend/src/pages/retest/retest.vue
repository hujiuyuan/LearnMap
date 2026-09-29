<template>
  <view>
    <view class="card" v-if="current">
      <view class="muted">{{ reasonLabel[current.reason] }} · 剩余 {{ list.length }} 个</view>
      <QuestionCard :question="current" :disabled="busy" @answer="submit" />
      <view v-if="last" :class="last.correct ? 'ok' : 'bad'">{{ last.correct ? '还记得' : '忘了，正确答案：' + last.expectedAnswer }}</view>
    </view>
    <view class="card" v-else-if="loaded">
      <view class="title">复测做完了</view>
      <view class="muted">这些结果会用来比较哪种学习方式对你最有效</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'
import QuestionCard from '../../components/QuestionCard.vue'

const reasonLabel = { RETENTION_3D: '学完 3 天', RETENTION_7D: '学完 7 天', RETENTION_21D: '学完 21 天', DELIBERATE_RECHECK: '刻意练习复验' }
const list = ref([])
const last = ref(null)
const busy = ref(false)
const loaded = ref(false)
const current = computed(() => list.value[0])

async function submit(answer) {
  busy.value = true
  try {
    last.value = await api.answerRetest(current.value.id, answer)
    list.value.shift()
  } finally {
    busy.value = false
  }
}

onLoad(async () => {
  list.value = await api.dueRetests()
  loaded.value = true
})
</script>

<style>
.ok { color: #2ba471; margin-top: 12rpx; }
.bad { color: #e34d59; margin-top: 12rpx; }
</style>
