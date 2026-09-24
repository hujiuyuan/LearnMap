<template>
  <view>
    <view class="stem">{{ question.stem }}</view>
    <view v-if="options.length" class="row">
      <button v-for="o in options" :key="o" size="mini" :disabled="disabled" @click="$emit('answer', o)">{{ o }}</button>
    </view>
    <view v-else class="row">
      <input class="input" v-model="input" placeholder="填写答案" :disabled="disabled" />
      <button size="mini" :disabled="disabled || !input" @click="submit">提交</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { parseOptions } from '../api/client'

const props = defineProps({ question: { type: Object, required: true }, disabled: Boolean })
const emit = defineEmits(['answer'])
const input = ref('')
const options = computed(() => parseOptions(props.question.optionsJson))

watch(() => props.question && props.question.id, () => { input.value = '' })

function submit() {
  emit('answer', input.value)
}
</script>

<style>
.stem { white-space: pre-wrap; line-height: 1.7; }
.row { display: flex; gap: 12rpx; margin-top: 16rpx; align-items: center; flex-wrap: wrap; }
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 8rpx 12rpx; }
</style>
