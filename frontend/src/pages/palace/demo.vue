<template>
  <view>
    <view class="card" v-if="phase === 'look'">
      <view class="title">跟着走一遍这座“宫殿”</view>
      <view class="muted">这是一套普通的两居室。沿着路线，每个地点上放了一样东西，配一个夸张的画面。慢慢读，在脑子里“看见”它，大约 1 分钟。</view>
      <view v-for="(s, i) in stops" :key="i" class="stop">
        <view><text class="no">{{ i + 1 }}</text><text class="locus">{{ s.locus }}</text> · <text class="item">{{ s.item }}</text></view>
        <view class="image">{{ s.image }}</view>
      </view>
      <button type="primary" @click="phase = 'recall'">记好了，开始回忆</button>
    </view>

    <view class="card" v-if="phase === 'recall'">
      <view class="title">闭上眼，从门口走一遍</view>
      <view class="muted">每到一个地点，那里放着什么？</view>
      <view v-for="(s, i) in stops" :key="i" class="recall">
        <text class="locus">{{ i + 1 }}. {{ s.locus }}</text>
        <input class="input" v-model="answers[i]" placeholder="这里放着……" />
      </view>
      <button type="primary" @click="check">看看记住了几个</button>
    </view>

    <view class="card" v-if="phase === 'done'">
      <view class="title">记住了 {{ score }} / {{ stops.length }} 个</view>
      <view class="muted" v-if="score >= 8">原来真的记得住！换成你自己熟悉的地方，效果会更好。</view>
      <view class="muted" v-else>第一次能记住这么多已经不错了。换成你自己熟悉的地方、自己编画面，会记得更牢。</view>
      <view v-for="(s, i) in stops" :key="i" :class="['result', ok[i] ? 'ok' : 'bad']">{{ s.locus }}：{{ s.item }}<text v-if="!ok[i]">（你写的：{{ answers[i] || '空' }}）</text></view>
      <view class="how">
        <view class="title">原理就三条</view>
        <view>1. 地点要熟、顺序固定：大脑天生擅长记空间和路线。</view>
        <view>2. 画面要夸张、有动作、有声音：越离奇越难忘。</view>
        <view>3. 自己编的画面比别人给的记得牢：AI 的建议只是起个头。</view>
      </view>
      <button type="primary" @click="back">去建我自己的宫殿</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const stops = [
  { locus: '门口鞋柜', item: '西瓜', image: '鞋柜门一开，一个大西瓜滚出来砸到你脚上，“咚”的一声，汁水溅了一地' },
  { locus: '玄关镜子', item: '雨伞', image: '镜子里的你撑着一把巨大的雨伞，镜子外却在往你头上下雨' },
  { locus: '沙发', item: '闹钟', image: '沙发上坐着一个会跳的闹钟，你一坐下它就尖叫着弹起来' },
  { locus: '茶几', item: '牙刷', image: '茶几上插满了牙刷，像一片森林，还散发着薄荷味' },
  { locus: '电视', item: '企鹅', image: '电视里一群企鹅排队往外走，一只只跳到地板上滑冰' },
  { locus: '餐桌', item: '钥匙', image: '餐桌上摆着一大盘钥匙，你用筷子夹起一把咬了一口，咯嘣响' },
  { locus: '冰箱', item: '气球', image: '打开冰箱，几百个气球“嘭”地涌出来，把你顶到天花板' },
  { locus: '厨房水槽', item: '书包', image: '水槽里泡着一个书包，里面的课本在冒泡泡' },
  { locus: '卧室床', item: '吉他', image: '床上躺着一把吉他盖着被子，你一碰它就自己弹起了摇篮曲' },
  { locus: '阳台', item: '蜡烛', image: '阳台栏杆上点满了蜡烛，风一吹全变成了会唱歌的小火苗' },
]

const phase = ref('look')
const answers = ref(stops.map(() => ''))
const ok = ref([])
const score = ref(0)

function check() {
  ok.value = stops.map((s, i) => (answers.value[i] || '').includes(s.item))
  score.value = ok.value.filter(Boolean).length
  phase.value = 'done'
}

function back() {
  uni.navigateBack()
}
</script>

<style>
.stop { padding: 14rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.no { display: inline-block; width: 40rpx; color: #0052d9; font-weight: 600; }
.locus { font-weight: 600; }
.item { color: #e37318; }
.image { font-size: 26rpx; color: #555; margin-top: 6rpx; line-height: 1.6; }
.recall { display: flex; align-items: center; gap: 12rpx; margin: 10rpx 0; }
.recall .locus { width: 220rpx; font-size: 26rpx; }
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 10rpx; }
.result { padding: 6rpx 0; font-size: 26rpx; }
.result.ok { color: #2ba471; }
.result.bad { color: #d54941; }
.how { margin: 24rpx 0; line-height: 1.8; font-size: 26rpx; }
</style>
