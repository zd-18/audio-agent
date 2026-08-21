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

export default function CreateAnalysisTaskButton({ audioFileId, fileName, block, buttonType = 'primary', size, label = '开始智能诊断' }: CreateAnalysisTaskButtonProps) {
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
    if (task) navigate('/tasks')
  }

  return (
    <>
      <Button type={buttonType} size={size} icon={<PlusCircleOutlined />} block={block} onClick={() => { resetError(); setOpen(true) }}>
        {label}
      </Button>
      <Modal
        title="开始智能诊断"
        open={open}
        okText="开始智能诊断"
        cancelText="取消"
        confirmLoading={loading}
        closable={!loading}
        maskClosable={!loading}
        keyboard={!loading}
        onOk={confirm}
        onCancel={close}
      >
        <p className="analysis-confirm-description">系统将在后台主动检测音频质量问题，完成后给出处理建议，不会直接修改原始音频。</p>
        <Descriptions column={1} size="small" items={[
          { key: 'file', label: '当前文件', children: fileName || '—' },
          { key: 'content', label: '分析内容', children: '音频质量、问题片段与处理建议' },
        ]} />
        {error && <Alert className="analysis-confirm-error" type="error" showIcon message="创建失败" description={error} />}
      </Modal>
    </>
  )
}
