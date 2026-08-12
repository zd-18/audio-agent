import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowDown, ArrowUpRight, X } from 'lucide-react'
import Hls from 'hls.js'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const videoSource = 'https://stream.mux.com/Aa02T7oM1wH5Mk5EEVDYhbZ1ChcdhRsS2m1NYyx4Ua1g.m3u8'

const projects = [
  { title: 'Automotive Motion', category: 'Film direction', image: 'https://images.unsplash.com/photo-1503736334956-4c8f8e92946d?auto=format&fit=crop&w=1600&q=85', span: 'md:col-span-7', aspect: 'aspect-[1.28/1]' },
  { title: 'Urban Architecture', category: 'Photography', image: 'https://images.unsplash.com/photo-1487958449943-2429e8be8625?auto=format&fit=crop&w=1200&q=85', span: 'md:col-span-5', aspect: 'aspect-[.91/1]' },
  { title: 'Human Perspective', category: 'Editorial', image: 'https://images.unsplash.com/photo-1531123897727-8f129e1688ce?auto=format&fit=crop&w=1200&q=85', span: 'md:col-span-5', aspect: 'aspect-[.91/1]' },
  { title: 'Brand Identity', category: 'Art direction', image: 'https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format&fit=crop&w=1600&q=85', span: 'md:col-span-7', aspect: 'aspect-[1.28/1]' },
]

const journal = [
  { title: 'The quiet power of a considered interface', date: 'May 24, 2026', time: '5 min', image: 'https://images.unsplash.com/photo-1558655146-9f40138edfeb?auto=format&fit=crop&w=500&q=80' },
  { title: 'Building brands that feel alive in motion', date: 'Apr 18, 2026', time: '7 min', image: 'https://images.unsplash.com/photo-1561070791-2526d30994b5?auto=format&fit=crop&w=500&q=80' },
  { title: 'What the city taught me about rhythm', date: 'Mar 02, 2026', time: '4 min', image: 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=500&q=80' },
  { title: 'Notes from a year of creative experiments', date: 'Jan 12, 2026', time: '6 min', image: 'https://images.unsplash.com/photo-1513364776144-60967b0f800f?auto=format&fit=crop&w=500&q=80' },
]

const explorations = [
  'https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=800&q=85',
  'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=800&q=85',
  'https://images.unsplash.com/photo-1618005198919-d3d4b5a92ead?auto=format&fit=crop&w=800&q=85',
  'https://images.unsplash.com/photo-1549490349-8643362247b5?auto=format&fit=crop&w=800&q=85',
  'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=800&q=85',
  'https://images.unsplash.com/photo-1547891654-e66ed7ebb968?auto=format&fit=crop&w=800&q=85',
]

function useHlsVideo(ref: React.RefObject<HTMLVideoElement | null>) {
  useEffect(() => {
    const video = ref.current
    if (!video) return
    let hls: Hls | undefined
    if (Hls.isSupported()) {
      hls = new Hls({ enableWorker: true })
      hls.loadSource(videoSource)
      hls.attachMedia(video)
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = videoSource
    }
    return () => hls?.destroy()
  }, [ref])
}

function VideoBackdrop({ flip = false }: { flip?: boolean }) {
  const ref = useRef<HTMLVideoElement>(null)
  useHlsVideo(ref)
  return <video ref={ref} autoPlay muted loop playsInline aria-hidden="true" className={`absolute inset-0 h-full w-full object-cover ${flip ? 'scale-y-[-1]' : ''}`} />
}

function LoadingScreen({ onComplete }: { onComplete: () => void }) {
  const [count, setCount] = useState(0)
  const words = ['Design', 'Create', 'Inspire']
  useEffect(() => {
    const start = performance.now()
    let raf = 0
    const update = (now: number) => {
      const progress = Math.min((now - start) / 2700, 1)
      setCount(Math.floor(progress * 100))
      if (progress < 1) raf = requestAnimationFrame(update)
      else window.setTimeout(onComplete, 400)
    }
    raf = requestAnimationFrame(update)
    return () => cancelAnimationFrame(raf)
  }, [onComplete])

  return (
    <motion.div exit={{ opacity: 0 }} transition={{ duration: .55 }} className="fixed inset-0 z-[9999] bg-bg">
      <motion.p initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} className="absolute left-6 top-6 text-[11px] uppercase tracking-[.3em] text-muted md:left-10 md:top-10">Portfolio</motion.p>
      <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
        <AnimatePresence mode="wait">
          <motion.span key={words[Math.min(2, Math.floor(count / 34))]} initial={{ opacity: 0, y: 20 }} animate={{ opacity: .82, y: 0 }} exit={{ opacity: 0, y: -20 }} className="font-display text-5xl italic md:text-7xl lg:text-8xl">
            {words[Math.min(2, Math.floor(count / 34))]}
          </motion.span>
        </AnimatePresence>
      </div>
      <div className="absolute bottom-7 right-6 font-display text-7xl tabular-nums md:bottom-10 md:right-10 md:text-9xl">{String(count).padStart(3, '0')}</div>
      <div className="absolute inset-x-0 bottom-0 h-[3px] bg-stroke/50"><div className="accent-gradient h-full origin-left" style={{ transform: `scaleX(${count / 100})` }} /></div>
    </motion.div>
  )
}

