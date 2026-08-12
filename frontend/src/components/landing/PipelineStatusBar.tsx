import {
  AimOutlined,
  BulbOutlined,
  CloudUploadOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  SolutionOutlined,
} from '@ant-design/icons'
import type { CSSProperties, ReactNode } from 'react'

const pipelineSteps: Array<{ label: string; description: string; icon: ReactNode }> = [
  { label: '上传音频', description: '选择播客、访谈、会议或课程录音。', icon: <CloudUploadOutlined /> },
  { label: '读取音频信息', description: '识别时长、格式、声道和基础音频信息。', icon: <FileSearchOutlined /> },
  { label: '定位问题片段', description: '标记静音、噪声、音量波动等位置。', icon: <AimOutlined /> },
  { label: '理解录音内容', description: '整理转写文本、说话人和重点内容。', icon: <FileTextOutlined /> },
  { label: '生成处理建议', description: '结合问题片段生成可执行的优化建议。', icon: <BulbOutlined /> },
  { label: '查看分析结果', description: '通过报告查看问题、片段和处理方案。', icon: <SolutionOutlined /> },
]

export default function PipelineStatusBar() {
  return (
    <section className="landing-section landing-pipeline-shell" id="workflow" aria-labelledby="landing-pipeline-title" data-landing-reveal>
      <div className="landing-section__inner">
        <div className="landing-section-heading">
          <span className="landing-section-heading__eyebrow">WORKFLOW · 02</span>
          <h2 id="landing-pipeline-title">从上传到结果，一条清楚的处理链路</h2>
          <p>以下为 AudioAgent 的完整产品处理路径。</p>
        </div>
        <span id="analysis-example" className="landing-inline-anchor" aria-hidden="true" />
        <div className="landing-section-visual landing-pipeline-scroll" role="region" aria-label="AudioAgent 音频处理产品流程">
          <ol className="landing-pipeline">
            {pipelineSteps.map((step, index) => (
              <li
                key={step.label}
                className="landing-pipeline__step"
                style={{ '--reveal-delay': `${index * 100}ms` } as CSSProperties}
              >
                <span className="landing-pipeline__point" aria-hidden="true">
                  {step.icon}
                </span>
                <div>
                  <small>0{index + 1}</small>
                  <strong>{step.label}</strong>
                  <p>{step.description}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  )
}
