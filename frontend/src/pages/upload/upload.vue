<template>
  <view>
    <view class="card">
      <view class="title">上传一本书</view>
      <view class="muted">支持 EPUB、PDF（文字版或扫描版）、Word（.docx）、Markdown / TXT，也可以一次选多张拍照的书页，按选的顺序拼成一本书。</view>
      <input class="input" v-model="title" placeholder="书名" />
      <input class="input" v-model="author" placeholder="作者（可不填）" />
      <view class="row">
        <button size="mini" @click="pickFiles">选文件</button>
        <button size="mini" @click="pickPhotos">拍照 / 选书页照片</button>
      </view>
      <view v-for="(f, i) in files" :key="i" class="file">
        <text>{{ i + 1 }}. {{ f.name }}</text>
        <text class="link" @click="files.splice(i, 1)">移除</text>
      </view>
      <view v-if="me && me.role !== 'LEARNER'" class="row">
        <text class="muted">生成图谱后</text>
        <button size="mini" :type="reviewMode === 'NONE' ? 'primary' : 'default'" @click="reviewMode = 'NONE'">直接用</button>
        <button size="mini" :type="reviewMode === 'REVIEW_BEFORE_PUBLISH' ? 'primary' : 'default'" @click="reviewMode = 'REVIEW_BEFORE_PUBLISH'">我先审一遍</button>
      </view>
      <view class="muted" v-if="hasImages">照片和扫描版需要 AI 识别文字，接入真实 AI 模型之前会提示不支持。</view>
      <button type="primary" :disabled="busy || !files.length" @click="submit">{{ busy ? '正在生成图谱…' : '上传并生成图谱' }}</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api, uploadBook } from '../../api/client'

const me = ref(null)
const title = ref('')
const author = ref('')
const files = ref([])
const reviewMode = ref('NONE')
const busy = ref(false)

const hasImages = computed(() => files.value.some((f) => /\.(png|jpe?g|webp|pdf)$/i.test(f.name)))

function add(tempFiles) {
  for (const f of tempFiles) {
    const name = f.name || f.path.split('/').pop()
    files.value.push({ path: f.path, name, file: typeof File !== 'undefined' && f instanceof File ? f : f.file })
    if (!title.value && files.value.length === 1) {
      title.value = name.replace(/\.[^.]+$/, '')
    }
  }
}

function pickFiles() {
  // #ifdef H5
  uni.chooseFile({
    count: 20,
    extension: ['.epub', '.pdf', '.docx', '.md', '.txt', '.zip', '.png', '.jpg', '.jpeg', '.webp'],
    success: (res) => add(res.tempFiles),
  })
  // #endif
  // #ifdef MP-WEIXIN
  wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => add(res.tempFiles) })
  // #endif
  // #ifdef APP-PLUS
  uni.chooseFile({ count: 20, success: (res) => add(res.tempFiles) })
  // #endif
}

function pickPhotos() {
  uni.chooseImage({
    count: 20,
    sourceType: ['camera', 'album'],
    success: (res) => add(res.tempFiles.map((f, i) => ({ path: f.path, name: f.name || `第${files.value.length + i + 1}页.jpg`, file: f }))),
  })
}

async function submit() {
  if (!title.value) {
    uni.showToast({ title: '请填写书名', icon: 'none' })
    return
  }
  busy.value = true
  try {
    const form = { title: title.value }
    if (author.value) form.author = author.value
    if (me.value.role !== 'LEARNER') form.reviewMode = reviewMode.value
    const res = await uploadBook(files.value, form)
    uni.showToast({ title: '图谱已生成', icon: 'success' })
    if (me.value.role === 'LEARNER') {
      const graph = await api.assign(res.book.id)
      uni.redirectTo({ url: `/pages/graph/graph?graphId=${graph.id}&bookId=${res.book.id}` })
    } else {
      uni.redirectTo({ url: `/pages/editor/editor?bookId=${res.book.id}` })
    }
  } finally {
    busy.value = false
  }
}

onLoad(async () => {
  me.value = await api.me()
})
</script>

<style>
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; margin: 16rpx 0; }
.row { display: flex; gap: 12rpx; margin: 16rpx 0; align-items: center; flex-wrap: wrap; }
.file { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; }
.link { color: #0052d9; }
</style>
