import { AudioOutlined, ClockCircleOutlined, DatabaseOutlined, SoundOutlined } from '@ant-design/icons'
import { Tooltip } from 'antd'
import type { AudioOverview } from '../../../types/analysisReport'
import { formatDuration } from '../../../utils/audioTime'

function displayText(value: string | null | undefined) {
  return value?.trim() || '—'
}

function formatChannels(channels: number | null) {
  if (channels === null || !Number.isFinite(channels)) return '—'
  if (channels === 1) return '单声道'
  if (channels === 2) return '双声道'
  return `${channels} 声道`
}

export default function AudioOverviewPanel({ overview }: { overview: AudioOverview }) {
  const fileName = displayText(overview.fileName)
  const items = [
    { key: 'duration', icon: <ClockCircleOutlined />, label: '时长', value: formatDuration(overview.durationMs) },
    { key: 'format', icon: <DatabaseOutlined />, label: '格式', value: displayText(overview.format) },
    { key: 'codec', icon: <AudioOutlined />, label: '编码', value: displayText(overview.codec) },
    { key: 'rate', icon: <SoundOutlined />, label: '采样率', value: overview.sampleRate === null ? '—' : `${overview.sampleRate.toLocaleString('zh-CN')} Hz` },
    { key: 'channels', icon: <SoundOutlined />, label: '声道', value: formatChannels(overview.channels) },
  ]

  return (
    <section className="report-info-panel report-audio-overview" aria-labelledby="audio-overview-title">
      <div className="report-section-heading">
        <div><span className="report-section-kicker">AUDIO OVERVIEW</span><h2 id="audio-overview-title">音频概览</h2></div>
      </div>
      <div className="report-audio-overview__file">
        <span><AudioOutlined /></span>
        <div>
          <small>文件名称</small>
          <Tooltip title={overview.fileName || undefined}>
            <strong>{fileName}</strong>
          </Tooltip>
        </div>
      </div>
      <dl className="report-compact-metrics">
        {items.map((item) => (
          <div key={item.key}>
            <dt>{item.icon}<span>{item.label}</span></dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