function Navbar() {
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 100)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])
  const scrollTo = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
  return (
    <nav aria-label="Primary navigation" className="fixed inset-x-0 top-0 z-50 flex justify-center px-3 pt-4 md:pt-6">
      <div className={`flex items-center rounded-full border border-white/10 bg-surface/80 p-2 backdrop-blur-xl transition-shadow ${scrolled ? 'shadow-xl shadow-black/30' : ''}`}>
        <button onClick={() => scrollTo('home')} aria-label="Back to home" className="logo-ring grid h-9 w-9 shrink-0 place-items-center rounded-full p-[1.5px]"><span className="grid h-full w-full place-items-center rounded-full bg-bg font-display text-sm italic transition-transform hover:scale-105">MS</span></button>
        <span className="mx-1 hidden h-5 w-px bg-stroke sm:block" />
        {['Home', 'Work', 'Journal'].map((label, index) => <button key={label} onClick={() => scrollTo(index === 0 ? 'home' : label.toLowerCase())} className={`min-h-9 rounded-full px-3 text-xs transition-colors sm:px-4 sm:text-sm ${index === 0 ? 'bg-stroke/60 text-text-primary' : 'text-muted hover:bg-stroke/50 hover:text-text-primary'}`}>{label}</button>)}
        <span className="mx-1 h-5 w-px bg-stroke" />
        <button onClick={() => scrollTo('contact')} className="gradient-button compact"><span>Say hi <ArrowUpRight size={14} /></span></button>
      </div>
    </nav>
  )
}

function SectionHeader({ eyebrow, title, italic, text }: { eyebrow: string; title: string; italic: string; text: string }) {
  return (
    <motion.header initial={{ opacity: 0, y: 28 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-100px' }} transition={{ duration: .8 }} className="mb-10 flex flex-col justify-between gap-8 md:mb-14 md:flex-row md:items-end">
      <div>
        <div className="mb-5 flex items-center gap-3 text-[11px] uppercase tracking-[.3em] text-muted"><span className="h-px w-8 bg-stroke" />{eyebrow}</div>
        <h2 className="text-4xl tracking-tight sm:text-5xl md:text-6xl">{title} <em className="font-display font-normal">{italic}</em></h2>
        <p className="mt-4 max-w-md text-sm leading-7 text-muted md:text-base">{text}</p>
      </div>
      <button className="gradient-button hidden md:block"><span>View all <ArrowUpRight size={15} /></span></button>
    </motion.header>
  )
}

function Hero() {
  const root = useRef<HTMLElement>(null)
  const [role, setRole] = useState(0)
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      gsap.timeline({ defaults: { ease: 'power3.out' } })
        .from('.name-reveal', { opacity: 0, y: 50, duration: 1.2, delay: .1 })
        .from('.blur-in', { opacity: 0, filter: 'blur(10px)', y: 20, duration: 1, stagger: .1 }, '-=.7')
    }, root)
    return () => ctx.revert()
  }, [])
  useEffect(() => {
    const timer = window.setInterval(() => setRole((v) => (v + 1) % 4), 2000)
    return () => clearInterval(timer)
  }, [])
  const roles = ['Creative', 'Fullstack', 'Founder', 'Scholar']
  return (
    <section ref={root} id="home" className="relative flex min-h-dvh items-center justify-center overflow-hidden px-6 pb-20 pt-32 text-center">
      <VideoBackdrop />
      <div className="absolute inset-0 bg-black/35" />
      <div className="absolute inset-x-0 bottom-0 h-56 bg-gradient-to-t from-bg to-transparent" />
      <div className="relative z-10 mx-auto max-w-5xl">
        <p className="blur-in mb-7 text-[11px] uppercase tracking-[.35em] text-white/60">Collection '26</p>
        <h1 className="name-reveal mb-7 font-display text-[clamp(4.3rem,11vw,9.5rem)] italic leading-[.82] tracking-[-.045em]">Michael Smith</h1>
        <p className="blur-in text-base text-white/75 md:text-lg">A <span key={role} className="inline-block animate-role-fade-in font-display text-xl italic text-white md:text-2xl">{roles[role]}</span> lives in Chicago.</p>
        <p className="blur-in mx-auto mb-10 mt-4 max-w-md text-sm leading-6 text-white/60 md:text-base">Designing seamless digital interactions by focusing on the unique nuances which bring systems to life.</p>
        <div className="blur-in flex flex-wrap justify-center gap-3">
          <button onClick={() => document.getElementById('work')?.scrollIntoView({ behavior: 'smooth' })} className="primary-button">See works <ArrowDown size={15} /></button>
          <button onClick={() => document.getElementById('contact')?.scrollIntoView({ behavior: 'smooth' })} className="outline-button">Reach out <ArrowUpRight size={15} /></button>
        </div>
      </div>
      <div className="absolute bottom-5 left-1/2 z-10 -translate-x-1/2 text-[9px] uppercase tracking-[.25em] text-white/45"><span>Scroll</span><span className="mx-auto mt-2 block h-9 w-px overflow-hidden bg-white/15"><i className="block h-1/2 w-full animate-scroll-down bg-white/70" /></span></div>
    </section>
  )
}

