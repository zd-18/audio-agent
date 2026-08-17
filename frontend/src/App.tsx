import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './auth/RouteGuards'
import WorkbenchLayout from './layouts/WorkbenchLayout'
import AudioUploadPage from './pages/audio/AudioUploadPage'
import AudioFileDetailPage from './pages/audio/AudioFileDetailPage'
import AudioFileLookupPage from './pages/audio/AudioFileLookupPage'
import AnalysisTaskDetailPage from './pages/analysis/AnalysisTaskDetailPage'
import AnalysisTaskLookupPage from './pages/analysis/AnalysisTaskLookupPage'
import AnalysisReportPage from './pages/analysis/AnalysisReportPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import LandingPage from './pages/landing/LandingPage'
import ProcessingPlanPage from './pages/processing-plan/ProcessingPlanPage'
import ProcessingExecutionPage from './pages/processing-execution/ProcessingExecutionPage'
import AgentConversationPage from './pages/agent/AgentConversationPage'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import SettingsPage from './pages/settings/SettingsPage'
import TranscriptionListPage from './pages/transcription/TranscriptionListPage'
import TranscriptionDetailPage from './pages/transcription/TranscriptionDetailPage'
import UserTasksPage from './pages/tasks/UserTasksPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/workbench" element={<Navigate to="/dashboard" replace />} />
        <Route path="/files" element={<Navigate to="/audio/files" replace />} />
        <Route path="/analysis-tasks" element={<Navigate to="/tasks" replace />} />
        <Route element={<WorkbenchLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/tasks" element={<UserTasksPage />} />
          <Route path="/audio/upload" element={<AudioUploadPage />} />
          <Route path="/audio/files" element={<AudioFileLookupPage />} />
          <Route path="/audio/files/:audioFileId" element={<AudioFileDetailPage />} />
          <Route path="/audio/files/:audioFileId/agent" element={<AgentConversationPage />} />
          <Route path="/analysis/tasks" element={<AnalysisTaskLookupPage />} />
          <Route path="/processing/executions" element={<Navigate to="/tasks" replace />} />
          <Route path="/transcriptions" element={<TranscriptionListPage />} />
          <Route path="/transcriptions/:taskId/agent" element={<AgentConversationPage />} />
          <Route path="/transcriptions/:taskId" element={<TranscriptionDetailPage />} />
          <Route path="/analysis/tasks/:taskId/report" element={<AnalysisReportPage />} />
          <Route path="/analysis/tasks/:taskId/processing-plan" element={<ProcessingPlanPage />} />
          <Route path="/analysis/tasks/:taskId/processing-execution" element={<ProcessingExecutionPage />} />
          <Route path="/analysis/tasks/:taskId" element={<AnalysisTaskDetailPage />} />
          <Route path="/agent" element={<Navigate to="/transcriptions" replace />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
