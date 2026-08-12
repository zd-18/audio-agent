import {
  AudioOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HighlightOutlined,
  SoundOutlined,
  ThunderboltOutlined,
  UserSwitchOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons'
import AudioToolNode from './AudioToolNode'

const spectrumBars = Array.from({ length: 24 }, (_, index) => index)
const waveformBars = [44, 72, 38, 84, 58, 96, 66, 48, 78, 42, 68]

export default function AudioProcessingNetwork() {
  return (
    <div className="landing-network-viewport">
      <div className="landing-network" role="group" aria-label="AudioAgent 不对称音频处理网络">
        <div className="landing-network__halo" aria-hidden="true" />
        <div className="landing-network__arc landing-network__arc--one" aria-hidden="true" />
        <div className="landing-network__arc landing-network__arc--two" aria-hidden="true" />
        <div className="landing-network__arc landing-network__arc--three" aria-hidden="true" />
        <div className="landing-network__arc landing-network__arc--four" aria-hidden="true" />

        <svg className="landing-network__connections" viewBox="0 0 720 620" aria-hidden="true">
          <path d="M115 100 C240 94 256 230 338 280" />
          <path d="M470 68 C448 158 405 190 360 270" />
          <path d="M590 184 C500 196 476 246 378 288" />
          <path d="M625 430 C520 403 468 352 385 315" />
          <path d="M448 528 C430 435 399 388 355 330" />
          <path d="M122 476 C190 410 226 354 315 315" />
          <path d="M52 302 C158 298 230 292 310 294" />
          <path d="M360 298 C415 274 435 238 454 204" />
        </svg>

        <div className="landing-network__scan" aria-hidden="true">
          <span />
        </div>

        <AudioToolNode position={1} size="large" color="blue" icon={<FileSearchOutlined />} label="元数据分析" description="读取时长、编码、声道和采样率" />
        <AudioToolNode position={2} color="purple" icon={<AudioOutlined />} label="语音转文字" description="将对话转换为可搜索文本" />
        <AudioToolNode position={3} size="large" color="cyan" icon={<VideoCameraOutlined />} label="静音检测" description="定位过长停顿与空白片段" />
        <AudioToolNode position={4} color="orange" icon={<ThunderboltOutlined />} label="噪声分析" description="识别持续底噪与突发干扰" />
        <AudioToolNode position={5} size="large" color="blue" icon={<SoundOutlined />} label="响度检测" description="发现音量波动与响度异常" />
        <AudioToolNode position={6} size="small" color="purple" icon={<UserSwitchOutlined />} label="说话人识别" description="区分访谈和会议中的不同角色" />
        <AudioToolNode position={7} color="cyan" icon={<FileTextOutlined />} label="内容摘要" description="提炼讨论重点与行动信息" />
        <AudioToolNode position={8} size="small" color="orange" icon={<HighlightOutlined />} label="修复建议" description="根据目标规划可执行处理步骤" />

        <div className="landing-audio-core" aria-label="AudioAgent 智能音频处理核心">
          <div className="landing-audio-core__spectrum" aria-hidden="true">
            {spectrumBars.map((index) => (
              <span key={index} style={{ '--bar-index': index } as React.CSSProperties}><i /></span>
            ))}
          </div>
          <div className="landing-audio-core__disc">
            <div className="landing-audio-core__wave" aria-hidden="true">
              {waveformBars.map((height, index) => (
                <i key={`${height}-${index}`} style={{ '--wave-height': `${height}%`, '--wave-delay': `${index * -0.08}s` } as React.CSSProperties} />
              ))}
            </div>
            <strong>AudioAgent</strong>
            <span>智能音频处理核心</span>
            <div className="landing-core-status" aria-label="当前处理状态动态展示：Listening、Analyzing、Planning、Processing">
              {['Listening', 'Analyzing', 'Planning', 'Processing'].map((status, index) => (
                <small key={status} style={{ '--status-index': index } as React.CSSProperties}>{status}</small>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
