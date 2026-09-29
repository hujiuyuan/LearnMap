<template>
  <view v-if="ready">
    <view class="card">
      <view class="muted">{{ kindHint[kind] }}</view>
      <input class="input" v-model="title" :disabled="readonly" :placeholder="kind === 'ZETTEL' ? '这张卡片的观点（一句话当标题）' : '标题'" />

      <template v-if="kind === 'CORNELL'">
        <view class="label">线索栏：一行一个问题或关键词（会变成复习卡片）</view>
        <textarea class="area short" v-model="cues" :disabled="readonly" placeholder="例如：移项的依据是什么？" />
        <view class="label">笔记栏</view>
        <textarea class="area" v-model="body" :disabled="readonly" placeholder="上课、看书时记下的内容" />
        <view class="label">总结栏：用自己的话写几句</view>
        <textarea class="area short" v-model="summary" :disabled="readonly" placeholder="这一页讲的是……" />
      </template>
      <template v-else>
        <view class="label">{{ kind === 'MOC' ? '内容地图' : '正文（只写一个观点，用自己的话；[[另一张卡片的标题]] 可以链接过去）' }}</view>
        <textarea class="area tall" v-model="body" :disabled="readonly" maxlength="-1" />
      </template>

      <view class="label">挂在哪些知识点上<text v-if="kind === 'CORNELL'">（至少一个）</text></view>
      <view class="chips" v-if="!readonly && graphs.length > 1">
        <text v-for="g in graphs" :key="g.id" :class="['chip', 'book', String(g.id) === String(currentGraph) && 'on']" @click="pickGraph(g.id)">{{ g.label }}</text>
      </view>
      <view class="chips" v-if="graphNodes.length && !readonly">
        <text v-for="n in graphNodes" :key="n.id" :class="['chip', selected.includes(n.id) && 'on']" @click="toggle(n.id)">{{ n.name }}</text>
      </view>
      <view class="muted" v-else>{{ selectedNames || '没有挂知识点' }}</view>

      <view class="row" v-if="!readonly">
        <button size="mini" type="primary" :disabled="busy" @click="save">保存</button>
        <button size="mini" v-if="id" @click="remove">删除</button>
      </view>
    </view>

    <view class="card" v-if="note && kind === 'CORNELL'">
      <view class="title">线索卡片</view>
      <view class="muted">线索栏的 {{ note.cueCards }} 个问题已经放进“今日复习”，先回想再翻面对照笔记。</view>
      <view class="row" v-if="!readonly">
        <button size="mini" :disabled="busy" @click="feedback">让 AI 看看我的总结</button>
      </view>
      <view v-if="note.feedback" class="feedback">
        <view v-if="note.feedback.covered.length"><text class="ok">讲到了：</text>{{ note.feedback.covered.join('；') }}</view>
        <view v-if="note.feedback.missing.length"><text class="warn">还可以补上：</text>{{ note.feedback.missing.join('；') }}</view>
        <view v-if="note.feedback.incorrect.length"><text class="bad">可能不对：</text>{{ note.feedback.incorrect.join('；') }}</view>
        <view v-if="note.feedback.followUps.length" class="muted">想一想：{{ note.feedback.followUps.join(' ') }}</view>
        <view class="muted">笔记不打分，这些只是帮你查漏。</view>
      </view>
    </view>

    <view class="card" v-if="note">
      <view class="title">链接</view>
      <view v-if="!note.links.length && !note.unresolved.length" class="muted">还没有和别的笔记连起来</view>
      <view v-for="l in note.links" :key="l.noteId" class="link-row">
        <text class="link" @click="openNote(l.noteId)">{{ l.outgoing ? '→' : '←' }} {{ l.title }}</text>
        <text class="muted" v-if="l.crossBook"> · 跨书：{{ l.bookTitle }}</text>
        <text class="muted remove" v-if="!readonly && l.source !== 'WIKI'" @click="unlink(l.noteId)">断开</text>
      </view>
      <view v-for="t in note.unresolved" :key="t" class="link-row">
        <text class="muted">[[{{ t }}]] 还没有这张卡片 </text>
        <text class="link" v-if="!readonly" @click="createLinked(t)">现在写</text>
      </view>
      <view class="row" v-if="!readonly && kind === 'ZETTEL'">
        <button size="mini" :disabled="busy" @click="suggest">找找相关的卡片</button>
      </view>
      <view v-for="s in suggestions" :key="s.noteId" class="link-row">
        <text>{{ s.title }}</text>
        <text class="muted" v-if="s.crossBook"> · 另一本书：{{ s.bookTitle }}</text>
        <text class="link remove" @click="link(s.noteId)">连起来</text>
      </view>
      <view class="muted" v-if="suggested && !suggestions.length">没找到明显相关的卡片</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api, currentUserId } from '../../api/client'

const kindHint = {
  ZETTEL: '卡片盒：一张卡片只写一个观点，用自己的话写',
  CORNELL: '康奈尔笔记：线索栏写问题，笔记栏记内容，总结栏用自己的话概括',
  MOC: '内容地图：一章的索引页，按自己的理解调整',
}

