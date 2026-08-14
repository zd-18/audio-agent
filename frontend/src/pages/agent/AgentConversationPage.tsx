import {
  ArrowLeftOutlined,
  AudioOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  MessageOutlined,
  PauseOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  SoundOutlined,
} from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Input, Skeleton, Spin, Tooltip } from 'antd'
import type { KeyboardEvent } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  confirmAgentProcessingWorkflow,
  createAgentConversation,
  getAgentConversations,
  getAgentMessages,
  getAgentProcessingWorkflow,
  getAgentProcessingWorkflows,
  sendAgentMessage,
} from '../../api/agent'
import { downloadAudioFile } from '../../api/audioFiles'
import { ApiError, isValidResourceId } from '../../api/http'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useTranscript } from '../../hooks/useTranscript'
import { useTranscriptionTaskPolling } from '../../hooks/useTranscriptionTaskPolling'
import type {
  AgentCitation,
  AgentConversation,
  AgentMessage,
  AgentProcessingWorkflow,
  AgentRequestMode,
} from '../../types/agent'
import {
  createClientRequestId,
  normalizeAgentMessages,
  selectLatestActiveConversation,
} from '../../utils/agent'
import { formatDateTime, formatDuration } from '../../utils/formatters'
import '../analysis/analysis-report.css'
import './agent-conversation.css'
import AgentProcessingWorkflowCard from './AgentProcessingWorkflowCard'

const RECOMMENDED_QUESTIONS = [
  '这段音频主要表达了什么观点？',
  '请总结其中的关键内容。',
  '哪些原文最能体现说话者的核心观点？',
]

interface DisplayAgentMessage extends AgentMessage {
  localRequestId?: string
  optimistic?: boolean
  processingRequest?: boolean
}

interface RetryRequest {
  content: string
  clientRequestId: string
  conversationId: string | null
  mode: AgentRequestMode
}

interface ActiveCitationPlayback {
  key: string
  startSeconds: number
  endSeconds: number
}

const CITATION_TIME_TOLERANCE_SECONDS = 0.04

function citationPlaybackKey(messageId: string, citationId: string) {
  return `${messageId}:${citationId}`
}

function mergeDisplayMessages(messages: DisplayAgentMessage[]) {
  const byId = new Map<string, DisplayAgentMessage>()
  for (const message of messages) byId.set(message.messageId, message)
  return [...byId.values()].sort((left, right) => (
    left.sequenceNo - right.sequenceNo
    || left.createdAt.localeCompare(right.createdAt)
    || left.messageId.localeCompare(right.messageId)
  ))
}

function localMessage(
  role: AgentMessage['role'],
  content: string | null,
  status: AgentMessage['status'],
  sequenceNo: number,
  clientRequestId: string,
  processingRequest = false,
): DisplayAgentMessage {
  const createdAt = new Date().toISOString()
  return {
    messageId: `local-${role.toLowerCase()}-${clientRequestId}`,
    role,
    content,
    status,
    sequenceNo,
    replyToMessageId: null,
    clientRequestId,
    modelName: null,
    promptVersion: null,
    promptTokens: null,
    completionTokens: null,
    totalTokens: null,
    failureCode: null,
    failureMessage: null,
    citations: [],
    createdAt,
    finishedAt: null,
    localRequestId: clientRequestId,
    optimistic: true,
    processingRequest,
  }
}

function conversationTime(conversation: AgentConversation) {
  return conversation.lastMessageAt || conversation.updatedAt || conversation.createdAt
}

