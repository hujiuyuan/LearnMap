// 后端地址：H5 开发时走 vite 代理（留空即可）；小程序 / App 需要配置 VITE_API_BASE
const BASE = import.meta.env.VITE_API_BASE || ''

const USER_KEY = 'learnmap.userId'

export function currentUserId() {
  return uni.getStorageSync(USER_KEY) || null
}

export function setCurrentUserId(id) {
  uni.setStorageSync(USER_KEY, id)
}

// 登录还没做：开发阶段用请求头 X-User-Id 表示当前用户
export function request(method, url, data) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE + url,
      method,
      data,
      header: { 'X-User-Id': currentUserId() || '' },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          const msg = (res.data && res.data.detail) || `请求失败（${res.statusCode}）`
          uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '连不上服务器', icon: 'none' })
        reject(err)
      },
    })
  })
}

export const api = {
  me: () => request('GET', '/api/me'),
  books: () => request('GET', '/api/books'),
  sections: (bookId) => request('GET', `/api/books/${bookId}/sections`),
  assign: (bookId, learnerId) => request('POST', `/api/books/${bookId}/assign${learnerId ? `?learnerId=${learnerId}` : ''}`),
  learnerGraphs: (learnerId) => request('GET', `/api/learners/${learnerId}/graphs`),
  graph: (graphId) => request('GET', `/api/graphs/${graphId}`),
  questions: (graphId, nodeId) => request('GET', `/api/graphs/${graphId}/nodes/${nodeId}/questions`),
  answer: (body) => request('POST', '/api/answers', body),
  startDiagnostic: (graphId) => request('POST', `/api/graphs/${graphId}/diagnostics`),
  answerDiagnostic: (sessionId, itemId, body) => request('POST', `/api/diagnostics/${sessionId}/items/${itemId}/answer`, body),
  diagnosticReport: (sessionId) => request('GET', `/api/diagnostics/${sessionId}/report`),
  dueCards: () => request('GET', '/api/cards/due'),
  reviewCard: (cardId, rating) => request('POST', `/api/cards/${cardId}/review`, { rating }),
  startPractice: (graphId, mode, targetNodeId) => request('POST', `/api/graphs/${graphId}/practice`, { mode, targetNodeId }),
  answerPractice: (sessionId, itemId, body) => request('POST', `/api/practice/${sessionId}/items/${itemId}/answer`, body),
  feynman: (graphId, nodeId, body) => request('POST', `/api/graphs/${graphId}/nodes/${nodeId}/feynman`, body),
  dueRetests: () => request('GET', '/api/retests/due'),
  answerRetest: (id, answer) => request('POST', `/api/retests/${id}/answer`, { answer }),
  createGoal: (body) => request('POST', '/api/goals', body),
  dashboard: () => request('GET', '/api/tutor/dashboard'),
  profile: (learnerId) => request('GET', `/api/learners/${learnerId}/profile`),
  highlights: (learnerId) => request('GET', `/api/learners/${learnerId}/highlights`),
  trace: (learnerId, nodeId) => request('GET', `/api/learners/${learnerId}/nodes/${nodeId}/trace`),
  flag: (learnerId, nodeId, kind, note) => request('POST', `/api/learners/${learnerId}/nodes/${nodeId}/flags`, { kind, note }),
  flags: (learnerId) => request('GET', `/api/learners/${learnerId}/flags`),
  resolveFlag: (flagId) => request('POST', `/api/flags/${flagId}/resolve`),
  feynmanReviews: () => request('GET', '/api/tutor/feynman-reviews'),
  reviewFeynman: (attemptId, score, note) => request('POST', `/api/feynman/${attemptId}/review`, { score, note }),
  opsDiagnosisFlags: () => request('GET', '/api/ops/diagnosis-flags'),
  plan: (learnerId) => request('GET', `/api/learners/${learnerId}/plan`),
  replan: (learnerId) => request('POST', `/api/learners/${learnerId}/plan/replan`),
  markTask: (taskId, status) => request('POST', `/api/plan/tasks/${taskId}`, { status }),
  startPomodoro: (taskId, minutes) => request('POST', '/api/pomodoros', { taskId, minutes }),
  finishPomodoro: (id, completed, interruptions) => request('POST', `/api/pomodoros/${id}/finish`, { completed, interruptions }),
}

export function parseOptions(optionsJson) {
  try {
    return optionsJson ? JSON.parse(optionsJson) : []
  } catch (e) {
    return []
  }
}

// 节点状态（产品方案 2.6）：绿 已掌握 / 黄 学习中 / 红 疑似薄弱 / 虚线 数据不足 / 灰 未学
export function nodeStatus(node) {
  if (node.mastery == null) return { key: 'new', label: '未学' }
  if (node.confidence < 0.3) return { key: 'unsure', label: '数据不足' }
  if (node.mastery >= 0.8) return { key: 'good', label: '已掌握' }
  if (node.mastery < 0.5) return { key: 'weak', label: '疑似薄弱' }
  return { key: 'learning', label: '学习中' }
}
