import { CheckCircleOutlined } from '@ant-design/icons'
import { Alert, Input, Modal } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import type { ProcessingPlan } from '../../../types/processingPlan'
import {
  getParameterDisplayItems,
  getProcessingTypeLabel,
} from '../../../utils/processingPlanDisplay'

interface ProcessingPlanConfirmationModalProps {
  open: boolean
  audioName: string
  plan: ProcessingPlan
  starting: boolean
  error?: string | null
  onCancel: () => void
  onStart: (note: string) => Promise<void>
}

export default function ProcessingPlanConfirmationModal({
  open,
  audioName,
  plan,
  starting,
  error,
  onCancel,
  onStart,
}: ProcessingPlanConfirmationModalProps) {
  const [note, setNote] = useState('')
  const coreParameters = useMemo(() => plan.steps.flatMap((step) => (
    getParameterDisplayItems(step).slice(0, 2).map((item) => ({
      key: `${step.stepId}-${item.label}`,
      step: getProcessingTypeLabel(step.operationType),
      ...item,
    }))
  )).slice(0, 6), [plan.steps])

  useEffect(() => {
    if (!open) setNote('')
  }, [open])

  return (
    <Modal
      className="processing-plan-confirm-modal"
      open={open}
      title={<span className="processing-plan-confirm-modal__title"><CheckCircleOutlined /> 确认处理方案</span>}
      okText="开始处理"
      cancelText="取消"
      centered
      width={620}
      confirmLoading={starting}
      okButtonProps={{ disabled: starting }}
      cancelButtonProps={{ disabled: starting }}
      closable={!starting}
      maskClosable={!starting}
      keyboard={!starting}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => onStart(note)}
    >
      <div className="processing-plan-confirm-modal__content">
        {error && <Alert type="error" showIcon message="启动失败" description={error} />}
        <dl className="processing-plan-confirm-modal__overview">
          <div>
            <dt>音频名称</dt>
            <dd title={audioName}>{audioName}</dd>
          </div>
          <div>
            <dt>当前处理方案</dt>
            <dd>{plan.summary || `按顺序执行 ${plan.stepCount} 个音频优化步骤`}</dd>
          </div>
        </dl>

        <section aria-labelledby="processing-plan-core-parameters">
          <h3 id="processing-plan-core-parameters">核心处理参数</h3>
          <dl className="processing-plan-confirm-modal__parameters">
            {coreParameters.map((item) => (
              <div key={item.key}>
                <dt>{item.step} · {item.label}</dt>
                <dd>{item.value}</dd>
              </div>
            ))}
          </dl>
        </section>

        <div className="processing-plan-confirm-modal__note">
          <label htmlFor="processing-plan-confirm-note">可选备注</label>
          <Input.TextArea
            id="processing-plan-confirm-note"
            value={note}
            rows={3}
            maxLength={500}
            showCount
            disabled={starting}
            placeholder="补充本次处理要求（选填）"
            onChange={(event) => setNote(event.currentTarget.value)}
          />
        </div>
      </div>
    </Modal>
  )
}
