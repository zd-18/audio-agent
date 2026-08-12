import { AlertOutlined, ExpandOutlined, RiseOutlined } from '@ant-design/icons'
import type { LoudnessOverview } from '../../../types/analysisReport'

function formatMetric(value: number | undefined, unit: string) {
  return typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(2)} ${unit}` : '—'
}

const LOUDNESS_LABELS = {
  LOW: '整体音量偏低',
  NORMAL: '整体音量正常',
  HIGH: '整体音量偏高',
} as const

const PEAK_LABELS = {
  NORMAL: '峰值处于正常范围',
  RISK: '存在峰值风险',
} as const

const DYNAMIC_LABELS = {
  NARROW: '动态变化较小',
  NORMAL: '动态范围正常',
  WIDE: '动态变化较大',
} as const

export default function LoudnessOverviewPanel({ overview }: { overview?: LoudnessOverview }) {
  return (
    <section className="report-info-panel report-loudness" aria-labelledby="loudness-overview-title">
      <div className="report-section-heading">
        <div><span className="report-section-kicker">LOUDNESS</span><h2 id="loudness-overview-title">响度概览</h2></div>
      </div>
      {!overview ? (
        <div className="report-inline-empty"><SoundWaveIcon /><span>暂无响度数据</span></div>
      ) : (
        <>
          <dl className="report-loudness__metrics">
            <div><dt>综合响度</dt><dd>{formatMetric(overview.integratedLoudnessLufs, 'LUFS')}</dd></div>
            <div><dt>响度范围</dt><dd>{formatMetric(overview.loudnessRangeLu, 'LU')}</dd></div>
            <div><dt>真实峰值</dt><dd>{formatMetric(overview.truePeakDbfs, 'dBFS')}</dd></div>
          </dl>
          <div className="report-loudness__states">
            <span><RiseOutlined />{overview.loudnessLevel ? LOUDNESS_LABELS[overview.loudnessLevel] : '整体响度状态未提供'}</span>
            <span className={overview.peakRisk === 'RISK' ? 'is-risk' : ''}><AlertOutlined />{overview.peakRisk ? PEAK_LABELS[overview.peakRisk] : '峰值状态未提供'}</span>
            <span><ExpandOutlined />{overview.dynamicRangeLevel ? DYNAMIC_LABELS[overview.dynamicRangeLevel] : '动态范围状态未提供'}</span>
          </div>
        </>
      )}
    </section>
  )
}

function SoundWaveIcon() {
  return <span className="report-inline-empty__wave" aria-hidden="true"><i /><i /><i /><i /><i /></span>
}
