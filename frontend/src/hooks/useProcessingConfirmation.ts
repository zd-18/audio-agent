import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cancelProcessingConfirmation,
  confirmProcessingConfirmation,
  createProcessingConfirmation,
  getProcessingConfirmation,
  updateProcessingStepConfirmation,
} from '../api/processingConfirmation'
import { ApiError } from '../api/http'
import type {
  ProcessingConfirmation,
  ProcessingStepConfirmation,
  UpdateProcessingStepConfirmationPayload,
} from '../types/processingConfirmation'
import {
  getProcessingConfirmationErrorMessage,
  PROCESSING_CONFIRMATION_NOT_FOUND_CODE,
} from '../utils/processingConfirmationDisplay'

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

function countDecisions(steps: ProcessingStepConfirmation[]) {
  return steps.reduce(
    (counts, step) => {
      if (step.decision === 'ACCEPTED') counts.accepted += 1
      if (step.decision === 'REJECTED') counts.rejected += 1
      if (step.decision === 'PENDING') counts.pending += 1
      return counts
    },
    { accepted: 0, rejected: 0, pending: 0 },
  )
}

export function useProcessingConfirmation(taskId?: string, planKey?: string) {
  const [confirmation, setConfirmation] = useState<ProcessingConfirmation | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [loading, setLoading] = useState(Boolean(taskId && planKey))
  const [refreshing, setRefreshing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [savingStepIds, setSavingStepIds] = useState<Set<string>>(() => new Set())
  const [version, setVersion] = useState(0)
  const mountedRef = useRef(true)
  const controllersRef = useRef<Set<AbortController>>(new Set())
  const createLockedRef = useRef(false)
  const confirmLockedRef = useRef(false)
  const cancelLockedRef = useRef(false)
  const savingStepIdsRef = useRef<Set<string>>(new Set())

  const registerController = useCallback(() => {
    const controller = new AbortController()
    controllersRef.current.add(controller)
    return controller
  }, [])

  const releaseController = useCallback((controller: AbortController) => {
    controllersRef.current.delete(controller)
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllersRef.current.forEach((controller) => controller.abort())
      controllersRef.current.clear()
    }
  }, [])

  const reload = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    if (!taskId || !planKey) {
      setConfirmation(null)
      setError(null)
      setNotFound(false)
      setLoading(false)
      setRefreshing(false)
      return undefined
    }

    const controller = registerController()
    setConfirmation(null)
    setError(null)
    setNotFound(false)
    setLoading(true)
    setRefreshing(true)

    getProcessingConfirmation(taskId, controller.signal)
      .then((nextConfirmation) => {
        if (!controller.signal.aborted && mountedRef.current) {
          setConfirmation(nextConfirmation)
        }
      })
      .catch((requestError: unknown) => {
        if (isAbortError(requestError) || controller.signal.aborted || !mountedRef.current) return
        if (requestError instanceof ApiError
          && requestError.code === PROCESSING_CONFIRMATION_NOT_FOUND_CODE) {
          setNotFound(true)
          return
        }
        setError(requestError instanceof ApiError
          ? requestError
          : new ApiError(getProcessingConfirmationErrorMessage(requestError)))
      })
      .finally(() => {
        releaseController(controller)
        if (!controller.signal.aborted && mountedRef.current) {
          setLoading(false)
          setRefreshing(false)
        }
      })

    return () => {
      controller.abort()
      releaseController(controller)
    }
  }, [planKey, registerController, releaseController, taskId, version])

  const create = useCallback(async () => {
    if (!taskId || createLockedRef.current) return null
    createLockedRef.current = true
    const controller = registerController()
    setCreating(true)
    setError(null)
    try {
      const nextConfirmation = await createProcessingConfirmation(taskId, controller.signal)
      if (!controller.signal.aborted && mountedRef.current) {
        setConfirmation(nextConfirmation)
        setNotFound(false)
      }
      return nextConfirmation
    } catch (requestError) {
      if (!isAbortError(requestError) && mountedRef.current) {
        setError(requestError instanceof ApiError
          ? requestError
          : new ApiError(getProcessingConfirmationErrorMessage(requestError)))
      }
      throw requestError
    } finally {
      releaseController(controller)
      createLockedRef.current = false
      if (!controller.signal.aborted && mountedRef.current) setCreating(false)
    }
  }, [registerController, releaseController, taskId])

  const updateStep = useCallback(async (
    stepConfirmationId: string,
    payload: UpdateProcessingStepConfirmationPayload,
  ) => {
    const current = confirmation
    if (!current || savingStepIdsRef.current.has(stepConfirmationId)) return null

    savingStepIdsRef.current.add(stepConfirmationId)
    setSavingStepIds(new Set(savingStepIdsRef.current))
    const controller = registerController()
    try {
      const savedStep = await updateProcessingStepConfirmation(
        current.confirmationId,
        stepConfirmationId,
        payload,
        controller.signal,
      )
      if (!controller.signal.aborted && mountedRef.current) {
        setConfirmation((previous) => {
          if (!previous || previous.confirmationId !== current.confirmationId) return previous
          const steps = previous.steps.map((step) => (
            step.stepConfirmationId === savedStep.stepConfirmationId ? savedStep : step
          ))
          const counts = countDecisions(steps)
          return {
            ...previous,
            steps,
            acceptedStepCount: counts.accepted,
            rejectedStepCount: counts.rejected,
            pendingStepCount: counts.pending,
          }
        })
      }
      if (taskId && !controller.signal.aborted) {
        try {
          const refreshedConfirmation = await getProcessingConfirmation(taskId, controller.signal)
          if (!controller.signal.aborted && mountedRef.current) {
            setConfirmation(refreshedConfirmation)
          }
        } catch (refreshError) {
          if (isAbortError(refreshError)) return savedStep
          // The step PUT has already succeeded. Keep its authoritative response
          // instead of turning a secondary summary refresh into a save failure.
        }
      }
      return savedStep
    } finally {
      releaseController(controller)
      savingStepIdsRef.current.delete(stepConfirmationId)
      if (mountedRef.current) setSavingStepIds(new Set(savingStepIdsRef.current))
    }
  }, [confirmation, registerController, releaseController, taskId])

  const confirm = useCallback(async () => {
    if (!confirmation || confirmLockedRef.current) return null
    confirmLockedRef.current = true
    const controller = registerController()
    setConfirming(true)
    try {
      const nextConfirmation = await confirmProcessingConfirmation(
        confirmation.confirmationId,
        controller.signal,
      )
      if (!controller.signal.aborted && mountedRef.current) setConfirmation(nextConfirmation)
      return nextConfirmation
    } finally {
      releaseController(controller)
      confirmLockedRef.current = false
      if (!controller.signal.aborted && mountedRef.current) setConfirming(false)
    }
  }, [confirmation, registerController, releaseController])

  const cancel = useCallback(async () => {
    if (!confirmation || cancelLockedRef.current) return null
    cancelLockedRef.current = true
    const controller = registerController()
    setCancelling(true)
    try {
      const nextConfirmation = await cancelProcessingConfirmation(
        confirmation.confirmationId,
        controller.signal,
      )
      if (!controller.signal.aborted && mountedRef.current) setConfirmation(nextConfirmation)
      return nextConfirmation
    } finally {
      releaseController(controller)
      cancelLockedRef.current = false
      if (!controller.signal.aborted && mountedRef.current) setCancelling(false)
    }
  }, [confirmation, registerController, releaseController])

  return {
    confirmation,
    error,
    notFound,
    loading,
    refreshing,
    creating,
    confirming,
    cancelling,
    savingStepIds,
    reload,
    create,
    updateStep,
    confirm,
    cancel,
  }
}
