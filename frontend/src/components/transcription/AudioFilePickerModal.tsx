import { FileTextOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, App, Empty, Input, Modal, Pagination, Radio, Spin, Tag, Tooltip } from 'antd'
import { useMemo, useState } from 'react'
import { useAudioFileList } from '../../hooks/useAudioFileList'
import { useCreateTranscription } from '../../hooks/useCreateTranscription'
import type { AudioFileListItem } from '../../types/api'
import { formatBytes, formatDuration } from '../../utils/formatters'

const PAGE_SIZE = 8

interface Props {
  open: boolean
  onCancel: () => void
  onCreated: () => void
}

type AudioFileNameFields = AudioFileListItem & {
  originalName?: string
  fileName?: string
}

function getFileName(file: AudioFileListItem) {
  const candidate = file as AudioFileNameFields
  return [candidate.originalFileName, candidate.originalName, candidate.fileName]
    .find((value) => value?.trim())
    ?.trim() || '未命名音频'
}

function getFileFormat(fileName: string) {
  const extension = fileName.match(/\.([^.]+)$/)?.[1]
  return extension ? extension.toUpperCase() : '未知格式'
}

function getCreateErrorMessage(error: string) {
  const message = error.trim()
  const looksTechnical = /(?:exception|traceback|stack\s*trace|java\.|org\.|com\.|funasr|grpc|redis|sql|http\s*\d{3})/i.test(message)
  const containsChinese = /[\u3400-\u9fff]/.test(message)
  return message && containsChinese && !looksTechnical
    ? message
    : '文字稿任务创建失败，请稍后重试'
}

export default function AudioFilePickerModal({ open, onCancel, onCreated }: Props) {
  const { message } = App.useApp()
  const [draftKeyword, setDraftKeyword] = useState('')
  const [keyword, setKeyword] = useState('')
  const [current, setCurrent] = useState(1)
  const [selectedId, setSelectedId] = useState<string>()
  const query = useMemo(() => ({ current, size: PAGE_SIZE, keyword }), [current, keyword])
  const { data, loading: filesLoading, error: filesError, refresh: refreshFiles } = useAudioFileList(query)
  const { create, loading: creating, error: createError, clearError } = useCreateTranscription()

  const search = (value: string) => {
    setKeyword(value.trim())
    setCurrent(1)
    setSelectedId(undefined)
    clearError()
  }

  const submit = async () => {
    if (!selectedId || creating) return
    clearError()
    const task = await create(selectedId)
    if (!task) return
    onCreated()
    onCancel()
    void message.success('文字稿任务已创建')
  }

  const close = () => {
    if (!creating) onCancel()
  }

  return (
    <Modal
      open={open}
      title="选择要生成文字稿的音频"
      width={720}
      okText="生成文字稿"
      cancelText="取消"
      confirmLoading={creating}
      okButtonProps={{ disabled: !selectedId || filesLoading }}
      cancelButtonProps={{ disabled: creating }}
      maskClosable={!creating}
      closable={!creating}
      onOk={submit}
      onCancel={close}
    >
      <div className="audio-picker">
        <label className="audio-picker__search-label" htmlFor="transcription-audio-search">按文件名搜索</label>
        <Input.Search
          id="transcription-audio-search"
          value={draftKeyword}
          allowClear
          enterButton={<SearchOutlined aria-label="搜索" />}
          placeholder="输入音频文件名"
          onChange={(event) => {
            const value = event.target.value
            setDraftKeyword(value)
            if (!value) search('')
          }}
          onSearch={search}
        />

        {createError && (
          <Alert
            type="error"
            showIcon
            message="创建失败"
            description={getCreateErrorMessage(createError)}
          />
        )}

        {filesError && (
          <Alert
            type="error"
            showIcon
            message="音频文件加载失败"
            description="暂时无法获取音频文件，请重试。"
            action={<button type="button" className="audio-picker__retry" onClick={refreshFiles}>重试</button>}
          />
        )}

        <Spin spinning={filesLoading} tip="正在加载音频文件">
          {!filesLoading && !filesError && data.records.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={keyword ? '没有找到匹配的音频文件' : '暂无可用音频文件'}
            />
          ) : (
            <Radio.Group
              className="audio-picker__group"
              value={selectedId}
              disabled={creating}
              aria-label="选择一个音频文件"
              onChange={(event) => {
                setSelectedId(event.target.value)
                clearError()
              }}
            >
              {data.records.map((file) => {
                const selected = selectedId === file.audioFileId
                const inputId = `transcription-audio-${file.audioFileId}`
                const fileName = getFileName(file)
                return (
                  <div
                    key={file.audioFileId}
                    className={`audio-picker__item${selected ? ' is-selected' : ''}`}
                    onClick={() => {
                      setSelectedId(file.audioFileId)
                      clearError()
                    }}
                  >
                    <Radio id={inputId} value={file.audioFileId} aria-label={`选择音频：${fileName}`} />
                    <label htmlFor={inputId} className="audio-picker__content">
                      <span className="audio-picker__header">
                        <Tooltip title={fileName} placement="topLeft" mouseEnterDelay={0.35}>
                          <strong className="audio-picker__filename">
                            <FileTextOutlined />
                            <span>{fileName}</span>
                          </strong>
                        </Tooltip>
                        {file.transcriptionStatus === 'SUCCESS' && <Tag color="success">已有文字稿</Tag>}
                      </span>
                      <span className="audio-picker__meta">
                        {getFileFormat(fileName)} · {formatDuration(file.duration)} · {formatBytes(file.fileSize)}
                      </span>
                    </label>
                  </div>
                )
              })}
            </Radio.Group>
          )}
        </Spin>

        {data.total > PAGE_SIZE && (
          <Pagination
            current={data.current}
            pageSize={PAGE_SIZE}
            total={data.total}
            showSizeChanger={false}
            showTotal={(total) => `共 ${total} 个音频`}
            onChange={(page) => {
              setCurrent(page)
              setSelectedId(undefined)
              clearError()
            }}
          />
        )}
      </div>
    </Modal>
  )
}
