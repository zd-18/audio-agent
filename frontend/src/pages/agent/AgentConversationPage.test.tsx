import { App as AntdApp } from 'antd'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createAgentConversation,
  getAgentConversations,
  getAgentMessages,
  getAgentProcessingWorkflow,
  getAgentProcessingWorkflows,
  sendAgentMessage,
} from '../../api/agent'
import { ApiError } from '../../api/http'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import type { AudioPlaybackController } from '../../hooks/useAudioPlayback'
import { useAudioFileDetail } from '../../hooks/useAudioFileDetail'
import { useTranscript } from '../../hooks/useTranscript'
import { useTranscriptionTaskPolling } from '../../hooks/useTranscriptionTaskPolling'
import type {
  AgentCitation,
  AgentConversation,
  AgentMessage,
  AgentMessagePair,
  AgentProcessingWorkflow,
} from '../../types/agent'
import type { Transcript, TranscriptionTask } from '../../types/transcription'
import AgentConversationPage from './AgentConversationPage'

vi.mock('../../api/agent', () => ({
  createAgentConversation: vi.fn(),
  getAgentConversations: vi.fn(),
  getAgentMessages: vi.fn(),
  getAgentProcessingWorkflow: vi.fn(),
  getAgentProcessingWorkflows: vi.fn(),
  sendAgentMessage: vi.fn(),
}))
vi.mock('../../api/audioFiles', () => ({ downloadAudioFile: vi.fn() }))
vi.mock('../../hooks/useTranscriptionTaskPolling', () => ({
  useTranscriptionTaskPolling: vi.fn(),
}))
vi.mock('../../hooks/useTranscript', () => ({ useTranscript: vi.fn() }))
vi.mock('../../hooks/useAudioPlayback', () => ({ useAudioPlayback: vi.fn() }))
vi.mock('../../hooks/useAudioFileDetail', () => ({ useAudioFileDetail: vi.fn() }))
vi.mock('../../components/audio/ReportAudioPlayer', () => ({
  default: () => <div aria-label="引用音频播放器">播放器</div>,
}))

const transcriptId = '2084176870403137537'
const taskId = '2084176870403137536'
const audioFileId = '2084176870403137535'
const conversationId = '2084462342950039553'
const secondConversationId = '2084462342950039554'
const clientRequestId = 'b737df46-6480-4dbc-87e7-a4be54f46286'

const task: TranscriptionTask = {
  taskId,
  audioFileId,
  audioFileName: '访谈录音.wav',
  status: 'SUCCESS',
  language: 'zh',
  enableSpeakerDiarization: false,
  progressPercent: 100,
  retryCount: 0,
  startedAt: '2026-08-04T09:00:00',
  finishedAt: '2026-08-04T09:01:00',
  createdAt: '2026-08-04T09:00:00',
  updatedAt: '2026-08-04T09:01:00',
}

const transcript: Transcript = {
  transcriptId,
  audioFileId,
  audioFileName: task.audioFileName,
  language: 'zh',
  fullText: '这是一段用于 Agent 测试的真实文字稿。',
  durationMs: 90_000,
  segmentCount: 1,
  segments: [{
    segmentId: '2084176870403137540',
    order: 1,
    segmentOrder: 1,
    startMs: 42_390,
    endMs: 48_000,
    text: '引用原文内容',
  }],
}

function conversation(id = conversationId, updatedAt = '2026-08-04T10:00:00'): AgentConversation {
  return {
    conversationId: id,
    transcriptId,
    audioFileId: null,
    title: id === conversationId ? '访谈录音.wav' : '第二个对话',
    status: 'ACTIVE',
    modelName: 'deepseek-v4-pro',
    promptVersion: 'transcript-chat-v1',
    lastMessageId: null,
    lastMessageAt: null,
    audioFileName: null,
    audioDurationMs: null,
    createdAt: updatedAt,
    updatedAt,
  }
}

