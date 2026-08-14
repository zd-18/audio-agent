import {
  AudioOutlined,
  CloudUploadOutlined,
  DashboardOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HomeOutlined,
  MessageOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'

export interface WorkbenchNavItem {
  key: string
  label: string
  icon: ReactNode
}

export const workbenchNavItems: WorkbenchNavItem[] = [
  { key: '/dashboard', label: '工作台', icon: <DashboardOutlined /> },
  { key: '/tasks', label: '任务进度', icon: <UnorderedListOutlined /> },
  { key: '/audio/upload', label: '上传音频', icon: <CloudUploadOutlined /> },
  { key: '/audio/files', label: '音频文件', icon: <AudioOutlined /> },
  { key: '/transcriptions', label: '音频转写', icon: <FileTextOutlined /> },
  { key: '/settings', label: '系统设置', icon: <SettingOutlined /> },
  { key: '/', label: '返回产品首页', icon: <HomeOutlined /> },
]

export const routeMeta: Record<string, { title: string; breadcrumb: string[]; icon?: ReactNode }> = {
  '/dashboard': { title: '工作台', breadcrumb: ['AudioAgent', '工作台'], icon: <DashboardOutlined /> },
  '/tasks': { title: '任务进度', breadcrumb: ['AudioAgent', '任务进度'], icon: <UnorderedListOutlined /> },
  '/audio/upload': { title: '上传音频', breadcrumb: ['AudioAgent', '音频管理', '上传音频'], icon: <CloudUploadOutlined /> },
  '/audio/files': { title: '音频文件', breadcrumb: ['AudioAgent', '音频管理', '音频文件'], icon: <FileSearchOutlined /> },
  '/analysis/tasks': { title: '分析任务', breadcrumb: ['AudioAgent', '任务管理', '分析任务'], icon: <UnorderedListOutlined /> },
  '/processing/executions': { title: '处理任务', breadcrumb: ['AudioAgent', '任务管理', '处理任务'], icon: <ThunderboltOutlined /> },
  '/transcriptions': { title: '音频转写', breadcrumb: ['AudioAgent', '内容工具', '音频转写'], icon: <FileTextOutlined /> },
  '/settings': { title: '系统设置', breadcrumb: ['AudioAgent', '系统设置'], icon: <SettingOutlined /> },
}

export function getRouteMeta(pathname: string) {
  if (/^\/transcriptions\/[^/]+\/agent$/.test(pathname)) {
    return { title: 'Agent 智能问答', breadcrumb: ['AudioAgent', '内容工具', '音频转写', '智能问答'], icon: <MessageOutlined /> }
  }
  if (/^\/transcriptions\/[^/]+$/.test(pathname)) {
    return { title: '文字稿详情', breadcrumb: ['AudioAgent', '内容工具', '音频转写', '文字稿详情'], icon: <FileTextOutlined /> }
  }
  if (/^\/audio\/files\/[^/]+$/.test(pathname)) {
    return { title: '音频文件详情', breadcrumb: ['AudioAgent', '音频管理', '音频文件详情'], icon: <FileSearchOutlined /> }
  }
  if (/^\/analysis\/tasks\/[^/]+\/report$/.test(pathname)) {
    return { title: '音频分析报告', breadcrumb: ['AudioAgent', '任务进度', '分析结果'], icon: <UnorderedListOutlined /> }
  }
  if (/^\/analysis\/tasks\/[^/]+\/processing-plan$/.test(pathname)) {
    return { title: '处理方案', breadcrumb: ['AudioAgent', '任务进度', '处理方案'], icon: <UnorderedListOutlined /> }
  }
  if (/^\/analysis\/tasks\/[^/]+\/processing-execution$/.test(pathname)) {
    return { title: '音频处理', breadcrumb: ['AudioAgent', '任务进度', '音频处理'], icon: <UnorderedListOutlined /> }
  }
  if (/^\/analysis\/tasks\/[^/]+$/.test(pathname)) {
    return { title: '任务详情', breadcrumb: ['AudioAgent', '任务进度', '任务详情'], icon: <UnorderedListOutlined /> }
  }
  return routeMeta[pathname] || routeMeta['/dashboard']
}