function AgentCitationList({
  messageId,
  citations,
  activeCitationKey,
  isPlaying,
  onPlay,
}: {
  messageId: string
  citations: AgentCitation[]
  activeCitationKey: string | null
  isPlaying: boolean
  onPlay: (messageId: string, citation: AgentCitation) => void
}) {
  if (citations.length === 0) return null
  return (
    <section className="agent-citations" aria-label={`引用原文，共 ${citations.length} 条`}>
      <div className="agent-citations__heading">
        <span>引用原文</span>
        <small>{citations.length} 处</small>
      </div>
      <div className="agent-citations__list">
        {citations.map((citation, index) => {
          const citationKey = citationPlaybackKey(messageId, citation.citationId)
          const isActive = citationKey === activeCitationKey
          const isCitationPlaying = isActive && isPlaying
          const actionLabel = isCitationPlaying ? '暂停' : isActive ? '继续播放' : '播放此处'
          const accessibleAction = isCitationPlaying ? '暂停' : isActive ? '继续播放' : '播放'
          return (
            <button
              key={citation.citationId}
              type="button"
              className={`agent-citation${isActive ? ' agent-citation--active' : ''}${isCitationPlaying ? ' agent-citation--playing' : isActive ? ' agent-citation--paused' : ''}`}
              data-playback-state={isCitationPlaying ? 'playing' : isActive ? 'paused' : 'idle'}
              onClick={() => onPlay(messageId, citation)}
              aria-label={`${accessibleAction}引用 ${index + 1}，${formatDuration(citation.startMs)} 到 ${formatDuration(citation.endMs)}`}
              aria-pressed={isActive}
            >
              <span className="agent-citation__index">{index + 1}</span>
              <span className="agent-citation__body">
                <span className="agent-citation__time">
                  {formatDuration(citation.startMs)} – {formatDuration(citation.endMs)}
                </span>
                <q>{citation.quote || '未返回引用原文'}</q>
              </span>
              <span className="agent-citation__play" aria-hidden="true">
                {isCitationPlaying ? <PauseOutlined /> : isActive ? <PlayCircleOutlined /> : <SoundOutlined />}
                {actionLabel}
              </span>
            </button>
          )
        })}
      </div>
    </section>
  )
}

function AgentMessageItem({
  message,
  activeCitationKey,
  citationIsPlaying,
  onPlayCitation,
  workflow,
  confirmingWorkflow,
  onConfirmWorkflow,
}: {
  message: DisplayAgentMessage
  activeCitationKey: string | null
  citationIsPlaying: boolean
  onPlayCitation: (messageId: string, citation: AgentCitation) => void
  workflow: AgentProcessingWorkflow | null
  confirmingWorkflow: boolean
  onConfirmWorkflow: (workflowId: string) => void
}) {
  if (message.role === 'ASSISTANT' && message.status === 'FAILED') {
    return (
      <article className="agent-message agent-message--assistant agent-message--failed">
        <div className="agent-message__avatar" aria-hidden="true"><ExclamationCircleOutlined /></div>
        <div className="agent-message__failed-card" role="status">
          <strong>本次回答生成失败</strong>
          <p>{message.failureMessage || 'Agent 暂时无法完成本次回答，请稍后重新提问。'}</p>
          <time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time>
        </div>
      </article>
    )
  }

  if (message.role === 'ASSISTANT' && message.status === 'PROCESSING') {
    return (
      <article className="agent-message agent-message--assistant agent-message--processing" aria-live="polite">
        <div className="agent-message__avatar" aria-hidden="true"><MessageOutlined /></div>
        <div className="agent-message__bubble">
          <span className="agent-message__thinking">
            <LoadingOutlined spin />
            {message.processingRequest ? '正在理解音频处理需求' : 'Agent 正在分析文字稿'}
          </span>
        </div>
      </article>
    )
  }

  const isUser = message.role === 'USER'
  return (
    <article className={`agent-message agent-message--${isUser ? 'user' : 'assistant'}`}>
      {!isUser && <div className="agent-message__avatar" aria-hidden="true"><MessageOutlined /></div>}
      <div className="agent-message__content">
        <div className="agent-message__bubble">
          <p>{message.content?.trim() || (isUser ? '消息内容不可用' : '回答内容暂不可用')}</p>
        </div>
        {!isUser && (
          <AgentCitationList
            messageId={message.messageId}
            citations={message.citations}
            activeCitationKey={activeCitationKey}
            isPlaying={citationIsPlaying}
            onPlay={onPlayCitation}
          />
        )}
        {!isUser && workflow && (
          <AgentProcessingWorkflowCard
            workflow={workflow}
            confirming={confirmingWorkflow}
            onConfirm={onConfirmWorkflow}
          />
        )}
        <time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time>
      </div>
    </article>
  )
}

