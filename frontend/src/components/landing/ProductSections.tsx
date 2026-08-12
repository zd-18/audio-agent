import {
  AimOutlined,
  AlertOutlined,
  AudioOutlined,
  AudioMutedOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  MessageOutlined,
  ProfileOutlined,
  ReadOutlined,
  RobotOutlined,
  SoundOutlined,
  TeamOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons'
import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import AnimatedBorderButton from './AnimatedBorderButton'

interface CompactCapability {
  name: string
  icon: ReactNode
}

const issueMarkers = [
  { label: '长静音', time: '08:42', detail: '连续静音 4.8 秒，可能影响节目节奏。', position: 22, tone: 'cyan' },
  { label: '背景噪声', time: '18:16', detail: '检测到持续环境底噪，建议检查该片段。', position: 48, tone: 'purple' },
  { label: '音量异常', time: '31:05', detail: '响度短时升高，可能造成听感突兀。', position: 78, tone: 'orange' },
]

const transcriptPreview = [
  { speaker: '说话人 A', time: '12:08', text: '我们希望新一季节目能把每位嘉宾的实践经验讲得更具体。' },
  { speaker: '说话人 B', time: '12:17', text: '可以先从选题方法开始，再补充录制和后期整理流程。' },
  { speaker: '说话人 A', time: '12:31', text: '那下一步先整理三期选题，并确认第一位访谈嘉宾。' },
]

const compactCapabilities: CompactCapability[] = [
  { name: '静音检测', icon: <AudioMutedOutlined /> },
  { name: '噪声分析', icon: <SoundOutlined /> },
  { name: '响度检测', icon: <AudioOutlined /> },
  { name: '语音转文字', icon: <FileTextOutlined /> },
  { name: '说话人识别', icon: <TeamOutlined /> },
  { name: '内容摘要', icon: <ProfileOutlined /> },
  { name: '片段定位', icon: <AimOutlined /> },
  { name: '修复建议', icon: <BulbOutlined /> },
]

const waveformBars = [34, 52, 43, 72, 58, 86, 64, 40, 76, 92, 55, 68, 44, 82, 62, 38, 71, 49, 88, 57, 74, 46, 66, 36, 54, 83, 47, 69, 91, 59, 41, 73]

const scenarios = [
  {
    name: '播客制作',
    description: '在发布前快速了解音频质量与内容结构。',
    problem: '发现静音、噪声和节奏断点，减少人工巡听成本。',
    icon: <CustomerServiceOutlined />,
  },
  {
    name: '访谈录音',
    description: '整理多人访谈中的关键内容与录音问题。',
    problem: '辅助区分发言内容，定位影响听感的问题片段。',
    icon: <MessageOutlined />,
  },
  {
    name: '会议记录',
    description: '让长时间会议录音更容易检索和回顾。',
    problem: '减少逐段回听，帮助聚焦议题、结论与待办。',
    icon: <VideoCameraOutlined />,
  },
  {
    name: '课程与讲座',
    description: '为课程、分享和讲座建立可复用的音频资料。',
    problem: '辅助提取知识重点，并发现影响学习体验的片段。',
    icon: <ReadOutlined />,
  },
]

const roadmap = [
  {
    status: 'live',
    title: '当前版本',
    description: '完成音频进入产品后的基础管理与信息分析。',
    items: ['音频上传', '基础信息分析', '分析任务管理'],
  },
  {
    status: 'building',
    title: '下一阶段',
    description: '逐步提供面向听感和质量问题的检测结果。',
    items: ['静音检测', '噪声分析', '音频问题报告'],
  },
  {
    status: 'planned',
    title: '后续计划',
    description: '继续扩展录音内容理解和片段级智能协作。',
    items: ['语音转文字', 'Agent 对话', '片段级处理建议'],
  },
]

function SectionHeading({ eyebrow, title, description, id }: { eyebrow: string; title: string; description: string; id: string }) {
  return (
    <div className="landing-section-heading">
      <span className="landing-section-heading__eyebrow">{eyebrow}</span>
      <h2 id={id}>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

export function CapabilitiesSection() {
  const sectionRef = useRef<HTMLElement>(null)
  const [selectedIssueIndex, setSelectedIssueIndex] = useState(0)
  const selectedIssue = issueMarkers[selectedIssueIndex]

  useEffect(() => {
    const revealItems = sectionRef.current?.querySelectorAll<HTMLElement>('[data-capability-reveal]')
    if (!revealItems?.length) return undefined

    if (!('IntersectionObserver' in window)) {
      revealItems.forEach((item) => item.classList.add('is-visible'))
      return undefined
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting || entry.intersectionRatio < 0.18) return
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        })
      },
      { threshold: 0.18, rootMargin: '0px 0px -10% 0px' },
    )

    revealItems.forEach((item) => observer.observe(item))
    return () => observer.disconnect()
  }, [])

  return (
    <section ref={sectionRef} className="landing-section landing-capabilities" id="capabilities" aria-labelledby="capabilities-title" data-landing-reveal>
      <div className="landing-section__inner">
        <SectionHeading
          eyebrow="CAPABILITIES · 01"
          title="从发现问题，到获得可执行建议"
          description="围绕真实的音频使用过程，帮助创作者和记录者看见问题、理解内容，并明确下一步怎么处理。"
          id="capabilities-title"
        />

        <div className="landing-capability-narrative">
          <article className="landing-value-module landing-value-module--issues" data-capability-reveal>
            <div className="landing-value-copy">
              <span className="landing-value-copy__index">01 · 发现音频问题</span>
              <h3>自动发现问题，准确定位片段</h3>
              <p>扫描整段音频，识别长静音、背景噪声、音量波动和异常声音，并将问题标记到具体时间位置。</p>
              <div className="landing-value-outcomes" aria-label="可以获得的结果">
                <span>定位时间</span><span>查看原因</span><span>回听片段</span>
              </div>
            </div>

            <div className="landing-value-visual landing-issue-preview" aria-label="音频问题检测效果预览">
              <div className="landing-preview-heading">
                <div><AudioOutlined aria-hidden="true" /><span>episode-final.wav</span></div>
                <small>效果预览</small>
              </div>
              <div className="landing-issue-waveform">
                <div className="landing-issue-waveform__bars" aria-hidden="true">
                  {waveformBars.map((height, index) => (
                    <span key={`${height}-${index}`} style={{ '--wave-height': `${height}%`, '--item-delay': `${index * 18}ms` } as CSSProperties} />
                  ))}
                </div>
                {issueMarkers.map((marker, index) => (
                  <button
                    key={marker.time}
                    type="button"
                    className={`landing-issue-marker landing-issue-marker--${marker.tone}${selectedIssueIndex === index ? ' is-active' : ''}`}
                    style={{ '--marker-position': `${marker.position}%`, '--item-delay': `${index * 90}ms` } as CSSProperties}
                    aria-label={`${marker.time} ${marker.label}：${marker.detail}`}
                    aria-pressed={selectedIssueIndex === index}
                    onClick={() => setSelectedIssueIndex(index)}
                  >
                    <i aria-hidden="true" />
                    <span>{marker.label}</span>
                  </button>
                ))}
              </div>
              <div className="landing-issue-timeline" aria-hidden="true">
                <span>00:00</span><span>10:00</span><span>20:00</span><span>30:00</span><span>42:18</span>
              </div>
              <div className="landing-issue-detail" aria-live="polite">
                <AlertOutlined aria-hidden="true" />
                <div><strong>{selectedIssue.time} · {selectedIssue.label}</strong><span>{selectedIssue.detail}</span></div>
              </div>
            </div>
          </article>

          <article className="landing-value-module landing-value-module--transcript" data-capability-reveal>
            <div className="landing-value-copy">
              <span className="landing-value-copy__index">02 · 理解录音内容</span>
              <h3>从声音到可检索的内容</h3>
              <p>将录音转换为文本，区分不同说话人，并从访谈、会议和课程录音中提取重点信息。</p>
              <div className="landing-value-outcomes" aria-label="可以获得的结果">
                <span>快速检索</span><span>提炼结论</span><span>整理待办</span>
              </div>
            </div>

            <div className="landing-value-visual landing-transcript-preview" aria-label="录音内容理解效果预览">
              <div className="landing-preview-heading">
                <div><FileTextOutlined aria-hidden="true" /><span>访谈转写</span></div>
                <small>效果预览</small>
              </div>
              <div className="landing-transcript-lines">
                {transcriptPreview.map((line, index) => (
                  <div key={line.time} className="landing-transcript-line landing-preview-sequence" style={{ '--item-delay': `${index * 90}ms` } as CSSProperties}>
                    <span className={`landing-speaker landing-speaker--${index % 2 === 0 ? 'a' : 'b'}`}>{line.speaker}</span>
                    <time>{line.time}</time>
                    <p>{line.text}</p>
                  </div>
                ))}
              </div>
              <div className="landing-transcript-insights landing-preview-sequence" style={{ '--item-delay': '300ms' } as CSSProperties}>
                <div><span>内容摘要</span><p>讨论新一季播客的选题方向、内容结构和嘉宾安排。</p></div>
                <div><span>关键结论</span><p>优先用具体实践案例组织访谈内容。</p></div>
                <div><span>待办事项</span><p><CheckCircleOutlined aria-hidden="true" /> 整理三期选题并确认首位嘉宾</p></div>
              </div>
            </div>
          </article>

          <article className="landing-value-module landing-value-module--agent" data-capability-reveal>
            <div className="landing-value-copy">
              <span className="landing-value-copy__index">03 · 生成处理建议</span>
              <h3>一句话描述目标，由 Agent 规划处理</h3>
              <p>用户可以直接告诉 AudioAgent 想删除长静音、降低噪声或整理重点，由系统结合检测结果生成片段级处理建议。</p>
              <div className="landing-value-outcomes" aria-label="可以获得的结果">
                <span>表达目标</span><span>引用片段</span><span>确认影响</span>
              </div>
            </div>

            <div className="landing-value-visual landing-agent-preview" aria-label="Agent 处理规划效果预览">
              <div className="landing-preview-heading">
                <div><RobotOutlined aria-hidden="true" /><span>AudioAgent 助手</span></div>
                <small>效果预览</small>
              </div>
              <div className="landing-agent-message landing-agent-message--user landing-preview-sequence" style={{ '--item-delay': '0ms' } as CSSProperties}>
                删除过长静音，降低访谈中的背景噪声，再帮我整理本期重点。
              </div>
              <div className="landing-agent-message landing-agent-message--assistant landing-preview-sequence" style={{ '--item-delay': '100ms' } as CSSProperties}>
                <div className="landing-agent-message__title"><BulbOutlined aria-hidden="true" /> 已生成片段级建议</div>
                <blockquote><span>引用问题片段</span>08:42 长静音 4.8 秒 · 18:16 持续背景噪声</blockquote>
                <ol>
                  <li>将 08:42 的静音缩短至约 0.8 秒</li>
                  <li>对 18:16–21:04 应用轻度降噪</li>
                  <li>提取访谈结论和后续行动项</li>
                </ol>
                <div className="landing-agent-impact"><ClockCircleOutlined aria-hidden="true" /> 预计影响 2 个片段，共约 3 分 36 秒</div>
              </div>
            </div>
          </article>
        </div>

        <div className="landing-capability-strip" data-capability-reveal aria-label="AudioAgent 能力概览">
          <span className="landing-capability-strip__title">能力概览</span>
          <div className="landing-capability-strip__items">
            {compactCapabilities.map((capability, index) => (
              <span key={capability.name} className="landing-capability-strip__item" style={{ '--item-delay': `${index * 45}ms` } as CSSProperties} title={capability.name}>
                <i aria-hidden="true">{capability.icon}</i>{capability.name}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}

export function ScenariosSection() {
  return (
    <section className="landing-section landing-scenarios" id="scenarios" aria-labelledby="scenarios-title" data-landing-reveal>
      <div className="landing-section__inner">
        <SectionHeading
          eyebrow="SCENARIOS · 03"
          title="适合需要理解长音频的工作场景"
          description="以更少的重复回听，帮助内容创作者和知识工作者快速掌握录音质量与内容线索。"
          id="scenarios-title"
        />
        <div className="landing-section-visual landing-scenario-grid">
          {scenarios.map((scenario, index) => (
            <article key={scenario.name} className="landing-scenario-card" style={{ '--reveal-delay': `${index * 100}ms` } as CSSProperties}>
              <span className="landing-card-icon landing-card-icon--large" aria-hidden="true">{scenario.icon}</span>
              <h3>{scenario.name}</h3>
              <p>{scenario.description}</p>
              <div className="landing-scenario-card__problem">
                <span>可解决的问题</span>
                <strong>{scenario.problem}</strong>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

export function RoadmapSection() {
  return (
    <section className="landing-section landing-roadmap" id="roadmap" aria-labelledby="roadmap-title" data-landing-reveal>
      <div className="landing-section__inner">
        <SectionHeading
          eyebrow="ROADMAP · 04"
          title="透明呈现产品建设路线"
          description="按用户能够获得的结果呈现能力演进。下一阶段和后续计划仅代表产品方向，不表示当前已经可用。"
          id="roadmap-title"
        />
        <div className="landing-section-visual landing-roadmap-grid">
          {roadmap.map((stage, index) => (
            <article
              key={stage.title}
              className={`landing-roadmap-card landing-roadmap-card--${stage.status}`}
              style={{ '--reveal-delay': `${index * 100}ms` } as CSSProperties}
            >
              <div className="landing-roadmap-card__header">
                <span className="landing-roadmap-card__index">0{index + 1}</span>
                <span className="landing-status-pill">{stage.title}</span>
              </div>
              <h3>{stage.title}</h3>
              <p>{stage.description}</p>
              <ul>
                {stage.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

export function BottomActionSection() {
  return (
    <section className="landing-bottom-action" aria-labelledby="bottom-action-title">
      <div className="landing-bottom-action__glow" aria-hidden="true" />
      <div className="landing-bottom-action__content">
        <span>READY WHEN YOU ARE</span>
        <h2 id="bottom-action-title">从一段音频开始，建立清晰的分析流程</h2>
        <p>上传音频查看已上线的文件管理、元数据分析与异步任务能力。</p>
        <div className="landing-bottom-action__buttons">
          <AnimatedBorderButton to="/audio/upload">开始分析音频</AnimatedBorderButton>
          <AnimatedBorderButton to="/dashboard" variant="secondary">进入工作台</AnimatedBorderButton>
        </div>
      </div>
    </section>
  )
}