const ready = ref(false)
const id = ref(null)
const kind = ref('ZETTEL')
const title = ref('')
const cues = ref('')
const body = ref('')
const summary = ref('')
const sectionId = ref(null)
const selected = ref([])
const graphNodes = ref([])
const note = ref(null)
const readonly = ref(false)
const busy = ref(false)
const suggestions = ref([])
const suggested = ref(false)
let graphId = null
const graphs = ref([])
const currentGraph = ref(null)

const selectedNames = computed(() => (note.value ? note.value.nodes.map((n) => n.name).join('、') : ''))

function toggle(nid) {
  const i = selected.value.indexOf(nid)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(nid)
}

function fill(n) {
  note.value = n
  id.value = n.id
  kind.value = n.kind
  title.value = n.title
  cues.value = n.cues || ''
  body.value = n.body || ''
  summary.value = n.summary || ''
  sectionId.value = n.sectionId
  selected.value = n.nodes.map((x) => x.id)
  readonly.value = String(n.ownerId) !== String(currentUserId())
}

async function loadNodes() {
  const [gs, books] = await Promise.all([api.learnerGraphs(currentUserId()), api.books()])
  const titles = Object.fromEntries(books.map((b) => [b.id, b.title]))
  graphs.value = gs.map((g) => ({ id: g.id, label: titles[g.bookId] || `图谱 ${g.id}` }))
  if (!graphId && graphs.value.length) graphId = graphs.value[0].id
  if (!graphId) return
  currentGraph.value = graphId
  const g = await api.graph(graphId)
  graphNodes.value = g.nodes
}

async function pickGraph(gid) {
  graphId = gid
  currentGraph.value = gid
  graphNodes.value = (await api.graph(gid)).nodes
}

async function save() {
  busy.value = true
  try {
    const payload = { kind: kind.value, title: title.value, cues: cues.value, body: body.value, summary: summary.value, nodeIds: selected.value, sectionId: sectionId.value }
    const n = id.value ? await api.updateNote(id.value, payload) : await api.createNote(payload)
    fill(n)
    uni.showToast({ title: '已保存', icon: 'success' })
  } finally {
    busy.value = false
  }
}

async function remove() {
  const res = await new Promise((resolve) => uni.showModal({ title: '删除这篇笔记？', content: '线索卡片和链接也会一起删掉', success: resolve }))
  if (!res.confirm) return
  await api.deleteNote(id.value)
  uni.navigateBack()
}

async function feedback() {
  if (!id.value) return
  busy.value = true
  try {
    await save()
    const fb = await api.noteFeedback(id.value)
    note.value = { ...note.value, feedback: fb }
  } finally {
    busy.value = false
  }
}

async function suggest() {
  busy.value = true
  try {
    suggestions.value = await api.linkSuggestions(id.value)
    suggested.value = true
  } finally {
    busy.value = false
  }
}

async function link(target) {
  fill(await api.linkNote(id.value, target, true))
  suggestions.value = suggestions.value.filter((s) => s.noteId !== target)
}

async function unlink(target) {
  fill(await api.unlinkNote(id.value, target))
}

function openNote(nid) {
  uni.navigateTo({ url: `/pages/notes/edit?id=${nid}${graphId ? `&graphId=${graphId}` : ''}` })
}

function createLinked(t) {
  uni.navigateTo({ url: `/pages/notes/edit?kind=ZETTEL&title=${encodeURIComponent(t)}${graphId ? `&graphId=${graphId}` : ''}` })
}

onLoad(async (q) => {
  graphId = q.graphId || null
  if (q.id) {
    const n = await api.note(q.id)
    fill(n)
    if (!graphId && n.nodes.length) graphId = n.nodes[0].graphId
  } else {
    kind.value = q.kind || 'ZETTEL'
    if (q.title) title.value = decodeURIComponent(q.title)
    if (q.nodeId) selected.value = [Number(q.nodeId)]
    if (kind.value === 'MOC' && graphId) {
      const draft = await api.mocDraft(graphId, q.sectionId)
      title.value = draft.title
      body.value = draft.body
      selected.value = draft.nodeIds
      sectionId.value = draft.sectionId
    }
  }
  if (!readonly.value) await loadNodes()
  ready.value = true
})
</script>

<style>
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 14rpx; margin: 16rpx 0; }
.label { font-size: 26rpx; color: #555; margin-top: 16rpx; }
.area { width: auto; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 14rpx; margin-top: 8rpx; height: 240rpx; }
.area.short { height: 140rpx; }
.area.tall { height: 520rpx; }
.chips { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 10rpx; }
.chip { font-size: 24rpx; padding: 6rpx 16rpx; border: 1rpx solid #ddd; border-radius: 30rpx; color: #555; }
.chip.on { background: #0052d9; border-color: #0052d9; color: #fff; }
.row { display: flex; gap: 12rpx; margin-top: 20rpx; flex-wrap: wrap; }
.feedback { margin-top: 16rpx; line-height: 1.7; font-size: 26rpx; }
.ok { color: #2ba471; }
.warn { color: #e37318; }
.bad { color: #d54941; }
.link-row { padding: 10rpx 0; font-size: 28rpx; }
.link { color: #0052d9; }
.remove { margin-left: 20rpx; }
</style>
