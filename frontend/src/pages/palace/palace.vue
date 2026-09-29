<template>
  <view v-if="p">
    <view class="card">
      <view class="title">{{ p.name }}</view>
      <view class="muted" v-if="p.place">{{ p.place }}</view>
      <view class="steps">
        <text v-for="(s, i) in stepNames" :key="i" :class="['step', i + 2 === step && 'on', i + 2 < step && 'done']">{{ i + 2 }}. {{ s }}</text>
      </view>
    </view>

    <!-- 3. 定地点 -->
    <view class="card" v-if="mode === 'loci'">
      <view class="title">定地点：按一条固定路线，依次定下 5~10 个地点</view>
      <view class="guide">
        <view>想象你现在站在{{ p.place || '这个地方' }}的入口：</view>
        <view>· 进来第一眼看到什么？</view>
        <view>· 往左（或往右）走，下一个是什么？</view>
        <view>· 接着会经过哪里？一直走到终点。</view>
        <view class="muted">地点要是具体的东西（鞋柜、沙发），彼此不一样，顺序每次都相同。</view>
      </view>
      <view v-for="(n, i) in lociDraft" :key="i" class="locus-row">
        <text class="no">{{ i + 1 }}</text>
        <input class="input" v-model="lociDraft[i]" :placeholder="i === 0 ? '例如：门口鞋柜' : '下一个地点'" />
        <text class="muted remove" v-if="lociDraft.length > 1" @click="lociDraft.splice(i, 1)">删</text>
      </view>
      <view class="row">
        <button size="mini" @click="lociDraft.push('')" v-if="lociDraft.length < 30">再加一个地点</button>
        <button size="mini" type="primary" @click="saveLoci">定好了</button>
        <button size="mini" v-if="p.loci.length" @click="mode = ''">取消</button>
      </view>
    </view>

    <!-- 4. 走熟路线 -->
    <view class="card" v-if="mode !== 'loci' && p.status === 'DRILLING'">
      <view class="title">走熟路线：闭上眼顺着走、倒着走</view>
      <view class="muted">三项都过了，宫殿才算建成。</view>
      <view class="progress">
        <text :class="p.forwardOk ? 'ok' : ''">{{ p.forwardOk ? '✓' : '○' }} 顺着背</text>
        <text :class="p.backwardOk ? 'ok' : ''">{{ p.backwardOk ? '✓' : '○' }} 倒着背</text>
        <text :class="p.randomOk >= p.randomNeeded ? 'ok' : ''">{{ p.randomOk >= p.randomNeeded ? '✓' : '○' }} 随机问下一个（连续答对 {{ p.randomOk }}/{{ p.randomNeeded }}）</text>
      </view>
      <view class="row">
        <button size="mini" @click="peek = !peek">{{ peek ? '藏起来' : '先看一眼路线' }}</button>
      </view>
      <view v-if="peek" class="muted">{{ p.loci.map((l) => l.name).join(' → ') }}</view>

      <view class="drill" v-if="!p.forwardOk || !p.backwardOk">
        <view class="label">{{ !p.forwardOk ? '从第一个地点开始，按顺序写出来' : '从最后一个地点开始，倒着写出来' }}</view>
        <view v-for="(a, i) in seqAnswers" :key="i" class="locus-row">
          <text class="no">{{ i + 1 }}</text>
          <input class="input" v-model="seqAnswers[i]" />
          <text v-if="seqResult" :class="seqResult.each[i] ? 'ok' : 'bad'">{{ seqResult.each[i] ? '✓' : seqResult.expected[i] }}</text>
        </view>
        <button size="mini" type="primary" @click="submitSeq">提交</button>
      </view>

      <view class="drill" v-else>
        <view class="label" v-if="question">「{{ question.locus }}」的下一个地点是？</view>
        <view class="locus-row" v-if="question">
          <input class="input" v-model="nextAnswer" />
          <button size="mini" type="primary" @click="submitNext">回答</button>
        </view>
        <view v-if="nextResult" :class="nextResult.correct ? 'ok' : 'bad'">{{ nextResult.correct ? '对了' : `不对，是「${nextResult.expected[0]}」，重新数一次` }}</view>
        <button size="mini" @click="ask">{{ question ? '换一个' : nextResult ? '再问一个' : '随机问一个' }}</button>
      </view>
      <view class="row"><text class="link" @click="editLoci">改地点</text></view>
    </view>

    <!-- 5. 挂内容 -->
    <view class="card" v-if="mode !== 'loci' && p.status === 'READY' && mode !== 'recall'">
      <view class="title">挂内容：每个地点放一样要记的东西</view>
      <view class="muted">有顺序的清单、术语、公式步骤最适合放进宫殿。可以选书里的知识点，也可以自己写。</view>
      <view v-for="l in p.loci" :key="l.id" class="place-row">
        <view class="locus-name">{{ l.ordinal }}. {{ l.name }}</view>
        <picker :range="candidateNames" @change="(e) => pick(l.id, e.detail.value)">
          <view class="picker">{{ drafts[l.id] && drafts[l.id].nodeName ? drafts[l.id].nodeName : '选一个知识点' }}</view>
        </picker>
        <input class="input" v-model="drafts[l.id].content" placeholder="或者自己写要记的内容" />
        <view v-if="l.placement && l.placement.image" class="image-box">
          <textarea class="area" v-model="images[l.placement.id]" />
          <view class="row">
            <text class="muted">{{ l.placement.ownImage ? '你自己改的画面' : 'AI 建议的画面，改成你自己的会记得更牢' }}</text>
            <text class="link" @click="saveImage(l.placement.id)">保存画面</text>
          </view>
        </view>
      </view>
      <view class="row">
        <button size="mini" type="primary" :disabled="busy" @click="savePlacements">放好了</button>
        <button size="mini" :disabled="busy || !placedCount" @click="suggestImages">让 AI 编画面</button>
      </view>
    </view>

    <!-- 6. 回忆验证 / 7. 间隔重走 -->
    <view class="card" v-if="mode !== 'loci' && p.status === 'READY' && placedCount">
      <view class="title">{{ mode === 'recall' ? '在脑中走一遍宫殿，每个地点放着什么？' : '回忆验证' }}</view>
      <view class="muted" v-if="mode !== 'recall'">
        <text v-if="p.dueAt">下次重走：{{ fmt(p.dueAt) }}</text>
        <text v-if="p.lastRecallRate != null"> · 上次记住 {{ Math.round(p.lastRecallRate * 100) }}%</text>
      </view>
      <template v-if="mode === 'recall'">
        <view v-for="(l, i) in placedLoci" :key="l.id" class="locus-row">
          <text class="locus-name">{{ l.name }}</text>
          <input class="input" v-model="recallAnswers[i]" />
        </view>
        <button size="mini" type="primary" @click="submitRecall">走完了</button>
      </template>
      <button size="mini" type="primary" v-else @click="startRecall">开始回忆</button>
      <view v-if="recallResult" class="recall-result">
        <view class="title">记住了 {{ recallResult.correct }} / {{ recallResult.total }}</view>
        <view v-for="(it, i) in recallResult.items" :key="i" :class="['result', it.correct ? 'ok' : 'bad']">
          {{ it.locus }}：{{ it.expected }}<text v-if="!it.correct">（你写的：{{ it.answer || '空' }}）</text>
          <view v-if="!it.correct && it.image" class="muted">画面：{{ it.image }}</view>
        </view>
        <view class="muted">下次重走：{{ fmt(recallResult.nextWalkAt) }}</view>
      </view>
    </view>

    <view class="card" v-if="mode !== 'loci' && p.status === 'READY'">
      <view class="row">
        <text class="link" @click="clear">清空内容（宫殿留着下次用）</text>
        <text class="link" @click="editLoci">重新定地点</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const stepNames = ['选地方', '定地点', '走熟路线', '挂内容', '回忆验证', '间隔重走']

