const issueWords = ['静音', '噪声', '爆音', '音量异常']

export default function DynamicIssueHeading() {
  return (
    <div className="landing-heading-block">
      <h1 className="landing-hero__heading" id="landing-heading">
        让声音更清楚，
        <br />
        <span>让问题被看见。</span>
      </h1>
      <p className="landing-issue-rotator" aria-label="自动发现静音、噪声、爆音和音量异常">
        <span aria-hidden="true" className="landing-issue-rotator__label">自动发现</span>
        <span aria-hidden="true" className="landing-issue-rotator__words">
          {issueWords.map((word, index) => (
            <span key={word} style={{ '--word-index': index } as React.CSSProperties}>
              {word}
            </span>
          ))}
        </span>
        <span aria-hidden="true" className="landing-issue-rotator__suffix">问题</span>
      </p>
    </div>
  )
}