function processingConversation(): AgentConversation {
  return {
    ...conversation(),
    transcriptId: null,
    audioFileId,
    title: '待处理音频.wav',
  }
}

function agentMessage(
  id: string,
  role: AgentMessage['role'],
  content: string | null,
  sequenceNo: number,
  overrides: Partial<AgentMessage> = {},
): AgentMessage {
  return {
    messageId: id,
    role,
    content,
    status: 'SUCCESS',
    sequenceNo,
    replyToMessageId: null,
    clientRequestId: null,
    modelName: null,
    promptVersion: null,
    promptTokens: null,
    completionTokens: null,
    totalTokens: null,
    failureCode: null,
    failureMessage: null,
    citations: [],
    createdAt: `2026-08-04T10:00:0${sequenceNo}`,
    finishedAt: null,
    ...overrides,
  }
}

const citation: AgentCitation = {
  citationId: '2084462342950039560',
  segmentId: '2084176870403137540',
  segmentOrder: 1,
  startMs: 42_390,
  endMs: 48_000,
  quote: '引用原文内容',
}

const secondCitation: AgentCitation = {
  citationId: '2084462342950039561',
  segmentId: '2084176870403137541',
  segmentOrder: 2,
  startMs: 60_000,
  endMs: 66_500,
  quote: '第二条引用原文',
}

function pair(content = '这是 Agent 的回答。'): AgentMessagePair {
  return {
    userMessage: agentMessage('2084462342950039570', 'USER', '新的问题', 1, { clientRequestId }),
    assistantMessage: agentMessage('2084462342950039571', 'ASSISTANT', content, 2, {
      replyToMessageId: '2084462342950039570',
      citations: [citation],
    }),
  }
}

function processingWorkflow(
  status: AgentProcessingWorkflow['status'] = 'WAITING_CONFIRMATION',
): AgentProcessingWorkflow {
  return {
    workflowId: '2084462342950039600',
    conversationId,
    userMessageId: '2084462342950039570',
    assistantMessageId: '2084462342950039571',
    taskId,
    audioFileId,
    planId: '2084462342950039601',
    confirmationId: '2084462342950039602',
    executionId: status === 'WAITING_CONFIRMATION' ? null : '2084462342950039603',
    resultFileId: null,
    status,
    summary: '压缩长静音',
    steps: [{
      order: 1,
      operationType: 'SILENCE_CLEANUP',
      title: '压缩长静音',
      reason: '停顿过长',
      startMs: null,
      endMs: null,
    }],
    progressPercent: status === 'EXECUTING' ? 0 : null,
    failureReason: null,
    createdAt: '2026-08-17T10:00:00Z',
    updatedAt: '2026-08-17T10:00:00Z',
    finishedAt: null,
  }
}

const seekTo = vi.fn()
const pause = vi.fn()
const resume = vi.fn()
const seek = vi.fn()
let playerController: AudioPlaybackController
const getConversationsMock = vi.mocked(getAgentConversations)
const getMessagesMock = vi.mocked(getAgentMessages)
const createConversationMock = vi.mocked(createAgentConversation)
const sendMessageMock = vi.mocked(sendAgentMessage)
const getWorkflowsMock = vi.mocked(getAgentProcessingWorkflows)
const getWorkflowMock = vi.mocked(getAgentProcessingWorkflow)

function player(): AudioPlaybackController {
  return {
    audioRef: { current: null },
    sourceUrl: 'https://example.test/audio.wav',
    playback: null,
    loading: false,
    refreshing: false,
    error: null,
    unsupported: false,
    currentTimeSeconds: 0,
    durationSeconds: 90,
    isPlaying: false,
    isWaiting: false,
    playbackRange: null,
    volume: 1,
    muted: false,
    playbackRate: 1,
    pause,
    resume,
    togglePlayback: vi.fn(),
    seek,
    seekTo,
    setVolume: vi.fn(),
    toggleMuted: vi.fn(),
    setPlaybackRate: vi.fn(),
    reload: vi.fn(),
  }
}

