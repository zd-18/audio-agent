import { CheckCircleFilled, LineChartOutlined } from '@ant-design/icons'

const issueSummary = [
  ['静音', '3 段', 'purple'],
  ['背景噪声', '2 段', 'cyan'],
  ['音量异常', '1 段', 'orange'],
]

export default function AnalysisPreviewCard() {
  return (
    <article className="landing-analysis-card" aria-label="模拟音频问题分析结果">
      <header className="landing-analysis-card__header">
        <div>
          <span className="landing-analysis-card__icon" aria-hidden="true"><LineChartOutlined /></span>
          <div>
            <small>ANALYSIS PREVIEW</small>
            <strong>检测到 6 个问题片段</strong>
          </div>
        </div>
        <div className="landing-analysis-card__badges">
          <span className="landing-analysis-card__score">音频质量 <strong>82</strong></span>
          <span className="landing-analysis-card__mock">MOCK</span>
        </div>
      </header>

      <div className="landing-analysis-card__body">
        <div className="landing-analysis-card__rows">
          {issueSummary.map(([name, value, color]) => (
            <div key={name}>
              <span><i className={`landing-analysis-card__dot landing-analysis-card__dot--${color}`} />{name}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>

        <div className="landing-mini-waveform">
          <div className="landing-mini-waveform__meta">
            <span><CheckCircleFilled /> 扫描完成</span>
            <small>03:24</small>
          </div>
          <svg viewBox="0 0 290 68" role="img" aria-label="带六个问题位置标记的音频波形">
            <defs>
              <linearGradient id="landing-wave-gradient" x1="0" x2="1">
                <stop offset="0" stopColor="#4F7CFF" stopOpacity="0.45" />
                <stop offset="0.5" stopColor="#35D9C5" />
                <stop offset="1" stopColor="#A068FF" stopOpacity="0.55" />
              </linearGradient>
            </defs>
            <path
              className="landing-mini-waveform__line"
              d="M2 35 C12 34 16 18 24 34 S38 56 46 34 S58 8 66 34 S78 52 86 34 S100 22 108 34 S120 61 130 34 S144 13 152 34 S166 48 174 34 S188 17 196 34 S210 55 218 34 S234 24 242 34 S258 10 266 34 S280 42 288 34"
              fill="none"
              stroke="url(#landing-wave-gradient)"
              strokeWidth="2"
            />
            {[32, 68, 113, 158, 221, 266].map((x, index) => (
              <g key={x} className={`landing-mini-waveform__marker landing-mini-waveform__marker--${index % 3}`}>
                <line x1={x} y1="13" x2={x} y2="55" />
                <circle cx={x} cy="13" r="3" />
              </g>
            ))}
          </svg>
        </div>
      </div>
    </article>
  )
}