const id = ref(null)
const p = ref(null)
const mode = ref('')
const lociDraft = ref(['', '', '', '', ''])
const peek = ref(false)
const seqAnswers = ref([])
const seqResult = ref(null)
const question = ref(null)
const nextAnswer = ref('')
const nextResult = ref(null)
const candidates = ref([])
const drafts = reactive({})
const images = reactive({})
const recallAnswers = ref([])
const recallResult = ref(null)
const busy = ref(false)

const step = computed(() => {
  if (!p.value) return 2
  if (mode.value === 'loci' || p.value.status === 'BUILDING') return 3
  if (p.value.status === 'DRILLING') return 4
  if (!placedCount.value) return 5
  return mode.value === 'recall' || p.value.lastRecallRate == null ? 6 : 7
})
const placedLoci = computed(() => (p.value ? p.value.loci.filter((l) => l.placement) : []))
const placedCount = computed(() => placedLoci.value.length)
const candidateNames = computed(() => ['（不放知识点）', ...candidates.value.map((c) => c.name)])

function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

function apply(view) {
  p.value = view
  seqAnswers.value = view.loci.map(() => '')
  seqResult.value = null
  for (const l of view.loci) {
    drafts[l.id] = l.placement ? { nodeId: l.placement.nodeId, nodeName: l.placement.nodeName, content: l.placement.nodeId ? '' : l.placement.content } : { nodeId: null, nodeName: '', content: '' }
    if (l.placement) images[l.placement.id] = l.placement.image || ''
  }
  if (view.status === 'BUILDING') mode.value = 'loci'
}

