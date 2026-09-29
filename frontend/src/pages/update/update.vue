<template>
  <view v-if="u">
    <view class="card" v-if="!u.available">
      <view class="muted">已经是最新版本了</view>
    </view>
    <view v-else>
      <view class="card">
        <view class="title">v{{ u.diff.fromVersion }} → v{{ u.diff.toVersion }}</view>
        <view class="muted" v-if="u.diff.empty">两个版本的内容一样，选“换成新版”只会更新版本号</view>
        <view class="muted">已经有学习记录的知识点，不管怎么选都保持不变，学习数据不会丢</view>
      </view>

      <view class="card" v-if="u.diff.added.length">
        <view class="title">新增 {{ u.diff.added.length }} 个知识点</view>
        <view v-for="n in u.diff.added" :key="n.id" class="line add">+ {{ n.name }}</view>
      </view>
      <view class="card" v-if="u.diff.removed.length">
        <view class="title">新版里没有了 {{ u.diff.removed.length }} 个</view>
        <view v-for="n in u.diff.removed" :key="n.id" class="line del">− {{ n.name }}<text v-if="n.hasRecords" class="muted">（有学习记录，会保留）</text></view>
      </view>
      <view class="card" v-if="u.diff.changed.length">
        <view class="title">改动了 {{ u.diff.changed.length }} 个</view>
        <view v-for="c in u.diff.changed" :key="c.name" class="changed">
          <view>{{ c.name }} <text class="muted">改了{{ c.fields.join('、') }}<text v-if="c.hasRecords">（有学习记录，不改）</text></text></view>
          <view class="muted before">旧：{{ c.before }}</view>
          <view class="muted">新：{{ c.after }}</view>
        </view>
      </view>
      <view class="card" v-if="u.diff.addedRelations.length || u.diff.removedRelations.length">
        <view class="title">关系</view>
        <view v-for="r in u.diff.addedRelations" :key="'a' + r" class="line add">+ {{ r }}</view>
        <view v-for="r in u.diff.removedRelations" :key="'d' + r" class="line del">− {{ r }}</view>
      </view>

      <view class="card" v-if="u.canDecide">
        <view class="choice" @click="apply('MERGE')">
          <view class="title">合并（推荐）</view>
          <view class="muted">加上新版多出来的知识点和关系，旧的都留着</view>
        </view>
        <view class="choice" @click="apply('KEEP')">
          <view class="title">保留旧版</view>
          <view class="muted">什么都不变，以后也不再提示这个版本</view>
        </view>
        <view class="choice" @click="apply('REPLACE')">
          <view class="title">换成新版</view>
          <view class="muted">按新版更新，新版里没有的知识点删掉（有学习记录的除外）</view>
        </view>
      </view>
      <view class="card" v-else>
        <view class="muted">这本书的更新由{{ u.decider }}来选择</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api/client'

const u = ref(null)
const graphId = ref(null)

async function apply(decision) {
  const res = await api.applyUpdate(graphId.value, decision)
  uni.showModal({ title: '已处理', content: res.summary, showCancel: false, success: () => uni.navigateBack() })
}

onLoad(async (q) => {
  graphId.value = q.graphId
  u.value = await api.graphUpdate(q.graphId)
})
</script>

<style>
.line { padding: 8rpx 0; }
.add { color: #2ba471; }
.del { color: #e34d59; }
.changed { padding: 12rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.before { text-decoration: line-through; }
.choice { padding: 20rpx 0; border-bottom: 1rpx solid #f0f0f0; }
</style>
