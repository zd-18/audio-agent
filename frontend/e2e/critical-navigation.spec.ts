import { expect, test, type Page, type Route } from '@playwright/test'

const failedTask = {
  taskId: '9007199254740993',
  audioFileId: '9007199254740995',
  fileName: '访谈录音.wav',
  status: 'FAILED',
  statusLabel: '未完成',
  currentStage: 'AUDIO_PARSING',
  currentStageLabel: '音频解析',
  progressPercent: 13,
  currentActivity: '文件解析失败，请确认音频文件是否完整。',
  requiresUserAction: true,
  failureReason: '文件解析失败，请确认音频文件是否完整。',
  stages: [
    { code: 'FILE_UPLOAD', label: '文件上传', status: 'COMPLETED', progressPercent: 100, description: '已完成' },
    { code: 'AUDIO_PARSING', label: '音频解析', status: 'FAILED', progressPercent: 0, description: '解析失败' },
    { code: 'INTELLIGENT_ANALYSIS', label: '智能分析', status: 'PENDING', progressPercent: 0, description: '尚未开始' },
  ],
  nextActions: [],
  completedOperations: ['文件上传'],
  resultPath: null,
  createdAt: '2026-08-13T10:00:00',
  completedAt: null,
}

function success(data: unknown) {
  return { code: 0, message: 'success', data, requestId: 'playwright-e2e' }
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(success(data)),
  })
}

async function mockAuthenticatedWorkspace(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('audioagent-auth-token', 'playwright-token')
  })
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    if (url.pathname === '/api/auth/me') {
      await fulfill(route, {
        id: '9007199254740001',
        username: 'e2e-user',
        displayName: '端到端测试用户',
        avatarUrl: null,
      })
      return
    }
    if (url.pathname === '/api/settings/me') {
      await fulfill(route, {})
      return
    }
    if (url.pathname === '/api/user-tasks') {
      await fulfill(route, { records: [failedTask], current: 1, size: 100, total: 1, pages: 1 })
      return
    }
    if (url.pathname === '/api/v1/files' || url.pathname === '/api/v1/files/recycle-bin') {
      await fulfill(route, { records: [], current: 1, size: 10, total: 0, pages: 0 })
      return
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(success(null)) })
  })
}

test('未登录访问异常任务深层地址时跳转登录页', async ({ page }) => {
  await page.goto('/tasks?view=exceptions')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: '登录工作区' })).toBeVisible()
})

test('已登录用户可以查看异常任务并进入音频回收站', async ({ page }) => {
  await mockAuthenticatedWorkspace(page)
  await page.goto('/tasks?view=exceptions')

  await expect(page.getByRole('button', { name: /查看全部任务/ })).toBeVisible()
  await expect(page.getByText('访谈录音.wav')).toBeVisible()

  await page.getByRole('menuitem', { name: /音频文件/ }).click()
  await expect(page.getByRole('heading', { name: '音频文件', level: 1 })).toBeVisible()
  await page.getByRole('button', { name: '回收站' }).click()

  await expect(page).toHaveURL(/\/audio\/files\?scope=trash$/)
  await expect(page.getByRole('heading', { name: '音频回收站', level: 2 })).toBeVisible()
  await expect(page.getByText('回收站为空')).toBeVisible()
})
