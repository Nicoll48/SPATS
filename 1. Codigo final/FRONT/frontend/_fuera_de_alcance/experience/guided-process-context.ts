import { createContext, useContext } from 'react'

export interface GuidedProcessContextValue {
  uploadedFiles: Set<string>
  dataReady: boolean
  planGenerated: boolean
  planReviewed: boolean
  planPublished: boolean
  registerUpload: (id: string) => void
  generatePlan: () => void
  reviewPlan: () => void
  publishPlan: () => void
  resetProcess: () => void
}

export const GuidedProcessContext = createContext<GuidedProcessContextValue | null>(null)

export function useGuidedProcess() {
  const context = useContext(GuidedProcessContext)
  if (!context) throw new Error('useGuidedProcess debe usarse dentro de <GuidedProcessProvider>')
  return context
}
