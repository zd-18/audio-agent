import type { AudioAnalysisResult } from '../../types/api'
import { formatBitRate, formatBytes, formatChannels, formatDuration } from '../../utils/formatters'

export default function AudioMetadataGrid({ result }: { result: AudioAnalysisResult }) {
  const items = [
    ['封装格式', result.formatName || '—'],
    ['音频编码', result.codecName || '—'],
    ['音频时长', formatDuration(result.durationMs)],
    ['采样率', result.sampleRate === undefined ? '—' : `${result.sampleRate.toLocaleString('zh-CN')} Hz`],
    ['声道', formatChannels(result.channels)],
    ['比特率', formatBitRate(result.bitRate)],
    ['文件大小', formatBytes(result.fileSize)],
  ]
  return <div className="analysis-metadata-grid">{items.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>
}