async function load() {
  apply(await api.palace(id.value))
  if (p.value.status === 'READY' && p.value.graphId) {
    candidates.value = await api.palaceCandidates(id.value)
  }
}

function editLoci() {
  lociDraft.value = p.value.loci.length ? p.value.loci.map((l) => l.name) : ['', '', '', '', '']
  mode.value = 'loci'
}

async function saveLoci() {
  apply(await api.setLoci(id.value, lociDraft.value.map((s) => s.trim()).filter(Boolean)))
  mode.value = ''
}

async function submitSeq() {
  const res = await api.drill(id.value, { mode: p.value.forwardOk ? 'BACKWARD' : 'FORWARD', answers: seqAnswers.value })
  if (res.correct) {
    uni.showToast({ title: '全对！', icon: 'success' })
    apply(res.palace)
  } else {
    seqResult.value = res
  }
}

async function ask() {
  question.value = await api.drillNext(id.value)
  nextAnswer.value = ''
  nextResult.value = null
}

async function submitNext() {
  const res = await api.drill(id.value, { mode: 'NEXT', fromOrdinal: question.value.ordinal, answer: nextAnswer.value })
  nextResult.value = res
  apply(res.palace)
  if (res.palace.status === 'READY') {
    uni.showToast({ title: '宫殿建成了', icon: 'success' })
    await load()
  } else {
    question.value = null
  }
}

function pick(locusId, index) {
  const i = Number(index)
  drafts[locusId] = i === 0 ? { nodeId: null, nodeName: '', content: drafts[locusId].content } : { nodeId: candidates.value[i - 1].id, nodeName: candidates.value[i - 1].name, content: '' }
}

async function savePlacements() {
  const items = p.value.loci
    .filter((l) => drafts[l.id] && (drafts[l.id].nodeId || drafts[l.id].content))
    .map((l) => ({ locusId: l.id, nodeId: drafts[l.id].nodeId, content: drafts[l.id].content }))
  busy.value = true
  try {
    apply(await api.place(id.value, items))
    candidates.value = await api.palaceCandidates(id.value)
    uni.showToast({ title: '放好了', icon: 'success' })
  } finally {
    busy.value = false
  }
}

async function suggestImages() {
  busy.value = true
  try {
    apply(await api.palaceImages(id.value))
  } finally {
    busy.value = false
  }
}

async function saveImage(placementId) {
  apply(await api.editPalaceImage(placementId, images[placementId]))
  uni.showToast({ title: '已保存', icon: 'success' })
}

function startRecall() {
  recallAnswers.value = placedLoci.value.map(() => '')
  recallResult.value = null
  mode.value = 'recall'
}

async function submitRecall() {
  const res = await api.recallPalace(id.value, recallAnswers.value)
  recallResult.value = res
  apply(res.palace)
  mode.value = ''
}

async function clear() {
  const res = await new Promise((resolve) => uni.showModal({ title: '清空宫殿里的内容？', content: '地点保留，可以放新的内容', success: resolve }))
  if (!res.confirm) return
  apply(await api.clearPalace(id.value))
  candidates.value = await api.palaceCandidates(id.value)
}

onLoad((q) => {
  id.value = q.id
  load()
})
</script>

<style>
.steps { display: flex; flex-wrap: wrap; gap: 8rpx 20rpx; margin-top: 16rpx; font-size: 24rpx; color: #aaa; }
.step.on { color: #0052d9; font-weight: 600; }
.step.done { color: #2ba471; }
.guide { font-size: 26rpx; line-height: 1.8; margin: 12rpx 0; }
.locus-row { display: flex; align-items: center; gap: 12rpx; margin: 10rpx 0; }
.no { width: 40rpx; color: #0052d9; }
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 10rpx; }
.remove { padding: 0 8rpx; }
.row { display: flex; gap: 16rpx; margin-top: 16rpx; flex-wrap: wrap; align-items: center; }
.progress { display: flex; flex-direction: column; gap: 6rpx; margin: 12rpx 0; font-size: 26rpx; color: #888; }
.ok { color: #2ba471; }
.bad { color: #d54941; }
.drill { margin-top: 16rpx; }
.label { font-size: 26rpx; color: #555; margin: 8rpx 0; }
.link { color: #0052d9; font-size: 26rpx; }
.place-row { padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.locus-name { font-weight: 600; min-width: 180rpx; }
.picker { color: #0052d9; padding: 8rpx 0; }
.image-box { margin-top: 8rpx; }
.area { width: auto; height: 120rpx; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 10rpx; font-size: 26rpx; }
.recall-result { margin-top: 20rpx; }
.result { padding: 6rpx 0; font-size: 26rpx; }
</style>
