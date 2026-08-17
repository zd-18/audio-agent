import { Input, Select } from 'antd'
import type { ProcessingStepConfirmation } from '../../../types/processingConfirmation'
import { getEditableParameterDefinitions } from '../../../utils/processingParameterValidation'

interface StepParameterEditorProps {
  step: ProcessingStepConfirmation
  values: Record<string, string>
  errors: Record<string, string>
  readOnly: boolean
  onChange: (key: string, value: string) => void
}

export default function StepParameterEditor({
  step,
  values,
  errors,
  readOnly,
  onChange,
}: StepParameterEditorProps) {
  const definitions = getEditableParameterDefinitions(step.operationType, values)

  if (definitions.length === 0) {
    return (
      <div className="processing-parameter-empty">
        当前建议没有可调整参数，将按系统建议内容保存决定。
      </div>
    )
  }

  if (readOnly) {
    return (
      <dl className="processing-effective-parameters">
        {definitions.map((definition) => (
          <div key={definition.key}>
            <dt>{definition.label}</dt>
            <dd>
              {definition.options?.find((option) => option.value === values[definition.key])?.label
                || values[definition.key]
                || '未设置'}
              {definition.unit && values[definition.key] ? ` ${definition.unit}` : ''}
            </dd>
          </div>
        ))}
      </dl>
    )
  }

  return (
    <div className="processing-parameter-editor__grid">
      {definitions.map((definition) => {
        const inputId = `confirmation-${step.stepConfirmationId}-${definition.key}`
        const error = errors[definition.key]
        return (
          <div className={`processing-parameter-field${error ? ' has-error' : ''}`} key={definition.key}>
            <label htmlFor={inputId}>{definition.label}</label>
            {definition.kind === 'select' ? (
              <Select
                id={inputId}
                value={values[definition.key] || undefined}
                options={definition.options}
                status={error ? 'error' : undefined}
                aria-invalid={Boolean(error)}
                onChange={(value) => onChange(definition.key, value)}
              />
            ) : (
              <div className="processing-parameter-input">
                <Input
                  id={inputId}
                  type="number"
                  inputMode="decimal"
                  value={values[definition.key] ?? ''}
                  min={definition.min}
                  max={definition.max}
                  step={definition.step}
                  status={error ? 'error' : undefined}
                  aria-invalid={Boolean(error)}
                  onChange={(event) => onChange(definition.key, event.currentTarget.value)}
                />
                {definition.unit && <span aria-hidden="true">{definition.unit}</span>}
              </div>
            )}
            <small>{definition.helper}</small>
            {error && <span className="processing-parameter-field__error" role="alert">{error}</span>}
          </div>
        )
      })}
    </div>
  )
}
