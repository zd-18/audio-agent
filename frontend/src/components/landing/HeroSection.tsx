import AnalysisPreviewCard from './AnalysisPreviewCard'
import AnimatedBorderButton from './AnimatedBorderButton'
import AudioProcessingNetwork from './AudioProcessingNetwork'
import DynamicIssueHeading from './DynamicIssueHeading'
import { useAuth } from '../../auth/AuthContext'

export default function HeroSection() {
  const { authStatus, currentUser } = useAuth()
  const isAuthenticated = authStatus === 'authenticated' && currentUser !== null

  return (
    <section className="landing-hero" id="home" aria-label="AudioAgent 产品介绍">
      <section className="landing-hero__copy" aria-labelledby="landing-heading">
        <div className="landing-eyebrow">
          <span className="landing-eyebrow__dot" aria-hidden="true" />
          AUDIO INTELLIGENCE, ORCHESTRATED
        </div>
        <DynamicIssueHeading />
        <p className="landing-hero__description">
          AudioAgent 自动定位音频中的问题片段，并根据自然语言要求规划处理流程，为<span className="landing-hero__description-term">播客</span>、访谈和<span className="landing-hero__description-term">会议录音</span>生成清晰的分析结果。
        </p>

        <div className="landing-hero__actions">
          <AnimatedBorderButton
            to={isAuthenticated ? '/audio/upload' : '/login'}
            state={isAuthenticated ? undefined : { from: '/audio/upload' }}
          >
            开始智能诊断
          </AnimatedBorderButton>
          <AnimatedBorderButton href="#analysis-example" variant="secondary">
            查看分析示例
          </AnimatedBorderButton>
        </div>

        <AnalysisPreviewCard />
      </section>

      <section className="landing-hero__visual" aria-label="AudioAgent 音频处理网络示意图">
        <AudioProcessingNetwork />
      </section>
    </section>
  )
}
