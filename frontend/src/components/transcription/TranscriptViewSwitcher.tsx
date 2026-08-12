export type TranscriptViewMode = 'compact' | 'sentence'

interface TranscriptViewSwitcherProps {
  value: TranscriptViewMode
  onChange: (value: TranscriptViewMode) => void
}

const OPTIONS: Array<{ label: string; value: TranscriptViewMode }> = [
  { label: '简洁视图', value: 'compact' },
  { label: '逐句视图', value: 'sentence' },
]

export default function TranscriptViewSwitcher({
  value,
  onChange,
}: TranscriptViewSwitcherProps) {
  return (
    <div className="transcript-view-switcher" role="group" aria-label="文字稿片段视图">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          className={value === option.value ? 'is-selected' : ''}
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
