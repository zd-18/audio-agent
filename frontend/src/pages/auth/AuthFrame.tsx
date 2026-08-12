import { AudioOutlined, SafetyCertificateOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import './auth.css'

interface AuthFrameProps {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
}

export default function AuthFrame({ eyebrow, title, description, children }: AuthFrameProps) {
  return (
    <main className="auth-shell">
      <div className="auth-shell__aurora auth-shell__aurora--purple" aria-hidden="true" />
      <div className="auth-shell__aurora auth-shell__aurora--cyan" aria-hidden="true" />
      <header className="auth-brandbar">
        <Link className="auth-brand" to="/" aria-label="返回 AudioAgent 产品首页">
          <span className="auth-brand__mark" aria-hidden="true"><i /><i /><i /><i /><i /></span>
          <span><strong>AudioAgent</strong><small>INTELLIGENT AUDIO WORKSPACE</small></span>
        </Link>
        <Link className="auth-brandbar__back" to="/">返回产品首页</Link>
      </header>

      <section className="auth-stage">
        <aside className="auth-story" aria-label="AudioAgent 能力介绍">
          <span className="auth-story__eyebrow">AUDIO INTELLIGENCE</span>
          <h1>让每一段声音，<br /><em>回到理想状态。</em></h1>
          <p>在一个连贯的工作区里完成音频检测、问题定位、处理确认与修复结果对比。</p>
          <div className="auth-signal" aria-hidden="true">
            {Array.from({ length: 28 }, (_, index) => <i key={index} />)}
          </div>
          <ul className="auth-capabilities">
            <li><AudioOutlined /><span><strong>精细分析</strong><small>定位静音、响度与噪声风险</small></span></li>
            <li><ThunderboltOutlined /><span><strong>可靠处理</strong><small>确认后执行可追溯修复流程</small></span></li>
            <li><SafetyCertificateOutlined /><span><strong>私有工作区</strong><small>你的文件与任务保持用户隔离</small></span></li>
          </ul>
        </aside>

        <section className="auth-card" aria-labelledby="auth-page-title">
          <div className="auth-card__heading">
            <span>{eyebrow}</span>
            <h2 id="auth-page-title">{title}</h2>
            <p>{description}</p>
          </div>
          {children}
        </section>
      </section>
    </main>
  )
}
