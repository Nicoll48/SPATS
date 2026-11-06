import { useMemo, useState, type ReactNode } from 'react'
import { GuidedProcessContext } from './guided-process-context'

export function GuidedProcessProvider({ children }: { children: ReactNode }) {
  const [uploadedFiles, setUploadedFiles] = useState<Set<string>>(new Set())
  const [dataReady, setDataReady] = useState(false)
  const [planGenerated, setPlanGenerated] = useState(false)
  const [planReviewed, setPlanReviewed] = useState(false)
  const [planPublished, setPlanPublished] = useState(false)

  const value = useMemo(() => ({
    uploadedFiles,
    dataReady,
    planGenerated,
    planReviewed,
    planPublished,
    registerUpload: (id: string) => { setUploadedFiles((prev) => new Set(prev).add(id)); if (id === 'carga-proyeccion') setDataReady(true) },
    generatePlan: () => setPlanGenerated(true),
    reviewPlan: () => setPlanReviewed(true),
    publishPlan: () => setPlanPublished(true),
    resetProcess: () => { setUploadedFiles(new Set()); setDataReady(false); setPlanGenerated(false); setPlanReviewed(false); setPlanPublished(false) },
  }), [uploadedFiles, dataReady, planGenerated, planReviewed, planPublished])

  return <GuidedProcessContext.Provider value={value}>{children}</GuidedProcessContext.Provider>
}
