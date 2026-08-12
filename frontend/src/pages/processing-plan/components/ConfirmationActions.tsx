import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, Button, Modal } from 'antd'
import { useMemo, useState } from 'react'
import type { ProcessingConfirmation } from '../../../types/processingConfirmation'
import { getProcessingConfirmationErrorMessage } from '../../../utils/processingConfirmationDisplay'

interface ConfirmationActionsProps {
  confirmation: ProcessingConfirmation | null
  loading: boolean
  creating: boolean
  confirming: boolean
  cancelling: boolean
  saving: boolean
  dirty: boolean
  onCreate: () => Promise<ProcessingConfirmation | null>
  onCreateLatest: (status: 'STALE' | 'CANCELLED') => Promise<ProcessingConfirmation | null>
  onConfirm: () => Promise<ProcessingConfirmation | null>
  onCancel: () => Promise<ProcessingConfirmation | null>
}

export default function ConfirmationActions({
  confirmation,
  loading,
  creating,
  confirming,
  cancelling,
  saving,
  dirty,
  onCreate,
  onCreateLatest,
  onConfirm,
  onCancel,
}: ConfirmationActionsProps) {
  const [modal, modalContext] = Modal.useModal()
  const [actionError, setActionError] = useState<string | null>(null)
  const unconfirmedAcceptedCount = useMemo(() => (
    confirmation?.steps.filter((step) => (
      step.decision === 'ACCEPTED' && step.requiresConfirmation && !step.userConfirmed
    )).length || 0
  ), [confirmation])
  const disabledReasons = useMemo(() => {
    const reasons: string[] = []
    if (!confirmation || confirmation.confirmationStatus !== 'DRAFT') return reasons
    if (confirmation.pendingStepCount > 0) {
      reasons.push(`还有 ${confirmation.pendingStepCount} 条建议尚未决定`)
    }
    if (unconfirmedAcceptedCount > 0) {
      reasons.push(`还有 ${unconfirmedAcceptedCount} 条已接受建议需要试听确认`)
    }
    if (saving) reasons.push('部分修改正在保存')
    if (dirty) reasons.push('当前步骤有修改尚未保存')
    return reasons
  }, [confirmation, dirty, saving, unconfirmedAcceptedCount])

  const run = async (action: () => Promise<ProcessingConfirmation | null>) => {
    setActionError(null)
    try {
      return await action()
    } catch (error) {
      setActionError(getProcessingConfirmationErrorMessage(error))
      throw error
    }
  }

  const requestCreate = () => {
    modal.confirm({
      title: '开始确认处理方案',
      content: '系统将创建一份确认草稿。你可以接受、拒绝或调整建议，不会直接修改原始音频。',
      okText: '创建确认草稿',
      cancelText: '取消',
      centered: true,
      onOk: () => run(onCreate),
    })
  }

  if (loading && !confirmation) {
    return <section className="processing-confirmation-actions is-loading" aria-label="正在加载确认状态" />
  }

  if (!confirmation) {
    return (
      <section className="processing-confirmation-actions processing-confirmation-start">
        {modalContext}
        <div>
          <span>CONFIRMATION</span>
          <h2>开始确认处理方案</h2>
          <p>创建草稿后可逐项决定、调整允许修改的参数并保存备注。</p>
        </div>
        <Button
          type="primary"
          size="large"
          icon={<EditOutlined />}
          loading={creating}
          disabled={creating}
          onClick={requestCreate}
        >
          开始确认处理方案
        </Button>
        {actionError && <Alert type="error" showIcon message={actionError} />}
      </section>
    )
  }

  if (confirmation.confirmationStatus === 'CONFIRMED') {
    return (
      <section className="processing-confirmation-actions">
        {modalContext}
        <Alert
          type="success"
          showIcon
          message="最终方案已确认"
          description="所有决定已锁定。本次确认不会立即处理音频。"
        />
      </section>
    )
  }

  if (confirmation.confirmationStatus === 'STALE' || confirmation.confirmationStatus === 'CANCELLED') {
    const status = confirmation.confirmationStatus
    const stale = status === 'STALE'
    return (
      <section className="processing-confirmation-actions">
        {modalContext}
        <Alert
          type={stale ? 'warning' : 'info'}
          showIcon
          message={stale ? '当前确认草稿不能继续提交' : '当前确认草稿已取消'}
          description={stale
            ? '处理方案已经重新生成，当前草稿基于旧版本。新草稿不会迁移旧决定或参数。'
            : '后端不允许在同一修订上重开已取消记录；系统将先生成新的方案修订，再创建草稿。'}
          action={(
            <Button
              icon={<ReloadOutlined />}
              loading={creating}
              disabled={creating}
              onClick={() => {
                modal.confirm({
                  title: '基于最新方案重新创建确认草稿',
                  content: '旧步骤决定和参数不会自动迁移到新草稿。',
                  okText: '重新创建',
                  cancelText: '取消',
                  centered: true,
                  onOk: () => run(() => onCreateLatest(status)),
                })
              }}
            >
              基于最新方案重新创建确认草稿
            </Button>
          )}
        />
        {actionError && <Alert type="error" showIcon message={actionError} />}
      </section>
    )
  }

  const allRejected = confirmation.acceptedStepCount === 0 && confirmation.pendingStepCount === 0
  const canConfirm = disabledReasons.length === 0

  return (
    <section className="processing-confirmation-actions">
      {modalContext}
      <div className="processing-confirmation-actions__heading">
        <div>
          <span>FINAL REVIEW</span>
          <h2>提交最终方案</h2>
          <p>提交后将锁定当前决定，但仍不会立即处理音频。</p>
        </div>
        <div className="processing-confirmation-actions__buttons">
          <Button
            danger
            icon={<CloseCircleOutlined />}
            loading={cancelling}
            disabled={confirming || cancelling || saving}
            onClick={() => {
              modal.confirm({
                title: '取消确认草稿',
                content: '取消后当前决定将无法继续编辑，系统处理方案不会被删除。',
                okText: '确认取消草稿',
                cancelText: '返回',
                okButtonProps: { danger: true },
                centered: true,
                onOk: () => run(onCancel),
              })
            }}
          >
            取消确认草稿
          </Button>
          <Button
            type="primary"
            size="large"
            icon={<CheckCircleOutlined />}
            loading={confirming}
            disabled={!canConfirm || confirming || cancelling}
            onClick={() => {
              modal.confirm({
                title: '提交最终方案',
                content: allRejected
                  ? '你已选择不接受任何处理建议，提交后不会产生可执行处理步骤。提交后将锁定当前决定，且不会立即处理音频。'
                  : '提交后将锁定当前决定，不能继续修改。本操作仍不会立即处理音频。',
                okText: '确认提交',
                cancelText: '返回检查',
                centered: true,
                onOk: () => run(onConfirm),
              })
            }}
          >
            提交最终方案
          </Button>
        </div>
      </div>
      {disabledReasons.length > 0 && (
        <ul className="processing-confirmation-actions__reasons" aria-label="暂时不能提交的原因">
          {disabledReasons.map((reason) => <li key={reason}>{reason}</li>)}
        </ul>
      )}
      {allRejected && canConfirm && (
        <Alert type="info" showIcon message="当前全部建议均选择暂不处理；仍可提交最终确认。" />
      )}
      {actionError && <Alert type="error" showIcon message={actionError} />}
    </section>
  )
}
