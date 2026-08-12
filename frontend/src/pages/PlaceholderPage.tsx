import { ArrowLeftOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import './placeholder.css'

interface PlaceholderPageProps {
  eyebrow: string
  title: string
}

export default function PlaceholderPage({ eyebrow, title }: PlaceholderPageProps) {
  return (
    <main className="audio-placeholder">
      <div className="audio-placeholder__panel">
        <Link className="audio-placeholder__back" to="/">
          <ArrowLeftOutlined aria-hidden="true" /> 返回产品首页
        </Link>
        <p>{eyebrow}</p>
        <h1>{title}</h1>
        <span>当前迭代仅交付首页 Hero；这里预留真实业务路由，后续页面可直接替换。</span>
      </div>
    </main>
  )
}
