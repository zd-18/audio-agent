import {
  CheckCircleOutlined,
  LoadingOutlined,
  ReloadOutlined,
  SaveOutlined,
  SoundOutlined,
} from '@ant-design/icons'
import { Alert, Button, Checkbox, Input, Radio } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import type {
  ProcessingStepConfirmation,
  UpdateProcessingStepConfirmationPayload,
} from '../../../types/processingConfirmation'
import {
  getProcessingConfirmationErrorMessage,
  STEP_DECISION_META,
} from '../../../utils/processingConfirmationDisplay'
import {
  buildStepUpdatePayload,
  createOriginalParameterValues,
  createStepEditorDraft,
  stepEditorDraftsEqual,
} from '../../../utils/processingParameterValidation'
import type { StepEditorDraft } from '../../../utils/processingParameterValidation'
import StepParameterEditor from './StepParameterEditor'

interface StepDecisionEditorProps {
  step: ProcessingStepConfirmation
  readOnly: boolean
  saving: boolean
  listened: boolean
  onDirtyChange: (dirty: boolean) => void
  onSave: (payload: UpdateProcessingStepConfirmationPayload) => Promise<ProcessingStepConfirmation | null>
}

export default function StepDecisionEditor({
  step,
  readOnly,
  saving,
  listened,
  onDirtyChange,
  onSave,
}: StepDecisionEditorProps) {
  const serverDraft = useMemo(() => createStepEditorDraft(step), [step])
  const [draft, setDraft] = useState<StepEditorDraft>(serverDraft)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const savedTimerRef = useRef<number>()
  const dirty = !stepEditorDraftsEqual(draft, serverDraft)

  useEffect(() => {
    setDraft(serverDraft)
    setFieldErrors({})
    setSaveError(null)
  }, [serverDraft])

  useEffect(() => {
    onDirtyChange(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => () => {
    onDirtyChange(false)
    if (savedTimerRef.current !== undefined) window.clearTimeout(savedTimerRef.current)
  }, [onDirtyChange])

  const updateDraft = (next: StepEditorDraft) => {
    setDraft(next)
    setSaved(false)
    setSaveError(null)
  }

  const markSaved = () => {
    setSaved(true)
    if (savedTimerRef.current !== undefined) window.clearTimeout(savedTimerRef.current)
    savedTimerRef.current = window.setTimeout(() => setSaved(false), 2600)
  }

  const submitDraft = async (nextDraft: StepEditorDraft) => {
    const result = buildStepUpdatePayload(step, nextDraft)
    setFieldErrors(result.fieldErrors)
    if (!result.payload) return

    setSaveError(null)
    try {
      const response = await onSave(result.payload)
      if (response) markSaved()
    } catch (error) {
      setSaveError(getProcessingConfirmationErrorMessage(error, '当前步骤保存失败，请检查后重试。'))
    }
  }

  const restoreSystemSuggestion = () => {
    const nextDraft = {
      ...draft,
      parameterValues: createOriginalParameterValues(step),
    }
    updateDraft(nextDraft)
    void submitDraft(nextDraft)
  }

  return (
    <section className={`processing-step-decision-editor${readOnly ? ' is-readonly' : ''}`}>
      <div className="processing-step-decision-editor__heading">
        <div>
          <span>YOUR DECISION</span>
          <h3>{readOnly ? '已保存的用户决定' : '你的决定'}</h3>
          <p>{readOnly ? '当前确认单为只读状态。' : '选择后请保存本步骤；保存失败不会影响其他步骤。'}</p>
        </div>
        <span className={`processing-step-save-state${saved ? ' is-saved' : ''}`} aria-live="polite">
          {saving ? <><LoadingOutlined spin /> 保存中</> : saved ? <><CheckCircleOutlined /> 已保存</> : dirty ? '尚未保存' : '已同步'}
        </span>
      </div>

      {readOnly ? (
        <div className="processing-decision-readonly">
          <strong>{STEP_DECISION_META[step.decision].label}</strong>
          {step.requiresConfirmation && step.decision === 'ACCEPTED' && (
            <span><CheckCircleOutlined /> {step.userConfirmed ? '已完成试听确认' : '未完成试听确认'}</span>
          )}
        </div>
      ) : (
        <Radio.Group
          className="processing-decision-options"
          value={draft.decision}
          optionType="button"
          buttonStyle="solid"
          aria-label="选择当前步骤决定"
          disabled={saving}
          onChange={(event) => {
            const decision = event.target.value as StepEditorDraft['decision']
            updateDraft({
              ...draft,
              decision,
              userConfirmed: decision === 'ACCEPTED' ? draft.userConfirmed : false,
            })
          }}
          options={([
            { value: 'ACCEPTED', label: '接受建议' },
            { value: 'REJECTED', label: '暂不处理' },
            { value: 'PENDING', label: '待决定' },
          ] satisfies Array<{ value: StepEditorDraft['decision']; label: string }>)}
        />
      )}

      <fieldset className="processing-parameter-editor">
        <legend>最终生效参数</legend>
        <p>输入框默认显示后端返回的有效参数；保存时仅提交相对系统建议发生变化的字段。</p>
        <StepParameterEditor
          step={step}
          values={draft.parameterValues}
          errors={fieldErrors}
          readOnly={readOnly}
          onChange={(key, value) => updateDraft({
            ...draft,
            parameterValues: { ...draft.parameterValues, [key]: value },
          })}
        />
        {!readOnly && Object.keys(draft.parameterValues).length > 0 && (
          <Button
            className="processing-parameter-editor__restore"
            type="link"
            icon={<ReloadOutlined />}
            loading={saving}
            disabled={saving}
            onClick={restoreSystemSuggestion}
          >
            恢复系统建议并保存
          </Button>
        )}
      </fieldset>

      {step.requiresConfirmation && draft.decision === 'ACCEPTED' && (
        <div className="processing-listen-confirmation">
          <div>
            <SoundOutlined aria-hidden="true" />
            <span>{listened ? '已试听（仅本地提示）' : '请先使用上方播放器试听对应内容'}</span>
          </div>
          {readOnly ? (
            <strong>{step.userConfirmed ? '已确认需要执行此建议' : '尚未明确确认'}</strong>
          ) : (
            <Checkbox
              checked={draft.userConfirmed}
              disabled={saving}
              onChange={(event) => updateDraft({ ...draft, userConfirmed: event.target.checked })}
            >
              我已试听并确认需要执行此建议
            </Checkbox>
          )}
          <small>试听不会自动完成确认，必须由你手动勾选并保存。</small>
        </div>
      )}

      <div className="processing-step-note">
        <label htmlFor={`processing-step-note-${step.stepConfirmationId}`}>简短备注</label>
        {readOnly ? (
          <p>{step.userNote || '未填写备注'}</p>
        ) : (
          <>
            <Input.TextArea
              id={`processing-step-note-${step.stepConfirmationId}`}
              value={draft.userNote}
              maxLength={500}
              rows={3}
              disabled={saving}
              status={fieldErrors.userNote ? 'error' : undefined}
              placeholder="可记录试听感受或决定原因"
              onChange={(event) => updateDraft({ ...draft, userNote: event.currentTarget.value })}
            />
            <small>{500 - draft.userNote.length} 个字符可用</small>
            {fieldErrors.userNote && <span role="alert">{fieldErrors.userNote}</span>}
          </>
        )}
      </div>

      {saveError && <Alert type="error" showIcon message="保存失败" description={saveError} />}

      {!readOnly && (
        <div className="processing-step-decision-editor__actions">
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={!dirty || saving}
            onClick={() => { void submitDraft(draft) }}
          >
            保存决定
          </Button>
          <span>{dirty ? '本步骤有尚未保存的修改' : '当前内容已与服务器同步'}</span>
        </div>
      )}
    </section>
  )
}
