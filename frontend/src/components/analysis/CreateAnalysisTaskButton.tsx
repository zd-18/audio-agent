import { PlusCircleOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Modal } from 'antd'
import type { ButtonProps } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateAnalysisTask } from '../../hooks/useCreateAnalysisTask'

interface CreateAnalysisTaskButtonProps {
  audioFileId: string
  fileName?: string
  block?: boolean
  buttonType?: ButtonProps['type']
  size?: ButtonProps['size']
  label?: string
}

export default function CreateAnalysisTaskButton({ audioFileId, fileName, block, buttonType = 'primary', size, label = '创建分析任务' }: CreateAnalysisTaskButtonProps) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const { create, loading, error, resetError } = useCreateAnalysisTask()

  const close = () => {
    if (loading) return
    setOpen(false)
    resetError()
  }

  const confirm = async () => {
    const task = await create(audioFileId)
    if (task) navigate(`/analysis/tasks/${task.taskId}`)
  }

  return (
    <>
      <Button type={buttonType} size={size} icon={<PlusCircleOutlined />} block={block} onClick={() => { resetError(); setOpen(true) }}>
        {label}
      </Button>
      <Modal
        title="创建音频分析任务"
        open={open}
        okText="确认创建"
        cancelText="取消"
        confirmLoading={loading}
        closable={!loading}
        maskClosable={!loading}
        keyboard={!loading}
        onOk={confirm}
        onCancel={close}
      >
        <p className="analysis-confirm-description">任务将通过异步队列执行，创建后会自动进入任务详情页。</p>
        <Descriptions column={1} size="small" items={[
          { key: 'file', label: '当前文件', children: fileName || '—' },
          { key: 'id', label: 'audioFileId', children: <span className="workbench-mono">{audioFileId}</span> },
          { key: 'type', label: '分析类型', children: 'FULL' },
          { key: 'queue', label: '执行方式', children: 'RabbitMQ 异步队列' },
        ]} />
        {error && <Alert className="analysis-confirm-error" type="error" showIcon message="创建失败" description={error} />}
      </Modal>
    </>
  )
}
