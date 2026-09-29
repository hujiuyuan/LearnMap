// 后端地址：H5 开发时走 vite 代理（留空即可）；小程序 / App 需要配置 VITE_API_BASE
const BASE = import.meta.env.VITE_API_BASE || ''

const TOKEN_KEY = 'learnmap.token'
const USER_KEY = 'learnmap.userId'

export function currentUserId() {
  return uni.getStorageSync(USER_KEY) || null
}

export function token() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

/** 登录 / 注册成功后保存令牌和当前用户 */
export function saveSession(session) {
  uni.setStorageSync(TOKEN_KEY, session.token)
  uni.setStorageSync(USER_KEY, session.user.id)
}

export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

function authHeader() {
  return token() ? { Authorization: `Bearer ${token()}` } : {}
}

function toLogin() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/login' })
}

function handle(res, resolve, reject) {
  let data = res.data
  // uni.uploadFile 返回的是字符串
  if (typeof data === 'string' && data) {
    try { data = JSON.parse(data) } catch (e) { /* 保持原样 */ }
  }
  if (res.statusCode >= 200 && res.statusCode < 300) {
    resolve(data)
    return
  }
  if (res.statusCode === 401) {
    toLogin()
    reject(new Error('请先登录'))
    return
  }
  const msg = (data && data.detail) || `请求失败（${res.statusCode}）`
  uni.showToast({ title: msg, icon: 'none' })
  reject(new Error(msg))
}

export function request(method, url, data) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE + url,
      method,
      data,
      header: authHeader(),
      success: (res) => handle(res, resolve, reject),
      fail: (err) => {
        uni.showToast({ title: '连不上服务器', icon: 'none' })
        reject(err)
      },
    })
  })
}

/**
 * 上传一本书。files 是 [{ path, name }]；多个文件（几张拍照的书页）一起传，后端按顺序拼成一本书。
 * H5 和 App 支持一次传多个文件；小程序一次只能传一个文件（可以先打包成 zip）。
 */
export function uploadBook(files, form) {
  return new Promise((resolve, reject) => {
    const opts = {
      url: BASE + '/api/books',
      header: authHeader(),
      formData: form,
      success: (res) => handle(res, resolve, reject),
      fail: (err) => {
        uni.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      },
    }
    if (files.length === 1) {
      opts.name = 'file'
      opts.filePath = files[0].path
    } else {
      opts.files = files.map((f) => ({ name: 'file', uri: f.path, file: f.file }))
    }
    uni.uploadFile(opts)
  })
}

export const api = {
  login: (username, password) => request('POST', '/api/auth/login', { username, password }),
  register: (body) => request('POST', '/api/auth/register', body),
  logout: () => request('POST', '/api/auth/logout'),
  changePassword: (password) => request('POST', '/api/auth/password', { password }),
  me: () => request('GET', '/api/me'),
  books: () => request('GET', '/api/books'),
  book: (bookId) => request('GET', `/api/books/${bookId}`),
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
  heatmap: () => request('GET', '/api/tutor/heatmap'),
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
  nodeDetail: (graphId, nodeId) => request('GET', `/api/graphs/${graphId}/nodes/${nodeId}`),
  masters: (bookId) => request('GET', `/api/books/${bookId}/graphs`),
  publish: (graphId, reviewed) => request('POST', `/api/graphs/${graphId}/publish?reviewed=${!!reviewed}`),
  regenerate: (bookId) => request('POST', `/api/books/${bookId}/regenerate`),
  diff: (graphId, against) => request('GET', `/api/graphs/${graphId}/diff?against=${against}`),
  editNode: (graphId, nodeId, body) => request('PATCH', `/api/editor/graphs/${graphId}/nodes/${nodeId}`, body),
  reviewNode: (graphId, nodeId, status) => request('POST', `/api/editor/graphs/${graphId}/nodes/${nodeId}/review`, { status }),
  addNode: (graphId, body) => request('POST', `/api/editor/graphs/${graphId}/nodes`, body),
  addRelation: (graphId, body) => request('POST', `/api/editor/graphs/${graphId}/relations`, body),
  reviewRelation: (graphId, relationId, status) => request('POST', `/api/editor/graphs/${graphId}/relations/${relationId}/review`, { status }),
  deleteRelation: (graphId, relationId) => request('DELETE', `/api/editor/graphs/${graphId}/relations/${relationId}`),
  graphUpdate: (graphId) => request('GET', `/api/graphs/${graphId}/update`),
  applyUpdate: (graphId, decision) => request('POST', `/api/graphs/${graphId}/update`, { decision }),
  plan: (learnerId) => request('GET', `/api/learners/${learnerId}/plan`),
  replan: (learnerId) => request('POST', `/api/learners/${learnerId}/plan/replan`),
  markTask: (taskId, status) => request('POST', `/api/plan/tasks/${taskId}`, { status }),
  startPomodoro: (taskId, minutes) => request('POST', '/api/pomodoros', { taskId, minutes }),
  finishPomodoro: (id, completed, interruptions) => request('POST', `/api/pomodoros/${id}/finish`, { completed, interruptions }),
  // 笔记
  notes: (learnerId, q = {}) => request('GET', `/api/learners/${learnerId}/notes?${query(q)}`),
  note: (id) => request('GET', `/api/notes/${id}`),
  createNote: (body) => request('POST', '/api/notes', body),
  updateNote: (id, body) => request('PUT', `/api/notes/${id}`, body),
  deleteNote: (id) => request('DELETE', `/api/notes/${id}`),
  noteFeedback: (id) => request('POST', `/api/notes/${id}/feedback`),
  linkSuggestions: (id) => request('GET', `/api/notes/${id}/link-suggestions`),
  linkNote: (id, targetId, suggested) => request('POST', `/api/notes/${id}/links/${targetId}?suggested=${!!suggested}`),
  unlinkNote: (id, targetId) => request('DELETE', `/api/notes/${id}/links/${targetId}`),
  mocDraft: (graphId, sectionId) => request('GET', `/api/graphs/${graphId}/moc-draft${sectionId ? `?sectionId=${sectionId}` : ''}`),
  // 记忆宫殿
  palaces: () => request('GET', '/api/palaces'),
  palacesDue: () => request('GET', '/api/palaces/due'),
  palace: (id) => request('GET', `/api/palaces/${id}`),
  createPalace: (body) => request('POST', '/api/palaces', body),
  deletePalace: (id) => request('DELETE', `/api/palaces/${id}`),
  setLoci: (id, names) => request('PUT', `/api/palaces/${id}/loci`, { names }),
  drillNext: (id) => request('GET', `/api/palaces/${id}/drill/next`),
  drill: (id, body) => request('POST', `/api/palaces/${id}/drill`, body),
  palaceCandidates: (id, graphId) => request('GET', `/api/palaces/${id}/candidates${graphId ? `?graphId=${graphId}` : ''}`),
  place: (id, items) => request('PUT', `/api/palaces/${id}/placements`, items),
  palaceImages: (id, regenerate) => request('POST', `/api/palaces/${id}/images?regenerate=${!!regenerate}`),
  editPalaceImage: (placementId, image) => request('PATCH', `/api/palaces/placements/${placementId}`, { image }),
  clearPalace: (id) => request('POST', `/api/palaces/${id}/clear`),
  recallPalace: (id, answers) => request('POST', `/api/palaces/${id}/recall`, { answers }),
}

function query(q) {
  return Object.entries(q).filter(([, v]) => v != null && v !== '').map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
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
