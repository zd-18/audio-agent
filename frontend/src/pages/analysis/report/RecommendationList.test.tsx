import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import RecommendationList from './RecommendationList'

describe('RecommendationList', () => {
  it('shows diagnosis methods and recommended parameters without executing them', () => {
    render(
      <RecommendationList
        recommendations={[{
          priority: 'MEDIUM',
          message: '整体响度偏低。当前：-24 LUFS。',
          recommendedMethod: '整段响度标准化',
          recommendedParameters: '目标 -16 LUFS，真峰值上限 -1 dBFS',
        }]}
        onLocate={vi.fn()}
        onPreview={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: '推荐处理方式' })).toBeInTheDocument()
    expect(screen.getByText('整体响度偏低。当前：-24 LUFS。')).toBeInTheDocument()
    expect(screen.queryByText(/RecommendationList.tsx/)).not.toBeInTheDocument()
    expect(screen.getByText(/整段响度标准化/)).toBeInTheDocument()
    expect(screen.getByText(/目标 -16 LUFS，真峰值上限 -1 dBFS/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '开始处理' })).not.toBeInTheDocument()
  })
})
