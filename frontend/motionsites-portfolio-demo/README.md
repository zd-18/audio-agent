# MotionSites Portfolio Demo

一个独立的 React + Vite 深色作品集落地页 Demo。所有内容均为模拟数据，不连接后端。

## 启动

```bash
npm install
npm run dev
```

生产构建验证：

```bash
npm run build
npm run preview
```

## 目录

```text
motionsites-portfolio-demo/
├─ src/
│  ├─ App.tsx          # 页面区块、模拟数据、HLS 与动效逻辑
│  ├─ index.css        # 设计变量、Tailwind 组件样式、响应式与动效
│  └─ main.tsx         # React 入口
├─ index.html
├─ package.json
├─ tailwind.config.js
├─ postcss.config.js
├─ tsconfig.json
├─ tsconfig.app.json
├─ tsconfig.node.json
└─ vite.config.ts
```

> 页面视频来自公开的 Mux HLS 示例流，图片来自 Unsplash。网络不可用时页面布局、颜色和渐变背景仍可正常显示。