function pageElement() {
  return (
    <AntdApp>
      <MemoryRouter
        initialEntries={[`/transcriptions/${taskId}/agent`]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route path="/transcriptions/:taskId/agent" element={<AgentConversationPage />} />
        </Routes>
      </MemoryRouter>
    </AntdApp>
  )
}

function renderPage() {
  return render(pageElement())
}

function renderProcessingPage() {
  return render(
    <AntdApp>
      <MemoryRouter
        initialEntries={[`/audio/files/${audioFileId}/agent`]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route path="/audio/files/:audioFileId/agent" element={<AgentConversationPage />} />
        </Routes>
      </MemoryRouter>
    </AntdApp>,
  )
}

describe('AgentConversationPage', () => {
  beforeEach(() => {
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => clientRequestId) })
    playerController = player()
    seekTo.mockReset().mockImplementation(async (
      seconds: number,
      options?: { play?: boolean, endSeconds?: number },
    ) => {
      playerController.currentTimeSeconds = seconds
      playerController.isPlaying = Boolean(options?.play)
      playerController.playbackRange = options?.play
        && typeof options.endSeconds === 'number'
        && options.endSeconds > seconds
        ? { startSeconds: seconds, endSeconds: options.endSeconds }
        : null
    })
    pause.mockReset().mockImplementation(() => {
      playerController.isPlaying = false
    })
    resume.mockReset().mockImplementation(async () => {
      playerController.isPlaying = true
    })
    seek.mockReset().mockImplementation((seconds: number) => {
      playerController.currentTimeSeconds = seconds
      playerController.playbackRange = null
    })
    getConversationsMock.mockReset().mockResolvedValue({
      records: [conversation()], current: 1, size: 20, total: 1, pages: 1,
    })
    getMessagesMock.mockReset().mockResolvedValue({
      records: [], current: 1, size: 50, total: 0, pages: 0,
    })
    createConversationMock.mockReset().mockResolvedValue(conversation())
    sendMessageMock.mockReset().mockResolvedValue(pair())
    getWorkflowsMock.mockReset().mockResolvedValue([])
    getWorkflowMock.mockReset()
    vi.mocked(useTranscriptionTaskPolling).mockReturnValue({
      task,
      loading: false,
      refreshing: false,
      error: null,
      timedOut: false,
      refresh: vi.fn(),
      resumeWith: vi.fn(),
    })
    vi.mocked(useTranscript).mockReturnValue({
      transcript,
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
    vi.mocked(useAudioPlayback).mockImplementation(() => playerController)
    vi.mocked(useAudioFileDetail).mockReturnValue({
      data: {
        fileId: audioFileId,
        originalName: '待处理音频.wav',
        extension: 'wav',
        mimeType: 'audio/wav',
        sizeBytes: 1000,
        sha256: 'abc',
        durationMs: 90_000,
        fileStatus: 'AVAILABLE',
        createdAt: '2026-08-04T09:00:00',
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
  })

  it('creates a PROCESSING conversation with audioFileId and sends processing mode', async () => {
    getConversationsMock.mockResolvedValue({
      records: [], current: 1, size: 20, total: 0, pages: 0,
    })
    createConversationMock.mockResolvedValue(processingConversation())
    renderProcessingPage()

    expect(await screen.findByRole('heading', { name: '开始处理这段音频' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '开始新的对话' })).not.toBeInTheDocument()
    await userEvent.type(await screen.findByLabelText('描述你希望如何处理音频'), '帮我轻度降噪')
    await userEvent.click(screen.getByRole('button', { name: '发送处理请求' }))

    await waitFor(() => expect(createConversationMock).toHaveBeenCalledWith(
      { audioFileId, title: null },
      expect.any(AbortSignal),
    ))
    await waitFor(() => expect(sendMessageMock).toHaveBeenCalledWith(
      conversationId,
      { content: '帮我轻度降噪', clientRequestId, mode: 'PROCESSING' },
      expect.any(AbortSignal),
    ))
  })

  it('does not allow blank questions to be sent', async () => {
    renderPage()
    await screen.findByText('开始新的对话')

    const sendButton = screen.getByRole('button', { name: '发送问题' })
    expect(sendButton).toBeDisabled()
    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '   ')
    expect(sendButton).toBeDisabled()
    expect(sendMessageMock).not.toHaveBeenCalled()
  })

  it('creates a conversation on the first send and keeps all IDs as strings', async () => {
    getConversationsMock.mockResolvedValue({
      records: [], current: 1, size: 20, total: 0, pages: 0,
    })
    renderPage()
    await screen.findByText('还没有对话')

    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => expect(createConversationMock).toHaveBeenCalledWith(
      { transcriptId, title: null },
      expect.any(AbortSignal),
    ))
    await waitFor(() => expect(sendMessageMock).toHaveBeenCalledWith(
      conversationId,
      { content: '新的问题', clientRequestId, mode: 'CHAT' },
      expect.any(AbortSignal),
    ))
    expect(typeof createConversationMock.mock.calls[0][0].transcriptId).toBe('string')
    expect(typeof sendMessageMock.mock.calls[0][0]).toBe('string')
  })

  it('reuses an existing active conversation instead of creating another one', async () => {
    renderPage()
    await screen.findByRole('button', { name: /访谈录音\.wav/ })

    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => expect(sendMessageMock).toHaveBeenCalled())
    expect(createConversationMock).not.toHaveBeenCalled()
  })

  it('disables send and conversation switching while a request is running', async () => {
    let resolveSend!: (value: AgentMessagePair) => void
    sendMessageMock.mockReturnValue(new Promise((resolve) => { resolveSend = resolve }))
    renderPage()
    const conversationButton = await screen.findByRole('button', { name: /访谈录音\.wav/ })

    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => expect(screen.getByRole('button', { name: '正在发送问题' })).toBeDisabled())
    expect(conversationButton).toBeDisabled()
    resolveSend(pair())
    await waitFor(() => expect(screen.getByRole('button', { name: '发送问题' })).toBeInTheDocument())
    expect(conversationButton).not.toBeDisabled()
  }, 10_000)

  it('replaces optimistic messages with the successful USER and ASSISTANT response', async () => {
    renderPage()
    await screen.findByRole('button', { name: /访谈录音\.wav/ })
    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    expect(await screen.findByText('这是 Agent 的回答。')).toBeInTheDocument()
    expect(screen.getAllByText('新的问题')).toHaveLength(1)
    expect(screen.queryByText('Agent 正在分析文字稿')).not.toBeInTheDocument()
  })

  it('keeps exactly one citation active and seeks each citation range correctly', async () => {
    getMessagesMock.mockResolvedValue({
      records: [agentMessage('2084462342950039562', 'ASSISTANT', '历史回答', 2, {
        citations: [citation, secondCitation],
      })],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    })
    const { container } = renderPage()

    const firstCitationButton = await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' })
    const secondCitationButton = screen.getByRole('button', { name: '播放引用 2，01:00 到 01:06' })
    expect(within(firstCitationButton).getByText('引用原文内容')).toBeInTheDocument()
    expect(within(firstCitationButton).getByText('播放此处')).toBeInTheDocument()
    expect(within(secondCitationButton).getByText('播放此处')).toBeInTheDocument()
    expect(container.querySelectorAll('.agent-citation--active')).toHaveLength(0)

    await userEvent.click(firstCitationButton)

    expect(seekTo).toHaveBeenCalledWith(42.39, { play: true, endSeconds: 48 })
    expect(screen.getByRole('button', { name: '暂停引用 1，00:42 到 00:48' })).toHaveAttribute('aria-pressed', 'true')
    expect(container.querySelectorAll('.agent-citation--active')).toHaveLength(1)

    await userEvent.click(screen.getByRole('button', { name: '播放引用 2，01:00 到 01:06' }))

    expect(seekTo).toHaveBeenLastCalledWith(60, { play: true, endSeconds: 66.5 })
    expect(screen.getByRole('button', { name: '播放引用 1，00:42 到 00:48' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: '暂停引用 2，01:00 到 01:06' })).toHaveAttribute('aria-pressed', 'true')
    expect(container.querySelectorAll('.agent-citation--active')).toHaveLength(1)
  })

  it('pauses and resumes the active citation from the real player state', async () => {
    getMessagesMock.mockResolvedValue({
      records: [agentMessage('2084462342950039563', 'ASSISTANT', '历史回答', 2, { citations: [citation] })],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    })
    const rendered = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' }))
    playerController.currentTimeSeconds = 45
    await userEvent.click(screen.getByRole('button', { name: '暂停引用 1，00:42 到 00:48' }))
    rendered.rerender(pageElement())

    expect(pause).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '继续播放引用 1，00:42 到 00:48' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: '继续播放引用 1，00:42 到 00:48' }))
    rendered.rerender(pageElement())

    expect(resume).toHaveBeenCalledTimes(1)
    expect(seekTo).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '暂停引用 1，00:42 到 00:48' })).toBeInTheDocument()
  })

  it('clears the active citation when the real playback range ends', async () => {
    getMessagesMock.mockResolvedValue({
      records: [agentMessage('2084462342950039564', 'ASSISTANT', '历史回答', 2, { citations: [citation] })],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    })
    const rendered = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' }))
    playerController.currentTimeSeconds = 48
    playerController.isPlaying = false
    playerController.playbackRange = null
    rendered.rerender(pageElement())

    const idleCitation = await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' })
    expect(idleCitation).toHaveAttribute('aria-pressed', 'false')
    expect(idleCitation).toHaveAttribute('data-playback-state', 'idle')
  })

  it('shows historical FAILED messages as error cards without a blank bubble', async () => {
    getMessagesMock.mockResolvedValue({
      records: [agentMessage('2084462342950039562', 'ASSISTANT', null, 2, {
        status: 'FAILED',
        failureMessage: '模型服务暂时不可用',
      })],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    })
    const { container } = renderPage()

    expect(await screen.findByText('本次回答生成失败')).toBeInTheDocument()
    expect(screen.getByText('模型服务暂时不可用')).toBeInTheDocument()
    expect(container.querySelector('.agent-message--failed .agent-message__bubble')).toBeNull()
  })

  it('reuses the original clientRequestId on a manual network retry', async () => {
    sendMessageMock
      .mockRejectedValueOnce(new ApiError('网络连接失败，请稍后重试'))
      .mockResolvedValueOnce(pair())
    renderPage()
    await screen.findByRole('button', { name: /访谈录音\.wav/ })
    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await userEvent.click(await screen.findByRole('button', { name: '使用原请求重试' }))
    await waitFor(() => expect(sendMessageMock).toHaveBeenCalledTimes(2))
    expect(sendMessageMock.mock.calls[0][1].clientRequestId).toBe(clientRequestId)
    expect(sendMessageMock.mock.calls[1][1].clientRequestId).toBe(clientRequestId)
  })

  it('does not render a 401 response as an Agent answer failure', async () => {
    sendMessageMock.mockRejectedValue(new ApiError('登录已失效', 40501, undefined, 401))
    renderPage()
    await screen.findByRole('button', { name: /访谈录音\.wav/ })
    await userEvent.type(screen.getByLabelText('向 Agent 提问'), '新的问题')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => expect(sendMessageMock).toHaveBeenCalled())
    expect(screen.queryByText('本次回答生成失败')).not.toBeInTheDocument()
    expect(screen.queryByText('问题未成功发送')).not.toBeInTheDocument()
  })

  it('shows the unified processing-plan entry for an Agent plan', async () => {
    const waiting = processingWorkflow()
    getMessagesMock.mockResolvedValue({
      records: [pair().assistantMessage], current: 1, size: 50, total: 1, pages: 1,
    })
    getWorkflowsMock.mockResolvedValue([waiting])
    renderPage()

    expect(await screen.findByRole('button', { name: '查看并确认处理方案' }))
      .toBeInTheDocument()
    expect(screen.getAllByText('压缩长静音')).toHaveLength(2)
    expect(screen.queryByText('问题未成功发送')).not.toBeInTheDocument()
  })

  it('loads the matching history after a conversation switch', async () => {
    getConversationsMock.mockResolvedValue({
      records: [
        conversation(conversationId, '2026-08-04T11:00:00'),
        conversation(secondConversationId, '2026-08-04T10:00:00'),
      ],
      current: 1,
      size: 20,
      total: 2,
      pages: 1,
    })
    getMessagesMock.mockImplementation(async (id) => ({
      records: [agentMessage(
        id === conversationId ? '2084462342950039580' : '2084462342950039581',
        'ASSISTANT',
        id === conversationId ? '第一个会话历史' : '第二个会话历史',
        1,
      )],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    }))
    renderPage()

    expect(await screen.findByText('第一个会话历史')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /第二个对话/ }))

    expect(await screen.findByText('第二个会话历史')).toBeInTheDocument()
    expect(screen.queryByText('第一个会话历史')).not.toBeInTheDocument()
    expect(getMessagesMock).toHaveBeenCalledWith(secondConversationId, 1, 50, expect.any(AbortSignal))
  })

  it('does not retain an active citation after switching conversations', async () => {
    getConversationsMock.mockResolvedValue({
      records: [
        conversation(conversationId, '2026-08-04T11:00:00'),
        conversation(secondConversationId, '2026-08-04T10:00:00'),
      ],
      current: 1,
      size: 20,
      total: 2,
      pages: 1,
    })
    getMessagesMock.mockImplementation(async (id) => ({
      records: [agentMessage(
        id === conversationId ? '2084462342950039582' : '2084462342950039583',
        'ASSISTANT',
        id === conversationId ? '第一个会话引用' : '第二个会话引用',
        1,
        { citations: [citation] },
      )],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    }))
    const { container } = renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' }))
    expect(container.querySelectorAll('.agent-citation--active')).toHaveLength(1)
    const pauseCallsBeforeSwitch = pause.mock.calls.length

    await userEvent.click(screen.getByRole('button', { name: /第二个对话/ }))

    expect(await screen.findByText('第二个会话引用')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '播放引用 1，00:42 到 00:48' })).toHaveAttribute('aria-pressed', 'false')
    expect(container.querySelectorAll('.agent-citation--active')).toHaveLength(0)
    expect(pause.mock.calls.length).toBeGreaterThan(pauseCallsBeforeSwitch)
    expect(playerController.playbackRange).toBeNull()
  })

  it('does not update citation state after the page unmounts', async () => {
    getMessagesMock.mockResolvedValue({
      records: [agentMessage('2084462342950039584', 'ASSISTANT', '历史回答', 2, { citations: [citation] })],
      current: 1,
      size: 50,
      total: 1,
      pages: 1,
    })
    let resolveSeek!: () => void
    seekTo.mockImplementationOnce((
      seconds: number,
      options?: { play?: boolean, endSeconds?: number },
    ) => {
      playerController.currentTimeSeconds = seconds
      playerController.isPlaying = true
      playerController.playbackRange = options?.endSeconds
        ? { startSeconds: seconds, endSeconds: options.endSeconds }
        : null
      return new Promise<void>((resolve) => { resolveSeek = resolve })
    })
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    try {
      const rendered = renderPage()
      await userEvent.click(await screen.findByRole('button', { name: '播放引用 1，00:42 到 00:48' }))
      rendered.unmount()
      resolveSeek()
      await Promise.resolve()

      expect(consoleError.mock.calls.flat().join(' ')).not.toContain('state update on an unmounted component')
    } finally {
      consoleError.mockRestore()
    }
  })
})
