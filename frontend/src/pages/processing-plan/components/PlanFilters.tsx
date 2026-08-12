import { FilterOutlined } from '@ant-design/icons'
import { Radio } from 'antd'

export type ProcessingPlanFilter =
  | 'ALL'
  | 'HIGH'
  | 'MEDIUM'
  | 'CONFIRMATION'
  | 'SEGMENT'
  | 'WHOLE_AUDIO'

const FILTER_OPTIONS: Array<{ value: ProcessingPlanFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'HIGH', label: '优先处理' },
  { value: 'MEDIUM', label: '建议处理' },
  { value: 'CONFIRMATION', label: '需要确认' },
  { value: 'SEGMENT', label: '片段处理' },
  { value: 'WHOLE_AUDIO', label: '整体处理' },
]

interface PlanFiltersProps {
  value: ProcessingPlanFilter
  onChange: (value: ProcessingPlanFilter) => void
}

export default function PlanFilters({ value, onChange }: PlanFiltersProps) {
  return (
    <div className="processing-plan-filters" aria-label="处理步骤筛选">
      <span><FilterOutlined /> 筛选步骤</span>
      <Radio.Group
        value={value}
        optionType="button"
        buttonStyle="solid"
        options={FILTER_OPTIONS}
        onChange={(event) => onChange(event.target.value as ProcessingPlanFilter)}
      />
    </div>
  )
}
