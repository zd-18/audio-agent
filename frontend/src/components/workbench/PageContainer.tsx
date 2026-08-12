import type { PropsWithChildren } from 'react'

export default function PageContainer({ children }: PropsWithChildren) {
  return <main id="workbench-main" className="workbench-page">{children}</main>
}

