import { describe, expect, it } from 'vitest'
import { parseTranscript } from './LessonPlayer'

describe('parseTranscript', () => {
  it('parses VTT captions and ignores notes', () => {
    expect(parseTranscript('WEBVTT\n\nNOTE hidden\n00:00.000 --> 00:01.000\nIgnore\n\n00:15.250 --> 00:17.000\n<i>Ví dụ</i>'))
      .toEqual([{ start: 15.25, label: '00:15', text: 'Ví dụ' }])
  })

  it('parses SRT with Windows newlines and hour offsets', () => {
    expect(parseTranscript('1\r\n01:02:03,500 --> 01:02:05,000\r\nDòng đầu\r\nDòng sau'))
      .toEqual([{ start: 3723.5, label: '01:02:03', text: 'Dòng đầu Dòng sau' }])
  })
})
