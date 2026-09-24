# 知图 LearnMap

把任何一本书变成一张可学习的知识地图，按学习者选择的学习方式生成学习计划，用测试数据持续更新学习者画像，并告诉他下一步学什么、怎么学。

产品方案见项目文件 `product-design/learnmap-product-design-v2.md`。当前在做 **Phase 1a 核心闭环**：书 → 图谱 → 诊断 → 5 种学习方式 → 计划 → 画像 → 辅导者 Dashboard，分多个 PR 逐步交付。

## 目录

| 目录 | 内容 |
|---|---|
| `backend/` | Spring Boot 3 / Java 21 模块化单体，MySQL 8（本地开发可用内存 H2），Flyway 管理表结构 |
| `frontend/` | uni-app（Vue 3），H5 / 微信小程序 / App 一套代码 |

后端按业务分包：`user`（角色、辅导者绑定）、`book`（导入、目录解析、原文块）、`graph`（书本图谱、学习者副本、修正）、`question`（题库、一题多解）、`evidence`（作答 → 证据）、`mastery`（掌握度规则引擎）、`ai`（AI 提供方接口）。

## 本地运行

需要 JDK 21、Maven 3.9、Node 18+。

```bash
# 后端：默认 dev 模式，用内存数据库，启动时自动导入演示账号和一本示例书
cd backend
mvn spring-boot:run

# 前端：H5 开发模式，/api 自动转发到 localhost:8080
cd frontend
npm install
npm run dev:h5
```

打开 http://localhost:5173 ，点“学习者 小明” → 打开示例书 → 看知识地图 → 点知识点做小测，掌握度会实时更新。

演示账号：运维 id=1、辅导者（王老师）id=2、学习者（小明）id=3。登录还没做，接口用请求头 `X-User-Id` 表示当前用户。

使用 MySQL：

```bash
docker compose up -d mysql
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

测试：`cd backend && mvn verify`。

## 现在能跑什么、什么还是占位

**已经能跑**
- 三种角色（学习者 / 辅导者 / 运维），辅导者绑定名下学习者，书的可见范围按方案 2.5 执行
- 上传 TXT / Markdown 书：按标题解析出 章 → 节 → 小节，切原文块，生成书本图谱和题目
- 校对规则：学习者上传的直接发布（标“未校对”）；辅导者 / 运维可以选“先校对再发布”
- 每个学习者一份图谱副本，互不影响；学习者可以删节点、标记“这条前置关系不对”
- 作答 → 证据 → 掌握度：一题多解按实际用的解法记分，分步作答只扣出错那一步的知识点；未校对节点 Confidence 上限 0.6
- 前端：书架、按章展示的知识地图（节点按掌握状态着色）、知识点小测

**还是占位（stub）**
- **AI**：图谱抽取、出题、费曼评分、OCR 都走 `AiProvider` 接口，目前只有离线假实现 `StubAiProvider`——每个小节抽成一个知识点、同章按顺序串前置、每个节点出一道判断题。接真实模型需要 API key，通过环境变量 `LEARNMAP_AI_PROVIDER` / `LEARNMAP_AI_API_KEY` / `LEARNMAP_AI_MODEL` 配置，不写进代码
- **登录**：用 `X-User-Id` 请求头代替
- **EPUB / PDF / Word / 图片（OCR）导入**：会直接提示“还在开发中”，计划由单独的 Python 导入流水线服务处理

**还没做（后续 PR）**
入门诊断三轮、5 种学习方式（间隔重复 + FSRS、费曼、刻意练习、交错练习、第一性原理补缺）和番茄钟、计划生成与每日重排、五张画像、辅导者 Dashboard、同一本书两次生成的差异合并、运维图谱校对页。
