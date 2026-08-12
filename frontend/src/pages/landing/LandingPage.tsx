import { VerticalAlignTopOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useState } from 'react'
import HeroSection from '../../components/landing/HeroSection'
import LandingHeader from '../../components/landing/LandingHeader'
import PipelineStatusBar from '../../components/landing/PipelineStatusBar'
import {
  BottomActionSection,
  CapabilitiesSection,
  RoadmapSection,
  ScenariosSection,
} from '../../components/landing/ProductSections'
import './landing-v2.css'

const sectionIds = ['home', 'capabilities', 'workflow', 'scenarios', 'roadmap'] as const

export type LandingSectionId = (typeof sectionIds)[number]

function isLandingSectionId(value: string): value is LandingSectionId {
  return sectionIds.includes(value as LandingSectionId)
}

export default function LandingPage() {
  const [activeSection, setActiveSection] = useState<LandingSectionId>('home')
  const [isHeaderScrolled, setIsHeaderScrolled] = useState(false)
  const [showBackToTop, setShowBackToTop] = useState(false)

  const navigateToSection = useCallback((id: LandingSectionId) => {
    const element = document.getElementById(id)

    if (!element) return

    setActiveSection(id)
    element.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    })

    const nextUrl = `${window.location.pathname}${window.location.search}#${id}`
    if (window.location.hash === `#${id}`) {
      window.history.replaceState(null, '', nextUrl)
    } else {
      window.history.pushState(null, '', nextUrl)
    }
  }, [])

  useEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'

    const scrollFromHash = () => {
      const hashId = window.location.hash.slice(1)
      const targetId = isLandingSectionId(hashId) ? hashId : 'home'

      if (hashId && !isLandingSectionId(hashId)) {
        window.history.replaceState(
          null,
          '',
          `${window.location.pathname}${window.location.search}#home`,
        )
      }

      setActiveSection(targetId)
      const previousScrollBehavior = document.documentElement.style.scrollBehavior
      document.documentElement.style.scrollBehavior = 'auto'
      document.getElementById(targetId)?.scrollIntoView({
        behavior: 'auto',
        block: 'start',
      })
      document.documentElement.style.scrollBehavior = previousScrollBehavior
    }

    const frameId = window.requestAnimationFrame(scrollFromHash)
    window.addEventListener('popstate', scrollFromHash)
    window.addEventListener('hashchange', scrollFromHash)

    return () => {
      window.cancelAnimationFrame(frameId)
      window.removeEventListener('popstate', scrollFromHash)
      window.removeEventListener('hashchange', scrollFromHash)
      window.history.scrollRestoration = previousScrollRestoration
    }
  }, [])

  useEffect(() => {
    let frameId = 0

    const updateScrollState = () => {
      frameId = 0
      const headerHeight = document.querySelector<HTMLElement>('.landing-header')?.offsetHeight ?? 84
      const activationLine = headerHeight + Math.min((window.innerHeight - headerHeight) * 0.35, 260)
      const home = document.getElementById('home')

      let nextActive: LandingSectionId = 'home'
      sectionIds.forEach((id) => {
        const section = document.getElementById(id)
        if (section && section.getBoundingClientRect().top <= activationLine) {
          nextActive = id
        }
      })

      setActiveSection(nextActive)
      setIsHeaderScrolled(window.scrollY > 24)
      setShowBackToTop(window.scrollY > Math.max(280, (home?.offsetHeight ?? 560) * 0.58))
    }

    const handleScroll = () => {
      if (!frameId) frameId = window.requestAnimationFrame(updateScrollState)
    }

    updateScrollState()
    window.addEventListener('scroll', handleScroll, { passive: true })
    window.addEventListener('resize', handleScroll)

    return () => {
      if (frameId) window.cancelAnimationFrame(frameId)
      window.removeEventListener('scroll', handleScroll)
      window.removeEventListener('resize', handleScroll)
    }
  }, [])

  useEffect(() => {
    const sections = sectionIds
      .map((id) => document.getElementById(id))
      .filter((section): section is HTMLElement => section !== null)

    if (!('IntersectionObserver' in window)) {
      sections.forEach((section) => section.classList.add('is-visible'))
      return undefined
    }

    const revealedSections = new Set<LandingSectionId>()

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const id = entry.target.id as LandingSectionId

          if (!sectionIds.includes(id)) return

          if (entry.isIntersecting && !revealedSections.has(id)) {
            revealedSections.add(id)
            entry.target.classList.add('is-visible')
          }
        })
      },
      {
        rootMargin: '-84px 0px -45% 0px',
        threshold: [0, 0.18, 0.35],
      },
    )

    sections.forEach((section) => observer.observe(section))

    return () => observer.disconnect()
  }, [])

  return (
    <div className="landing-page">
      <div className="landing-page__grid" aria-hidden="true" />
      <LandingHeader
        activeSection={activeSection}
        isScrolled={isHeaderScrolled}
        onNavigate={navigateToSection}
      />
      <main>
        <HeroSection />
        <CapabilitiesSection />
        <PipelineStatusBar />
        <ScenariosSection />
        <RoadmapSection />
        <BottomActionSection />
      </main>
      {showBackToTop && (
        <button
          className="landing-back-to-top"
          type="button"
          aria-label="返回顶部"
          title="返回顶部"
          onClick={() => navigateToSection('home')}
        >
          <VerticalAlignTopOutlined aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
