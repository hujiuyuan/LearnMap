<template>
  <view>
    <view class="card">
      <view class="title">学什么</view>
      <radio-group @change="(e) => (scope = e.detail.value)">
        <label class="opt" v-for="o in scopes" :key="o.value">
          <radio :value="o.value" :checked="scope === o.value" /> {{ o.label }}
        </label>
      </radio-group>
      <checkbox-group v-if="scope === 'CHAPTERS'" @change="(e) => (chapterIds = e.detail.value)" class="chapters">
        <label class="opt" v-for="c in chapters" :key="c.id">
          <checkbox :value="String(c.id)" /> {{ c.title }}
        </label>
      </checkbox-group>
    </view>

    <view class="card">
      <view class="title">什么时候之前学完</view>
      <picker mode="date" :value="deadline" :start="today" @change="(e) => (deadline = e.detail.value)">
        <view class="picker">{{ deadline }}（还有 {{ daysLeft }} 天）</view>
      </picker>
    </view>

    <view class="card">
      <view class="title">每天能学多久（分钟）</view>
      <view class="row">工作日 <input class="num" type="number" v-model="weekdayMinutes" /></view>
      <view class="row">周末 <input class="num" type="number" v-model="weekendMinutes" /></view>
    </view>

    <view class="card">
      <view class="title">偏好</view>
      <view class="muted">主风格</view>
      <radio-group @change="(e) => (mainStyle = e.detail.value)">
        <label class="opt" v-for="o in styles" :key="o.value">
          <radio :value="o.value" :checked="mainStyle === o.value" /> {{ o.label }}
        </label>
      </radio-group>
      <view class="muted">学习节奏</view>
      <radio-group @change="(e) => (rhythm = e.detail.value)">
        <label class="opt" v-for="o in rhythms" :key="o.value">
          <radio :value="o.value" :checked="rhythm === o.value" /> {{ o.label }}
        </label>
      </radio-group>
    </view>

    <button type="primary" @click="submit">生成计划</button>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const scopes = [
  { value: 'BOOK', label: '整本书' },
  { value: 'CHAPTERS', label: '指定章节' },
  { value: 'WEAK', label: '只补薄弱点（先做过诊断或练习）' },
]
const styles = [
  { value: 'UNDERSTAND', label: '重理解：多用自己的话讲' },
  { value: 'MEMORIZE', label: '重记忆：多卡片、多小测' },
  { value: 'PRACTICE', label: '重练习：多做题' },
]
const rhythms = [
  { value: 'POMODORO', label: '番茄钟（25 分钟一块）' },
  { value: 'MICRO', label: '碎片时间（10 分钟一块）' },
  { value: 'LONG', label: '整块时间' },
]

function fmt(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const today = fmt(new Date())
const graphId = ref(null)
const chapters = ref([])
const scope = ref('BOOK')
const chapterIds = ref([])
const deadline = ref(fmt(new Date(Date.now() + 30 * 86400000)))
const weekdayMinutes = ref(30)
const weekendMinutes = ref(60)
const mainStyle = ref('UNDERSTAND')
const rhythm = ref('POMODORO')

const daysLeft = computed(() => Math.round((new Date(deadline.value) - new Date(today)) / 86400000))

async function submit() {
  const res = await api.createGoal({
    graphId: Number(graphId.value),
    scope: scope.value,
    chapterIds: chapterIds.value.map(Number),
    deadline: deadline.value,
    weekdayMinutes: Number(weekdayMinutes.value),
    weekendMinutes: Number(weekendMinutes.value),
    mainStyle: mainStyle.value,
    rhythm: rhythm.value,
  })
  uni.redirectTo({ url: `/pages/plan/plan?learnerId=${res.goal.learnerId}` })
}

onLoad(async (q) => {
  graphId.value = q.graphId
  const sections = await api.sections(q.bookId)
  chapters.value = sections.filter((s) => !s.parentId)
})
</script>

<style>
.opt {
  display: block;
  padding: 10rpx 0;
}
.chapters {
  margin-top: 12rpx;
  padding-left: 24rpx;
}
.picker {
  padding: 12rpx 0;
  color: #0052d9;
}
.row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 0;
}
.num {
  border: 1rpx solid #ddd;
  border-radius: 8rpx;
  padding: 6rpx 12rpx;
  width: 160rpx;
}
</style>
