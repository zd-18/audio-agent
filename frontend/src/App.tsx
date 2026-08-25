import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Skeleton } from 'antd'
import { ProtectedRoute, PublicOnlyRoute } from './auth/RouteGuards'
import WorkbenchLayout from './layouts/WorkbenchLayout'

const AudioUploadPage = lazy(() => import('./pages/audio/AudioUploadPage'))
const AudioFileDetailPage = lazy(() => import('./pages/audio/AudioFileDetailPage'))
const AudioFileLookupPage = lazy(() => import('./pages/audio/AudioFileLookupPage'))
const AnalysisTaskDetailPage = lazy(() => import('./pages/analysis/AnalysisTaskDetailPage'))
const AnalysisTaskLookupPage = lazy(() => import('./pages/analysis/AnalysisTaskLookupPage'))
const AnalysisReportPage = lazy(() => import('./pages/analysis/AnalysisReportPage'))
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'))
const LandingPage = lazy(() => import('./pages/landing/LandingPage'))
const ProcessingPlanPage = lazy(() => import('./pages/processing-plan/ProcessingPlanPage'))
const ProcessingExecutionPage = lazy(() => import('./pages/processing-execution/ProcessingExecutionPage'))
const AgentConversationPage = lazy(() => import('./pages/agent/AgentConversationPage'))
const LoginPage = lazy(() => import('./pages/auth/LoginPage'))
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'))
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage'))
const TranscriptionListPage = lazy(() => import('./pages/transcription/TranscriptionListPage'))
const TranscriptionDetailPage = lazy(() => import('./pages/transcription/TranscriptionDetailPage'))
const UserTasksPage = lazy(() => import('./pages/tasks/UserTasksPage'))

function PublicRouteFallback() {
  return (
    <main className="auth-startup" aria-busy="true" aria-label="正在加载页面">
      <span className="auth-startup__mark" aria-hidden="true"><i /><i /><i /><i /><i /></span>
      <strong>正在加载 AudioAgent</strong>
      <small>马上就好</small>
    </main>
  )
}

function WorkbenchRouteFallback() {
  return (
    <section className="workbench-panel" aria-busy="true" aria-label="正在加载页面">
      <Skeleton active title={{ width: '32%' }} paragraph={{ rows: 8 }} />
    </section>
  )
}

function publicPage(page: ReactNode) {
  return <Suspense fallback={<PublicRouteFallback />}>{page}</Suspense>
}

function workbenchPage(page: ReactNode) {
  return <Suspense fallback={<WorkbenchRouteFallback />}>{page}</Suspense>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={publicPage(<LandingPage />)} />
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={publicPage(<LoginPage />)} />
        <Route path="/register" element={publicPage(<RegisterPage />)} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/workbench" element={<Navigate to="/dashboard" replace />} />
        <Route path="/files" element={<Navigate to="/audio/files" replace />} />
        <Route path="/analysis-tasks" element={<Navigate to="/tasks" replace />} />
        <Route element={<WorkbenchLayout />}>
          <Route path="/dashboard" element={workbenchPage(<DashboardPage />)} />
          <Route path="/tasks" element={workbenchPage(<UserTasksPage />)} />
          <Route path="/audio/upload" element={workbenchPage(<AudioUploadPage />)} />
          <Route path="/audio/files" element={workbenchPage(<AudioFileLookupPage />)} />
          <Route path="/audio/files/:audioFileId" element={workbenchPage(<AudioFileDetailPage />)} />
          <Route path="/audio/files/:audioFileId/agent" element={workbenchPage(<AgentConversationPage />)} />
          <Route path="/analysis/tasks" element={workbenchPage(<AnalysisTaskLookupPage />)} />
          <Route path="/processing/executions" element={<Navigate to="/tasks" replace />} />
          <Route path="/transcriptions" element={workbenchPage(<TranscriptionListPage />)} />
          <Route path="/transcriptions/:taskId/agent" element={workbenchPage(<AgentConversationPage />)} />
          <Route path="/transcriptions/:taskId" element={workbenchPage(<TranscriptionDetailPage />)} />
          <Route path="/analysis/tasks/:taskId/report" element={workbenchPage(<AnalysisReportPage />)} />
          <Route path="/analysis/tasks/:taskId/processing-plan" element={workbenchPage(<ProcessingPlanPage />)} />
          <Route path="/analysis/tasks/:taskId/processing-execution" element={workbenchPage(<ProcessingExecutionPage />)} />
          <Route path="/analysis/tasks/:taskId" element={workbenchPage(<AnalysisTaskDetailPage />)} />
          <Route path="/agent" element={<Navigate to="/transcriptions" replace />} />
          <Route path="/settings" element={workbenchPage(<SettingsPage />)} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
