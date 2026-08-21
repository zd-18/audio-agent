import { DownOutlined } from '@ant-design/icons'
import { Collapse } from 'antd'
import type { ProcessingStep } from '../../../types/processingPlan'
import { getParameterDisplayItems } from '../../../utils/processingPlanDisplay'

export default function StepParameters({ step }: { step: ProcessingStep }) {
  const items = getParameterDisplayItems(step)
  const summaryItems = items.slice(0, 2)

  return (
    <div className="processing-step-parameter-disclosure">
      <dl className="processing-step-parameters" aria-label="参数摘要">
        {summaryItems.map((item) => (
          <div key={`${item.label}-${item.value}`}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
      <Collapse
        ghost
        expandIconPosition="end"
        expandIcon={({ isActive }) => <DownOutlined rotate={isActive ? 180 : 0} />}
        items={[{
          key: 'advanced',
          label: '高级参数',
          children: (
            <dl className="processing-step-parameters is-advanced">
              {items.map((item) => (
                <div key={`${item.label}-${item.value}`}>
                  <dt>{item.label}</dt>
                  <dd>{item.value}</dd>
                </div>
              ))}
            </dl>
          ),
        }]}
      />
    </div>
  )
}