export default function AgentConversationPage() {
  const { message: messageApi } = AntdApp.useApp()
  const { taskId } = useParams()
  const validTaskId = isValidResourceId(taskId) ? taskId : undefined
  const taskState = useTranscriptionTaskPolling(validTaskId)
  const task = taskState.task?.taskId === validTaskId ? taskState.task : null
  const transcriptState = useTranscript(validTaskId, task?.status === 'SUCCESS')
  const transcript = transcriptState.transcript
  const transcriptId = transcript?.transcriptId
  const player = useAudioPlayback(task?.audioFileId, transcript?.durationMs)
  const playerRef = useRef(player)
  playerRef.current = player
  const [conversations, setConversations] = useState<AgentConversation[]>([])
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<DisplayAgentMessage[]>([])
  const [input, setInput] = useState('')
  const [conversationsLoading, setConversationsLoading] = useState(false)
  const [conversationsError, setConversationsError] = useState<string | null>(null)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [messagesError, setMessagesError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [sending, setSending] = useState(false)
  const [composerError, setComposerError] = useState<string | null>(null)
  const [retryRequest, setRetryRequest] = useState<RetryRequest | null>(null)
  const [requestMode, setRequestMode] = useState<AgentRequestMode>('CHAT')
  const [workflows, setWorkflows] = useState<AgentProcessingWorkflow[]>([])
  const [confirmingWorkflowId, setConfirmingWorkflowId] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [activeCitation, setActiveCitation] = useState<ActiveCitationPlayback | null>(null)
  const [conversationVersion, setConversationVersion] = useState(0)
  const selectedConversationIdRef = useRef<string | null>(null)
  const createPromiseRef = useRef<Promise<AgentConversation> | null>(null)
  const createControllerRef = useRef<AbortController | null>(null)
  const sendControllerRef = useRef<AbortController | null>(null)
  const sendInFlightRef = useRef(false)
  const mountedRef = useRef(true)
  const skipNextMessageLoadRef = useRef<string | null>(null)
  const messageEndRef = useRef<HTMLDivElement | null>(null)
  const composingRef = useRef(false)

  useEffect(() => {
    selectedConversationIdRef.current = selectedConversationId
  }, [selectedConversationId])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      createControllerRef.current?.abort()
      sendControllerRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    setConversations([])
    setSelectedConversationId(null)
    selectedConversationIdRef.current = null
    setMessages([])
    setRetryRequest(null)
    setComposerError(null)
    setActiveCitation(null)
    setWorkflows([])
    setConfirmingWorkflowId(null)
  }, [transcriptId])

  useEffect(() => {
    if (!transcriptId) return
    const controller = new AbortController()
    setConversationsLoading(true)
    setConversationsError(null)
    getAgentConversations({
      current: 1,
      size: 20,
      transcriptId,
      status: 'ACTIVE',
    }, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) return
        const matching = page.records.filter((conversation) => conversation.transcriptId === transcriptId)
        const latest = selectLatestActiveConversation(matching, transcriptId)
        setConversations(matching)
        setSelectedConversationId((current) => {
          const next = current && matching.some((item) => item.conversationId === current)
            ? current
            : latest?.conversationId ?? null
          selectedConversationIdRef.current = next
          return next
        })
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setConversationsError(error instanceof Error ? error.message : '会话列表加载失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setConversationsLoading(false)
      })
    return () => controller.abort()
  }, [conversationVersion, transcriptId])

  useEffect(() => {
    const currentPlayer = playerRef.current
    currentPlayer.pause()
    currentPlayer.seek(currentPlayer.currentTimeSeconds)
    setActiveCitation(null)
    setMessagesError(null)
    setComposerError(null)
    setRetryRequest(null)
    if (!selectedConversationId) {
      setMessages([])
      setMessagesLoading(false)
      return
    }
    if (skipNextMessageLoadRef.current === selectedConversationId) {
      skipNextMessageLoadRef.current = null
      setMessagesLoading(false)
      return
    }
    setMessages([])
    const controller = new AbortController()
    const requestedConversationId = selectedConversationId
    setMessagesLoading(true)
    getAgentMessages(requestedConversationId, 1, 50, controller.signal)
      .then((page) => {
        if (
          controller.signal.aborted
          || selectedConversationIdRef.current !== requestedConversationId
        ) return
        setMessages(normalizeAgentMessages(page.records))
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (selectedConversationIdRef.current !== requestedConversationId) return
        setMessagesError(error instanceof Error ? error.message : '消息历史加载失败')
      })
      .finally(() => {
        if (
          !controller.signal.aborted
          && selectedConversationIdRef.current === requestedConversationId
        ) setMessagesLoading(false)
      })
    return () => controller.abort()
  }, [selectedConversationId])

  useEffect(() => {
    if (!selectedConversationId) {
      setWorkflows([])
      return
    }
    const controller = new AbortController()
    const conversationId = selectedConversationId
    getAgentProcessingWorkflows(conversationId, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted
          && selectedConversationIdRef.current === conversationId) {
          setWorkflows(items)
        }
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (selectedConversationIdRef.current === conversationId) {
          setComposerError(error instanceof Error ? error.message : '音频处理进度加载失败')
        }
      })
    return () => controller.abort()
  }, [selectedConversationId])

  useEffect(() => {
    const active = workflows.filter((workflow) => (
      workflow.status === 'EXECUTING' || workflow.status === 'REVIEWING'
    ))
    if (active.length === 0) return
    let cancelled = false
    const timer = window.setInterval(() => {
      void Promise.all(active.map((workflow) => (
        getAgentProcessingWorkflow(workflow.workflowId)
      ))).then((updated) => {
        if (cancelled) return
        const byId = new Map(updated.map((workflow) => [workflow.workflowId, workflow]))
        setWorkflows((current) => current.map((workflow) => (
          byId.get(workflow.workflowId) ?? workflow
        )))
      }).catch(() => {
        // Preserve the last known state. A later bounded poll may recover.
      })
    }, 1500)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [workflows])

  useEffect(() => {
    if (!activeCitation) return
    const playbackRange = player.playbackRange
    const rangeMatches = playbackRange !== null
      && Math.abs(playbackRange.endSeconds - activeCitation.endSeconds) <= CITATION_TIME_TOLERANCE_SECONDS
    const currentTimeInCitation = player.currentTimeSeconds >= activeCitation.startSeconds - CITATION_TIME_TOLERANCE_SECONDS
      && player.currentTimeSeconds < activeCitation.endSeconds - CITATION_TIME_TOLERANCE_SECONDS
    if (!rangeMatches || !currentTimeInCitation || player.error) setActiveCitation(null)
  }, [activeCitation, player.currentTimeSeconds, player.error, player.playbackRange])

  useEffect(() => {
    if (messages.length === 0 && !sending) return
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const frame = window.requestAnimationFrame(() => {
      messageEndRef.current?.scrollIntoView({
        block: 'end',
        behavior: reduceMotion ? 'auto' : 'smooth',
      })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [messages, sending])

  const createConversation = useCallback(async () => {
    if (!transcriptId) throw new Error('文字稿尚未加载完成')
    if (createPromiseRef.current) return createPromiseRef.current
    const controller = new AbortController()
    createControllerRef.current = controller
    setCreating(true)
    const promise = createAgentConversation({ transcriptId, title: null }, controller.signal)
      .then((conversation) => {
        if (!mountedRef.current || transcriptId !== transcript?.transcriptId) return conversation
        skipNextMessageLoadRef.current = conversation.conversationId
        selectedConversationIdRef.current = conversation.conversationId
        setSelectedConversationId(conversation.conversationId)
        setConversations((current) => [
          conversation,
          ...current.filter((item) => item.conversationId !== conversation.conversationId),
        ])
        setMessages([])
        setMessagesError(null)
        setComposerError(null)
        setRetryRequest(null)
        return conversation
      })
      .finally(() => {
        if (createControllerRef.current === controller) createControllerRef.current = null
        createPromiseRef.current = null
        if (mountedRef.current) setCreating(false)
      })
    createPromiseRef.current = promise
    return promise
  }, [transcript, transcriptId])

  const newConversation = async () => {
    if (creating || sending || !transcriptId) return
    try {
      await createConversation()
      void messageApi.success('已创建新对话')
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      void messageApi.error(error instanceof Error ? error.message : '新建对话失败')
    }
  }

  const submitMessage = useCallback(async (retry?: RetryRequest) => {
    const content = (retry?.content ?? input).trim()
    const mode = retry?.mode ?? requestMode
    if (
      !content
      || sendInFlightRef.current
      || !transcriptId
      || conversationsLoading
      || messagesLoading
    ) return
    const clientRequestId = retry?.clientRequestId ?? createClientRequestId()
    sendInFlightRef.current = true
    setSending(true)
    setComposerError(null)
    setRetryRequest(null)
    let targetConversationId = retry?.conversationId || selectedConversationIdRef.current
    let optimisticAdded = false
    try {
      if (!targetConversationId) {
        const created = await createConversation()
        targetConversationId = created.conversationId
      }
      const conversationId = targetConversationId
      if (selectedConversationIdRef.current !== conversationId) {
        selectedConversationIdRef.current = conversationId
        setSelectedConversationId(conversationId)
      }
      setMessages((current) => {
        if (current.some((item) => item.localRequestId === clientRequestId)) return current
        optimisticAdded = true
        const nextSequence = current.reduce(
          (maximum, item) => Math.max(maximum, item.sequenceNo),
          0,
        ) + 1
        return mergeDisplayMessages([
          ...current,
          localMessage('USER', content, 'SUCCESS', nextSequence, clientRequestId),
          localMessage('ASSISTANT', null, 'PROCESSING', nextSequence + 1,
            clientRequestId, mode === 'PROCESSING'),
        ])
      })
      const controller = new AbortController()
      sendControllerRef.current = controller
      const pair = await sendAgentMessage(
        conversationId,
        { content, clientRequestId, mode },
        controller.signal,
      )
      if (!mountedRef.current || selectedConversationIdRef.current !== conversationId) return
      setMessages((current) => mergeDisplayMessages([
        ...current.filter((item) => item.localRequestId !== clientRequestId),
        ...normalizeAgentMessages([pair.userMessage, pair.assistantMessage]),
      ]))
      if (pair.processingWorkflow) {
        const workflow = pair.processingWorkflow
        setWorkflows((current) => [
          ...current.filter((item) => item.workflowId !== workflow.workflowId),
          workflow,
        ])
      }
      setInput((current) => current.trim() === content ? '' : current)
      setConversations((current) => current.map((conversation) => (
        conversation.conversationId === conversationId
          ? {
              ...conversation,
              lastMessageId: pair.assistantMessage.messageId,
              lastMessageAt: pair.assistantMessage.createdAt,
              updatedAt: pair.assistantMessage.createdAt,
            }
          : conversation
      )))
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      if (error instanceof ApiError && (error.status === 401 || [40501, 40504, 40507].includes(error.code ?? 0))) return
      if (!mountedRef.current) return
      if (targetConversationId && selectedConversationIdRef.current === targetConversationId) {
        setMessages((current) => current.filter((item) => !(
          item.localRequestId === clientRequestId
          && item.role === 'ASSISTANT'
          && item.status === 'PROCESSING'
        )))
      }
      setComposerError(error instanceof Error ? error.message : '问题发送失败，请检查网络后重试')
      setRetryRequest({
        content,
        clientRequestId,
        conversationId: targetConversationId,
        mode,
      })
      if (!optimisticAdded) setInput(content)
    } finally {
      sendControllerRef.current = null
      sendInFlightRef.current = false
      if (mountedRef.current) setSending(false)
    }
  }, [conversationsLoading, createConversation, input, messagesLoading, requestMode, transcriptId])

  const confirmWorkflow = useCallback(async (workflowId: string) => {
    if (confirmingWorkflowId) return
    setConfirmingWorkflowId(workflowId)
    setComposerError(null)
    try {
      const updated = await confirmAgentProcessingWorkflow(workflowId)
      if (!mountedRef.current) return
      setWorkflows((current) => current.map((workflow) => (
        workflow.workflowId === updated.workflowId ? updated : workflow
      )))
      void messageApi.success('已确认，开始处理音频')
    } catch (error) {
      if (!mountedRef.current) return
      setComposerError(error instanceof Error ? error.message : '处理方案确认失败')
    } finally {
      if (mountedRef.current) setConfirmingWorkflowId(null)
    }
  }, [confirmingWorkflowId, messageApi])

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key !== 'Enter'
      || event.shiftKey
      || composingRef.current
      || event.nativeEvent.isComposing
    ) return
    event.preventDefault()
    void submitMessage()
  }

  const selectConversation = (conversationId: string) => {
    if (sending || creating || conversationId === selectedConversationIdRef.current) return
    setActiveCitation(null)
    player.pause()
    player.seek(player.currentTimeSeconds)
    selectedConversationIdRef.current = conversationId
    setSelectedConversationId(conversationId)
    setMessages([])
  }

  const playCitation = useCallback((messageId: string, citation: AgentCitation) => {
    const key = citationPlaybackKey(messageId, citation.citationId)
    if (activeCitation?.key === key) {
      if (player.isPlaying) player.pause()
      else void player.resume()
      return
    }

    const startSeconds = citation.startMs / 1000
    const endSeconds = citation.endMs / 1000
    setActiveCitation({ key, startSeconds, endSeconds })
    void player.seekTo(citation.startMs / 1000, {
      play: true,
      endSeconds: citation.endMs > citation.startMs ? citation.endMs / 1000 : undefined,
    })
  }, [activeCitation?.key, player.isPlaying, player.pause, player.resume, player.seekTo])

  const download = async () => {
    if (!task || downloading) return
    setDownloading(true)
    try {
      await downloadAudioFile(task.audioFileId, task.audioFileName || `audio-${task.audioFileId}`)
    } catch (error) {
      void messageApi.error(error instanceof Error ? error.message : '音频下载失败')
    } finally {
      if (mountedRef.current) setDownloading(false)
    }
  }

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.conversationId === selectedConversationId) ?? null,
    [conversations, selectedConversationId],
  )
  const contextLoading = taskState.loading || (task?.status === 'SUCCESS' && transcriptState.loading)

  if (!validTaskId) {
    return (
      <PageContainer>
        <Alert type="error" showIcon message="Agent 页面地址无效" description="请从文字稿详情页重新进入。" action={<Link to="/transcriptions"><Button>返回转写列表</Button></Link>} />
      </PageContainer>
    )
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="TRANSCRIPT AGENT"
        title={task?.audioFileName || '智能问答'}
        description="围绕当前文字稿持续追问；回答中的引用可直接定位到源音频。"
        actions={<Link to={`/transcriptions/${encodeURIComponent(validTaskId)}`}><Button icon={<ArrowLeftOutlined />}>返回文字稿</Button></Link>}
      />

      {contextLoading && !transcript && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 8 }} /></section>}
      {taskState.error && !task && <Alert className="resource-detail-alert" type="error" showIcon message="转写任务加载失败" description={taskState.error} action={<Button onClick={taskState.refresh}>重试</Button>} />}
      {task && task.status !== 'SUCCESS' && (
        <Alert
          className="resource-detail-alert"
          type="warning"
          showIcon
          message="文字稿尚未准备完成"
          description="Agent 需要成功的文字稿才能回答问题，请返回详情等待转写完成。"
          action={<Link to={`/transcriptions/${encodeURIComponent(validTaskId)}`}><Button>查看转写状态</Button></Link>}
        />
      )}
      {transcriptState.error && <Alert className="resource-detail-alert" type="error" showIcon message="文字稿加载失败" description={transcriptState.error} action={<Button onClick={transcriptState.refresh}>重试</Button>} />}

      {task && transcript && (
        <>
          <ReportAudioPlayer
            player={player}
            fallbackFileName={task.audioFileName}
            downloading={downloading}
            onDownload={() => { void download() }}
            sectionId="agent-audio-player"
            eyebrow="CITATION AUDIO"
          />

          <section className="agent-workspace" aria-label="Agent 多轮问答工作区">
            <aside className="agent-conversation-panel" aria-labelledby="agent-conversation-list-title">
              <div className="agent-conversation-panel__heading">
                <div>
                  <span>CONVERSATIONS</span>
                  <h3 id="agent-conversation-list-title">对话记录</h3>
                </div>
                <Tooltip title="新建对话">
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    aria-label="新建对话"
                    loading={creating}
                    disabled={creating || sending || conversationsLoading}
                    onClick={() => { void newConversation() }}
                  />
                </Tooltip>
              </div>
              {conversationsError && (
                <Alert
                  className="agent-conversation-panel__error"
                  type="error"
                  showIcon
                  message="会话加载失败"
                  description={conversationsError}
                  action={<Button size="small" icon={<ReloadOutlined />} onClick={() => setConversationVersion((value) => value + 1)}>重试</Button>}
                />
              )}
              <div className="agent-conversation-list" aria-busy={conversationsLoading}>
                {conversationsLoading && conversations.length === 0 && <div className="agent-conversation-list__loading"><Spin size="small" /><span>正在加载对话</span></div>}
                {!conversationsLoading && conversations.length === 0 && !conversationsError && (
                  <div className="agent-conversation-list__empty">
                    <MessageOutlined />
                    <strong>还没有对话</strong>
                    <span>发送第一个问题时会自动创建。</span>
                  </div>
                )}
                {conversations.map((conversation, index) => {
                  const selected = conversation.conversationId === selectedConversationId
                  return (
                    <button
                      key={conversation.conversationId}
                      type="button"
                      className={`agent-conversation-item${selected ? ' is-selected' : ''}`}
                      aria-pressed={selected}
                      disabled={sending || creating}
                      onClick={() => selectConversation(conversation.conversationId)}
                    >
                      <span className="agent-conversation-item__icon" aria-hidden="true"><MessageOutlined /></span>
                      <span className="agent-conversation-item__copy">
                        <strong>{conversation.title || `对话 ${index + 1}`}</strong>
                        <span>{conversation.lastMessageAt ? '最近更新' : '创建于'} {formatDateTime(conversationTime(conversation))}</span>
                      </span>
                    </button>
                  )
                })}
              </div>
            </aside>

            <section className="agent-chat-panel" aria-labelledby="agent-chat-title">
              <div className="agent-chat-panel__heading">
                <div>
                  <span>ACTIVE CONVERSATION</span>
                  <h3 id="agent-chat-title">{selectedConversation?.title || '开始新的对话'}</h3>
                </div>
                <span className="agent-chat-panel__audio"><AudioOutlined /> {transcript.audioFileName || task.audioFileName}</span>
              </div>

              <div className="agent-message-viewport" aria-busy={messagesLoading}>
                {messagesLoading && <div className="agent-message-skeleton"><Skeleton active avatar paragraph={{ rows: 3 }} /><Skeleton active avatar paragraph={{ rows: 2 }} /></div>}
                {messagesError && (
                  <Alert
                    className="agent-message-error"
                    type="error"
                    showIcon
                    message="消息历史加载失败"
                    description={messagesError}
                    action={<Button icon={<ReloadOutlined />} onClick={() => {
                      const current = selectedConversationIdRef.current
                      setSelectedConversationId(null)
                      window.setTimeout(() => setSelectedConversationId(current), 0)
                    }}>重试</Button>}
                  />
                )}
                {!messagesLoading && messages.length === 0 && !messagesError && (
                  <div className="agent-chat-empty">
                    <span className="agent-chat-empty__icon" aria-hidden="true"><MessageOutlined /></span>
                    <h4>从文字稿中找到答案</h4>
                    <p>Agent 会基于当前音频文字稿回答，并在可用时附上可播放的原文引用。</p>
                    <div className="agent-recommendations" aria-label="推荐问题">
                      {RECOMMENDED_QUESTIONS.map((question) => (
                        <button key={question} type="button" onClick={() => setInput(question)}>{question}</button>
                      ))}
                    </div>
                  </div>
                )}
                {!messagesLoading && messages.map((agentMessage) => (
                  <AgentMessageItem
                    key={agentMessage.messageId}
                    message={agentMessage}
                    activeCitationKey={activeCitation?.key ?? null}
                    citationIsPlaying={Boolean(activeCitation) && player.isPlaying}
                    onPlayCitation={playCitation}
                    workflow={workflows.find((workflow) => (
                      workflow.assistantMessageId === agentMessage.messageId
                    )) ?? null}
                    confirmingWorkflow={confirmingWorkflowId !== null
                      && workflows.some((workflow) => (
                        workflow.workflowId === confirmingWorkflowId
                        && workflow.assistantMessageId === agentMessage.messageId
                      ))}
                    onConfirmWorkflow={confirmWorkflow}
                  />
                ))}
                <div ref={messageEndRef} aria-hidden="true" />
              </div>

              <form className="agent-composer" onSubmit={(event) => { event.preventDefault(); void submitMessage() }}>
                {composerError && (
                  <Alert
                    className="agent-composer__error"
                    type="error"
                    showIcon
                    message="问题未成功发送"
                    description={composerError}
                    action={retryRequest ? <Button size="small" onClick={() => { void submitMessage(retryRequest) }}>使用原请求重试</Button> : undefined}
                  />
                )}
                <div className="agent-composer__mode" role="group" aria-label="Agent 请求类型">
                  <button
                    type="button"
                    className={requestMode === 'CHAT' ? 'is-active' : ''}
                    aria-pressed={requestMode === 'CHAT'}
                    disabled={sending}
                    onClick={() => setRequestMode('CHAT')}
                  >
                    内容问答
                  </button>
                  <button
                    type="button"
                    className={requestMode === 'PROCESSING' ? 'is-active' : ''}
                    aria-pressed={requestMode === 'PROCESSING'}
                    disabled={sending}
                    onClick={() => setRequestMode('PROCESSING')}
                  >
                    音频处理
                  </button>
                </div>
                <label htmlFor="agent-question">
                  {requestMode === 'PROCESSING' ? '描述希望怎样处理音频' : '向 Agent 提问'}
                </label>
                <div className="agent-composer__input-row">
                  <Input.TextArea
                    id="agent-question"
                    value={input}
                    autoSize={{ minRows: 2, maxRows: 6 }}
                    maxLength={4000}
                    placeholder={requestMode === 'PROCESSING'
                      ? '例如：裁掉 00:10 到 00:15，并把整段音量调整得更均衡'
                      : '围绕这段文字稿继续提问…'}
                    disabled={sending}
                    onChange={(event) => setInput(event.target.value)}
                    onCompositionStart={() => { composingRef.current = true }}
                    onCompositionEnd={() => { composingRef.current = false }}
                    onKeyDown={handleKeyDown}
                  />
                  <Button
                    type="primary"
                    htmlType="submit"
                    icon={sending ? <LoadingOutlined spin /> : <SendOutlined />}
                    disabled={sending || creating || conversationsLoading || messagesLoading || !input.trim()}
                    aria-label={sending
                      ? (requestMode === 'PROCESSING' ? '正在发送处理请求' : '正在发送问题')
                      : (requestMode === 'PROCESSING' ? '发送处理请求' : '发送问题')}
                  >
                    {sending ? (requestMode === 'PROCESSING' ? '规划中' : '分析中') : '发送'}
                  </Button>
                </div>
                <div className="agent-composer__hint">
                  <span>Enter 发送 · Shift + Enter 换行</span>
                  <span>{input.length}/4000</span>
                </div>
              </form>
            </section>
          </section>
        </>
      )}
    </PageContainer>
  )
}
