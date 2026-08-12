import AnalysisPreviewCard from './AnalysisPreviewCard'
import AnimatedBorderButton from './AnimatedBorderButton'
import AudioProcessingNetwork from './AudioProcessingNetwork'
import DynamicIssueHeading from './DynamicIssueHeading'

export default function HeroSection() {
  return (
    <section className="landing-hero" id="home" aria-label="AudioAgent 产品介绍">
      <section className="landing-hero__copy" aria-labelledby="landing-heading">
        <div className="landing-eyebrow">
          <span className="landing-eyebrow__dot" aria-hidden="true" />
          AUDIO INTELLIGENCE, ORCHESTRATED
        </div>
        <DynamicIssueHeading />
        <p className="landing-hero__description">
          AudioAgent 自动定位音频中的问题片段，并根据自然语言要求规划处理流程，为播客、访谈和会议录音生成清晰的分析结果。
        </p>

        <div className="landing-hero__actions">
          <AnimatedBorderButton to="/audio/upload">开始分析音频</AnimatedBorderButton>
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
