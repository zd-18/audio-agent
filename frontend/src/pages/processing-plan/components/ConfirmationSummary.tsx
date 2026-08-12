import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
} from '@ant-design/icons'
import type { ProcessingConfirmation } from '../../../types/processingConfirmation'
import { formatDateTime } from '../../../utils/formatters'
import { CONFIRMATION_STATUS_META } from '../../../utils/processingConfirmationDisplay'

export default function ConfirmationSummary({
  confirmation,
}: { confirmation: ProcessingConfirmation }) {
  const meta = CONFIRMATION_STATUS_META[confirmation.confirmationStatus]

  return (
    <section
      className={`processing-confirmation-summary is-${confirmation.confirmationStatus.toLowerCase()}`}
      aria-labelledby="processing-confirmation-summary-title"
    >
      <div className="processing-plan-section-heading">
        <div>
          <span>CONFIRMATION OVERVIEW</span>
          <h2 id="processing-confirmation-summary-title">确认摘要</h2>
          <p>{meta.description}</p>
        </div>
        <span className={`processing-confirmation-status is-${confirmation.confirmationStatus.toLowerCase()}`}>
          {confirmation.confirmationStatus === 'CONFIRMED'
            ? <CheckCircleOutlined aria-hidden="true" />
            : confirmation.confirmationStatus === 'CANCELLED'
              ? <CloseCircleOutlined aria-hidden="true" />
              : confirmation.confirmationStatus === 'DRAFT'
                ? <EditOutlined aria-hidden="true" />
                : <ClockCircleOutlined aria-hidden="true" />}
          {meta.label}
        </span>
      </div>

      <dl className="processing-confirmation-summary__metrics">
        <div className="is-accepted"><dt>已接受</dt><dd>{confirmation.acceptedStepCount}</dd></div>
        <div className="is-rejected"><dt>暂不处理</dt><dd>{confirmation.rejectedStepCount}</dd></div>
        <div className="is-pending"><dt>待决定</dt><dd>{confirmation.pendingStepCount}</dd></div>
        <div><dt>方案修订</dt><dd>R{confirmation.sourcePlanRevision}</dd></div>
      </dl>

      <dl className="processing-confirmation-summary__meta">
        <div><dt>最后更新</dt><dd>{formatDateTime(confirmation.updatedAt || undefined)}</dd></div>
        {confirmation.confirmedAt && (
          <div><dt>最终提交</dt><dd>{formatDateTime(confirmation.confirmedAt)}</dd></div>
        )}
      </dl>
    </section>
  )
}
