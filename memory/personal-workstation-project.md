---
name: personal-workstation-project
description: 个人工作台项目：笔记+任务看板+开发者工具+数据仪表盘，Windows服务端+原生Android客户端，局域网互通
metadata:
  type: project
---

**项目概述**：双端个人工作台，Windows 做服务端 + 原生 Android App 做客户端，局域网 HTTP/WebSocket 连接。

**四大模块（全部要）**：
1. 笔记/知识管理
2. 任务/项目管理（看板）
3. 开发者工具集
4. 数据仪表盘

**技术决策**：
- Windows 端：做服务端（HTTP API + WebSocket）
- Android 端：原生 App（非网页）
- 开发策略：渐进式，先 MVP 再迭代
- 网络：局域网，Windows 为主机

**视觉设计决策**：
- 风格：极客/开发者风（数据密集、面板感、克制装饰，类似 Linear / Obsidian）
- 主题：默认深色模式
- 精细度：MVP 也要精致打磨，动效跟手

**开发中需注意**：用户偏好简单快速的方案，避免过度设计；GitHub 追踪是核心模块之一（上传频率、项目进展时间线）。