function Works() {
  return (
    <section id="work" className="section-shell py-16 md:py-24">
      <SectionHeader eyebrow="Selected work" title="Featured" italic="projects" text="A selection of projects I've worked on, from the first sketch to the final launch." />
      <div className="grid grid-cols-1 gap-5 md:grid-cols-12 md:gap-6">
        {projects.map((project, index) => (
          <motion.article key={project.title} initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-80px' }} transition={{ duration: .7, delay: index * .06 }} className={`project-card group ${project.span} ${project.aspect}`}>
            <img src={project.image} alt={`${project.title} project cover`} loading={index > 1 ? 'lazy' : 'eager'} />
            <div className="halftone" />
            <div className="project-overlay"><span className="project-pill">View — <em>{project.title}</em> <ArrowUpRight size={15} /></span></div>
            <div className="absolute inset-x-0 bottom-0 flex items-end justify-between bg-gradient-to-t from-black/80 to-transparent p-5 pt-20 transition-opacity group-hover:opacity-0"><h3 className="text-lg">{project.title}</h3><span className="text-xs uppercase tracking-widest text-white/55">{project.category}</span></div>
          </motion.article>
        ))}
      </div>
    </section>
  )
}

function Journal() {
  return (
    <section id="journal" className="section-shell py-16 md:py-24">
      <SectionHeader eyebrow="Journal" title="Recent" italic="thoughts" text="Loose observations on creativity, technology, culture, and the spaces between them." />
      <div className="space-y-3">
        {journal.map((entry, index) => (
          <motion.button key={entry.title} initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} transition={{ delay: index * .06 }} className="journal-row group">
            <img src={entry.image} alt="" loading="lazy" />
            <span className="min-w-0 flex-1 text-left text-base sm:text-lg md:text-xl">{entry.title}</span>
            <span className="hidden text-xs uppercase tracking-widest text-muted md:block">{entry.date}</span>
            <span className="hidden min-w-14 text-xs text-muted sm:block">{entry.time}</span>
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full border border-stroke transition-all group-hover:rotate-45 group-hover:border-white/40"><ArrowUpRight size={16} /></span>
          </motion.button>
        ))}
      </div>
    </section>
  )
}

