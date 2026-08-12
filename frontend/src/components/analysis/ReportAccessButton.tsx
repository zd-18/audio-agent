import { FileTextOutlined } from '@ant-design/icons'
import { Button, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import { Link } from 'react-router-dom'
import type { AnalysisTaskStatus } from '../../types/api'

interface ReportAccessButtonProps {
  taskId: string
  status: AnalysisTaskStatus
  type?: ButtonProps['type']
  size?: ButtonProps['size']
  block?: boolean
  compactLabel?: boolean
}

export default function ReportAccessButton({
  taskId,
  status,
  type = 'default',
  size,
  block,
  compactLabel,
}: ReportAccessButtonProps) {
  if (status === 'SUCCESS') {
    return (
      <Link to={`/analysis/tasks/${taskId}/report`}>
        <Button type={type} size={size} block={block} icon={<FileTextOutlined />}>
          {compactLabel ? '报告' : '查看报告'}
        </Button>
      </Link>
    )
  }

  const isFailed = status === 'FAILED'
  const label = isFailed ? '分析失败' : '分析中'
  const tip = isFailed
    ? '本次分析未完成，暂时无法生成报告。'
    : '分析完成后即可查看报告。'

  return (
    <Tooltip title={tip}>
      <span className="analysis-report-access-disabled">
        <Button
          type={type}
          size={size}
          block={block}
          disabled
          icon={<FileTextOutlined />}
        >
          {label}
        </Button>
      </span>
    </Tooltip>
  )
}
