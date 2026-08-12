import { FileTextOutlined } from '@ant-design/icons'
import { App, Button, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateTranscription } from '../../hooks/useCreateTranscription'

interface Props {
  audioFileId: string
  taskId?: string | null
  status?: string | null
  buttonType?: ButtonProps['type']
  size?: ButtonProps['size']
  block?: boolean
}

export default function CreateTranscriptionButton({
  audioFileId, taskId, status, buttonType = 'default', size, block,
}: Props) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { create, loading, error, clearError } = useCreateTranscription()
  const hasTask = Boolean(taskId)
  const label = status === 'SUCCESS' ? '查看文字稿'
    : status === 'FAILED' ? '查看并重试'
      : hasTask ? '查看转写进度' : '开始转写'
  const tooltip = hasTask
    ? label
    : '默认使用中文识别，说话人区分已关闭'

  useEffect(() => {
    if (error) {
      void message.error(error)
      clearError()
    }
  }, [clearError, error, message])

  const act = async () => {
    clearError()
    if (taskId) {
      navigate(`/transcriptions/${encodeURIComponent(taskId)}`)
      return
    }
    const task = await create(audioFileId)
    if (task) navigate(`/transcriptions/${encodeURIComponent(task.taskId)}`)
  }

  return (
    <Tooltip title={tooltip}>
      <Button
        type={buttonType}
        size={size}
        block={block}
        icon={<FileTextOutlined />}
        loading={loading}
        disabled={loading}
        onClick={() => { void act() }}
      >
        {label}
      </Button>
    </Tooltip>
  )
}
