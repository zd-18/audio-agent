import { CloudUploadOutlined, SoundOutlined } from '@ant-design/icons'
import { useRef, useState } from 'react'

interface AudioUploadDropzoneProps {
  disabled?: boolean
  onSelect: (file: File) => void
}

export default function AudioUploadDropzone({ disabled, onSelect }: AudioUploadDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  const choose = (files: FileList | null) => {
    const file = files?.[0]
    if (file) onSelect(file)
  }

  return (
    <div
      className={`audio-upload-dropzone${dragging ? ' audio-upload-dropzone--dragging' : ''}${disabled ? ' audio-upload-dropzone--disabled' : ''}`}
      onDragEnter={(event) => { event.preventDefault(); if (!disabled) setDragging(true) }}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={(event) => { event.preventDefault(); if (event.currentTarget === event.target) setDragging(false) }}
      onDrop={(event) => { event.preventDefault(); setDragging(false); if (!disabled) choose(event.dataTransfer.files) }}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".mp3,.wav,.m4a,.mp4,audio/mpeg,audio/wav,audio/mp4,video/mp4"
        onChange={(event) => { choose(event.target.files); event.target.value = '' }}
        disabled={disabled}
        aria-label="选择音频文件"
      />
      <button type="button" onClick={() => inputRef.current?.click()} disabled={disabled}>
        <span className="audio-upload-dropzone__visual" aria-hidden="true">
          <CloudUploadOutlined />
          <span className="audio-upload-wave"><i /><i /><i /><i /><i /><i /><i /></span>
        </span>
        <strong>{dragging ? '释放文件以完成选择' : '拖拽音频到这里，或点击选择文件'}</strong>
        <span>支持 MP3、WAV、M4A、MP4，单文件最大 500MB</span>
        <small><SoundOutlined /> 暂不支持分片与断点续传</small>
      </button>
    </div>
  )
}

