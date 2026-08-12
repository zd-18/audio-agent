import { Typography } from 'antd'

export default function CopyableValue({ value, mono = false }: { value?: string; mono?: boolean }) {
  if (!value) return <span>—</span>
  return (
    <Typography.Text className={mono ? 'workbench-copyable workbench-copyable--mono' : 'workbench-copyable'} copyable={{ text: value, tooltips: ['复制', '已复制'] }}>
      {value}
    </Typography.Text>
  )
}
