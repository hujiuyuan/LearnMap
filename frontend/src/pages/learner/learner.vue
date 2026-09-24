<template>
  <view v-if="profile">
    <view class="tabs">
      <view v-for="t in tabs" :key="t.key" :class="['tab', tab === t.key ? 'on' : '']" @click="tab = t.key">{{ t.label }}</view>
    </view>

    <!-- 知识画像：每章一行热力格子，点格子看“为什么不会” -->
    <view v-if="tab === 'knowledge'">
      <view class="card">
        <view class="title">{{ k.mastered }} / {{ k.total }} 已掌握</view>
        <view class="muted">学习中 {{ k.learning }} · 疑似薄弱 {{ k.weak }} · 数据不足 {{ k.unsure }} · 未学 {{ k.untouched }}</view>
        <view class="actions">
          <button size="mini" @click="assignBook">分配书</button>
          <button size="mini" v-for="g in graphs" :key="g.id" @click="setGoal(g)">设目标：{{ bookTitles[g.bookId] || '书 ' + g.bookId }}</button>
          <button size="mini" @click="openPlan">看计划</button>
        </view>
        <view v-for="g in graphs" :key="'u' + g.id">
          <view class="update-hint" v-if="updates[g.id] && updates[g.id].available" @click="openUpdate(g)">
            「{{ bookTitles[g.bookId] || '书 ' + g.bookId }}」有新版图谱，{{ updates[g.id].canDecide ? '点这里选择怎么更新' : '由学习者本人选择' }}
          </view>
        </view>
      </view>
      <view class="card" v-for="ch in k.chapters" :key="ch.graphId + '-' + ch.chapterId">
        <view class="title">{{ ch.title }}</view>
        <view class="muted">{{ ch.mastered }}/{{ ch.nodes }} 已掌握 · 平均掌握度 {{ pct(ch.avgMastery) }}</view>
        <view class="heat">
          <view v-for="n in nodesOf(ch)" :key="n.id" :class="['cell', n.status.toLowerCase()]" @click="trace(n)">
            <text>{{ n.name }}</text>
            <text class="cell-num">{{ n.mastery == null ? '未学' : pct(n.mastery) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="tab === 'ability'">
      <view class="card">
        <view class="title">能力</view>
        <view v-if="!profile.abilities.abilities.length" class="muted">还没有带能力标注的作答</view>
        <view v-for="a in profile.abilities.abilities" :key="a.ability" class="bar-row">
          <view class="bar-label">{{ a.label }}</view>
          <view class="bar"><view class="bar-fill" :style="{ width: pct(a.accuracy) }"></view></view>
          <view class="muted">{{ pct(a.accuracy) }}（{{ a.attempts }} 题）</view>
        </view>
        <view class="bar-row" v-if="profile.abilities.expression != null">
          <view class="bar-label">表达</view>
          <view class="bar"><view class="bar-fill" :style="{ width: pct(profile.abilities.expression) }"></view></view>
          <view class="muted">{{ pct(profile.abilities.expression) }}（{{ profile.abilities.feynmanCount }} 次费曼）</view>
        </view>
      </view>
      <view class="card">
        <view class="title">自我判断准不准</view>
        <view class="muted" v-if="!cal.total">还没有答前自评的数据</view>
        <view v-else>
          <view>判断准确 {{ pct(cal.calibratedRate) }}（共 {{ cal.total }} 题）</view>
          <view class="muted">说“有把握”却做错 {{ cal.overconfident }} 次 · 说“不确定 / 不会”却做对 {{ cal.underconfident }} 次</view>
        </view>
      </view>
      <view class="card" v-if="profile.abilities.solutions.length">
        <view class="title">常用解法</view>
        <view v-for="s in profile.abilities.solutions" :key="s.name" class="muted">{{ s.name }}：做对 {{ s.correct }} 次</view>
      </view>
    </view>

    <view v-if="tab === 'errors'" class="card">
      <view class="title">错题 {{ profile.errors.wrongTotal }} 道</view>
      <view v-for="e in profile.errors.byType" :key="e.type" class="bar-row">
        <view class="bar-label">{{ e.label }}</view>
        <view class="bar"><view class="bar-fill warn" :style="{ width: pct(e.share) }"></view></view>
        <view class="muted">{{ e.count }} 次</view>
      </view>
      <view class="muted" v-if="profile.errors.unlabeled">另有 {{ profile.errors.unlabeled }} 道没标错因</view>
    </view>

    <view v-if="tab === 'modes'">
      <view class="card">
        <view class="title">建议</view>
        <view v-if="!profile.modes.suggestions.length" class="muted">数据还不够：等延时复测结果出来，就能看出哪种方式最适合</view>
        <view v-for="s in profile.modes.suggestions" :key="s">{{ s }}</view>
      </view>
      <view class="card">
        <view class="title">各方式效果</view>
        <view v-for="m in profile.modes.stats" :key="m.mode + m.nodeType" class="mode-row">
          <view>{{ m.modeLabel }} · {{ typeLabel[m.nodeType] }}</view>
          <view class="muted">
            {{ m.evidences }} 次 · {{ m.minutes }} 分钟 · 每 10 分钟 +{{ m.gainPer10Min == null ? '—' : pct(m.gainPer10Min) }}
            · 复测记得 {{ m.retentionRate == null ? '—' : pct(m.retentionRate) + '（' + m.retests + ' 次）' }}
          </view>
        </view>
      </view>
      <view class="card" v-if="profile.modes.completion.length">
        <view class="title">完成率</view>
        <view v-for="c in profile.modes.completion" :key="c.mode" class="muted">{{ c.modeLabel }}：{{ c.completed }}/{{ c.started }}（{{ pct(c.rate) }}）</view>
      </view>
    </view>

    <view v-if="tab === 'behavior'" class="card">
      <view class="title">学习习惯</view>
      <view>连续学习 {{ b.streak }} 天 · 近两周学了 {{ b.activeDays14 }} 天</view>
      <view>计划完成率 {{ pct(b.planCompletionRate) }}（完成 {{ b.tasksDone }}、跳过 {{ b.tasksSkipped }}、没做 {{ b.tasksMissed }}）</view>
      <view>番茄钟 {{ b.pomodoros }} 个，坚持完 {{ pct(b.pomodoroCompletionRate) }}，平均被打断 {{ b.avgInterruptions == null ? '—' : b.avgInterruptions }} 次</view>
      <view>近两周专注 {{ b.focusMinutes14 }} 分钟 · 常在{{ b.preferredSlot || '—' }}学习</view>
      <view v-for="n in b.notes" :key="n" class="muted">{{ n }}</view>
    </view>

    <view v-if="tab === 'todo'">
      <view class="card" v-if="!reviews.length && !openFlags.length"><view class="muted">没有要处理的事</view></view>
      <view class="card" v-for="f in openFlags" :key="'f' + f.id">
        <view class="title">{{ flagLabel[f.kind] }}：{{ f.nodeName }}</view>
        <view class="muted">{{ f.note }}</view>
        <view class="actions">
          <button size="mini" @click="trace({ id: f.nodeId })">看原因</button>
          <button size="mini" @click="resolve(f)">已处理</button>
        </view>
      </view>
      <view class="card" v-for="r in reviews" :key="'r' + r.id">
        <view class="title">复核费曼讲解：{{ r.nodeName }}</view>
        <view class="quote">{{ r.explanation }}</view>
        <view class="muted">AI 评分 {{ pct(r.score) }}<text v-if="r.missing.length"> · 讲漏：{{ r.missing.join('；') }}</text></view>
        <view class="muted" v-if="r.incorrect.length">可能讲错：{{ r.incorrect.join('；') }}</view>
        <view class="row">你的分数 <slider :value="r._score" min="0" max="100" step="10" show-value @change="(e) => (r._score = e.detail.value)" class="slider" /></view>
        <input class="note" v-model="r._note" placeholder="给学习者的意见（可不填）" />
        <button size="mini" type="primary" @click="submitReview(r)">提交复核</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const tabs = [
  { key: 'knowledge', label: '知识' },
  { key: 'ability', label: '能力' },
  { key: 'errors', label: '错误' },
  { key: 'modes', label: '学习方式' },
  { key: 'behavior', label: '习惯' },
  { key: 'todo', label: '待处理' },
]
const typeLabel = { FACT: '事实', CONCEPT: '概念', PRINCIPLE: '原理', METHOD: '方法', SYNTHESIS: '综合' }
const flagLabel = { STUCK: '补了两轮还卡住', DIAGNOSIS_WRONG: '你标了诊断不对', REALLY_WEAK: '你标了确实不会' }

const learnerId = ref(null)
const tab = ref('knowledge')
const profile = ref(null)
const graphs = ref([])
const bookTitles = ref({})
const updates = ref({})
const reviews = ref([])
const flags = ref([])

const k = computed(() => profile.value.knowledge)
const b = computed(() => profile.value.behavior)
const cal = computed(() => profile.value.abilities.calibration)
const openFlags = computed(() => flags.value.filter((f) => !f.resolved))

function pct(v) {
  return v == null ? '—' : `${Math.round(v * 100)}%`
}

function nodesOf(ch) {
  return k.value.nodes.filter((n) => n.graphId === ch.graphId && (n.chapterId || 0) === (ch.chapterId || 0))
}

function trace(n) {
  uni.navigateTo({ url: `/pages/trace/trace?learnerId=${learnerId.value}&nodeId=${n.id}` })
}

async function assignBook() {
  const books = await api.books()
  uni.showActionSheet({
    itemList: books.map((x) => x.title),
    success: async (res) => {
      await api.assign(books[res.tapIndex].id, learnerId.value)
      uni.showToast({ title: '已分配', icon: 'none' })
      load()
    },
  })
}

function setGoal(g) {
  uni.navigateTo({ url: `/pages/goal/goal?graphId=${g.id}&bookId=${g.bookId}` })
}

function openUpdate(g) {
  uni.navigateTo({ url: `/pages/update/update?graphId=${g.id}` })
}

function openPlan() {
  uni.navigateTo({ url: `/pages/plan/plan?learnerId=${learnerId.value}` })
}

async function resolve(f) {
  await api.resolveFlag(f.id)
  flags.value = await api.flags(learnerId.value)
}

async function submitReview(r) {
  await api.reviewFeynman(r.id, (r._score ?? 50) / 100, r._note || null)
  uni.showToast({ title: '已复核', icon: 'none' })
  load()
}

async function load() {
  profile.value = await api.profile(learnerId.value)
  graphs.value = await api.learnerGraphs(learnerId.value)
  bookTitles.value = Object.fromEntries((await api.books()).map((x) => [x.id, x.title]))
  const ups = {}
  for (const g of graphs.value) ups[g.id] = await api.graphUpdate(g.id)
  updates.value = ups
  flags.value = await api.flags(learnerId.value)
  reviews.value = (await api.feynmanReviews())
    .filter((r) => String(r.learnerId) === String(learnerId.value))
    .map((r) => ({ ...r, _score: Math.round(r.score * 10) * 10, _note: '' }))
}

onLoad((q) => {
  learnerId.value = q.learnerId
  uni.setNavigationBarTitle({ title: decodeURIComponent(q.name || '学习者画像') })
})
onShow(load)
</script>

<style>
.tabs {
  display: flex;
  background: #fff;
  overflow-x: auto;
}
.tab {
  padding: 20rpx 22rpx;
  white-space: nowrap;
  color: #666;
}
.tab.on {
  color: #0052d9;
  border-bottom: 4rpx solid #0052d9;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}
.actions uni-button {
  margin: 0;
}
.update-hint {
  margin-top: 12rpx;
  color: #b56b00;
}
.heat {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 12rpx;
}
.cell {
  width: calc(33% - 10rpx);
  padding: 14rpx;
  border-radius: 10rpx;
  font-size: 24rpx;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.cell-num {
  font-size: 22rpx;
  opacity: 0.8;
}
.cell.mastered { background: #2ba471; color: #fff; }
.cell.learning { background: #f5b400; }
.cell.weak { background: #e34d59; color: #fff; }
.cell.unsure { border: 3rpx dashed #8a8f99; }
.cell.untouched { background: #e7e9ee; }
.bar-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 12rpx 0;
}
.bar-label {
  width: 140rpx;
}
.bar {
  flex: 1;
  height: 20rpx;
  background: #eef0f3;
  border-radius: 10rpx;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: #0052d9;
}
.bar-fill.warn {
  background: #e34d59;
}
.mode-row {
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
.quote {
  background: #f5f6f8;
  padding: 16rpx;
  margin: 12rpx 0;
  border-radius: 8rpx;
}
.row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.slider {
  flex: 1;
}
.note {
  border: 1rpx solid #ddd;
  border-radius: 8rpx;
  padding: 12rpx;
  margin: 12rpx 0;
}
</style>