function Explorations() {
  const section = useRef<HTMLElement>(null)
  const center = useRef<HTMLDivElement>(null)
  const [selected, setSelected] = useState<string | null>(null)
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      ScrollTrigger.create({ trigger: section.current, start: 'top top', end: 'bottom bottom', pin: center.current, pinSpacing: false })
      gsap.to('.explore-left', { yPercent: -36, ease: 'none', scrollTrigger: { trigger: section.current, start: 'top bottom', end: 'bottom top', scrub: 1 } })
      gsap.to('.explore-right', { yPercent: 26, ease: 'none', scrollTrigger: { trigger: section.current, start: 'top bottom', end: 'bottom top', scrub: 1 } })
    }, section)
    return () => ctx.revert()
  }, [])
  return (
    <section ref={section} className="relative min-h-[300vh] overflow-hidden">
      <div ref={center} className="relative z-10 flex h-dvh items-center justify-center px-6 text-center">
        <div><p className="mb-5 text-[11px] uppercase tracking-[.35em] text-muted">Explorations</p><h2 className="text-5xl sm:text-6xl md:text-8xl">Visual <em className="font-display font-normal">playground</em></h2><p className="mx-auto mt-5 max-w-md text-sm leading-7 text-muted">Uncommissioned experiments, strange ideas, and visual notes made purely for the joy of making.</p><button className="outline-button mt-8">Follow the process <ArrowUpRight size={15} /></button></div>
      </div>
      <div className="pointer-events-none absolute inset-0 z-20 mx-auto grid max-w-[1300px] grid-cols-2 gap-8 px-4 md:gap-40 md:px-12">
        <div className="explore-left flex flex-col gap-[42vh] pt-[65vh]">{explorations.filter((_, i) => i % 2 === 0).map((src, i) => <button onClick={() => setSelected(src)} key={src} className="pointer-events-auto aspect-square w-full max-w-[320px] self-start overflow-hidden rounded-[2rem] border border-white/10 bg-surface shadow-2xl transition-transform hover:scale-[1.02]" style={{ transform: `rotate(${i % 2 ? 5 : -5}deg)` }}><img src={src} alt={`Visual experiment ${i * 2 + 1}`} loading="lazy" className="h-full w-full object-cover" /></button>)}</div>
        <div className="explore-right flex flex-col gap-[48vh] pt-[30vh]">{explorations.filter((_, i) => i % 2 === 1).map((src, i) => <button onClick={() => setSelected(src)} key={src} className="pointer-events-auto aspect-square w-full max-w-[320px] self-end overflow-hidden rounded-[2rem] border border-white/10 bg-surface shadow-2xl transition-transform hover:scale-[1.02]" style={{ transform: `rotate(${i % 2 ? -4 : 6}deg)` }}><img src={src} alt={`Visual experiment ${i * 2 + 2}`} loading="lazy" className="h-full w-full object-cover" /></button>)}</div>
      </div>
      <AnimatePresence>{selected && <motion.div role="dialog" aria-modal="true" aria-label="Exploration preview" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setSelected(null)} className="fixed inset-0 z-[100] grid place-items-center bg-black/85 p-5 backdrop-blur-lg"><button aria-label="Close preview" className="absolute right-5 top-5 grid h-12 w-12 place-items-center rounded-full bg-white text-black"><X /></button><motion.img initial={{ scale: .92 }} animate={{ scale: 1 }} src={selected} alt="Selected visual experiment" className="max-h-[82vh] max-w-[90vw] rounded-3xl object-contain" /></motion.div>}</AnimatePresence>
    </section>
  )
}

function Stats() {
  const stats = [['20+', 'Years experience'], ['95+', 'Projects done'], ['200%', 'Satisfied clients']]
  return <section className="section-shell py-20 md:py-28"><div className="grid overflow-hidden rounded-[2rem] border border-stroke sm:grid-cols-3">{stats.map(([value, label]) => <motion.div key={label} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="border-b border-stroke p-8 text-center last:border-0 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-12"><strong className="font-display text-6xl font-normal italic md:text-8xl">{value}</strong><p className="mt-3 text-[11px] uppercase tracking-[.24em] text-muted">{label}</p></motion.div>)}</div></section>
}

function Footer() {
  const marquee = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    const tween = gsap.to(marquee.current, { xPercent: -50, duration: 40, ease: 'none', repeat: -1 })
    return () => {
      tween.kill()
    }
  }, [])
  return (
    <footer id="contact" className="relative overflow-hidden border-t border-white/10 pt-24">
      <VideoBackdrop flip />
      <div className="absolute inset-0 bg-black/70" />
      <div className="relative z-10">
        <div className="overflow-hidden border-y border-white/10 py-5"><div ref={marquee} className="flex w-max whitespace-nowrap font-display text-5xl italic text-white/20 sm:text-7xl md:text-8xl">{Array.from({ length: 20 }, (_, i) => <span key={i} className="px-5">BUILDING THE FUTURE —</span>)}</div></div>
        <div className="section-shell py-20 text-center md:py-28"><p className="mb-5 text-[11px] uppercase tracking-[.35em] text-white/50">Have a project in mind?</p><h2 className="mx-auto max-w-4xl text-5xl leading-[.95] sm:text-7xl md:text-8xl">Let's make something <em className="font-display font-normal">memorable.</em></h2><a href="mailto:hello@michaelsmith.com" className="primary-button mt-9 inline-flex">hello@michaelsmith.com <ArrowUpRight size={16} /></a></div>
        <div className="section-shell flex flex-col items-center justify-between gap-5 border-t border-white/10 py-7 text-xs text-white/55 sm:flex-row"><div className="flex flex-wrap justify-center gap-5">{['Twitter', 'LinkedIn', 'Dribbble', 'GitHub'].map(item => <a key={item} href="#contact" className="transition-colors hover:text-white">{item}</a>)}</div><p className="flex items-center gap-2"><span className="h-2 w-2 animate-pulse-soft rounded-full bg-emerald-400" />Available for projects</p></div>
      </div>
    </footer>
  )
}

export default function App() {
  const [isLoading, setIsLoading] = useState(true)
  return (
    <>
      <AnimatePresence>{isLoading && <LoadingScreen onComplete={() => setIsLoading(false)} />}</AnimatePresence>
      <Navbar />
      <main><Hero /><Works /><Journal /><Explorations /><Stats /></main>
      <Footer />
    </>
  )
}
