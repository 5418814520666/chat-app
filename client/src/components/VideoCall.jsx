import { useState, useEffect, useRef } from 'react'

export default function VideoCall({
  localStream,
  remoteStream,
  isCaller,
  peerUsername,
  onHangUp,
  onClose,
  onToggleMute
}) {
  const localVideoRef = useRef(null)
  const remoteVideoRef = useRef(null)

  useEffect(() => {
    if (localVideoRef.current && localStream) {
      localVideoRef.current.srcObject = localStream
    }
  }, [localStream])

  useEffect(() => {
    if (remoteVideoRef.current && remoteStream) {
      remoteVideoRef.current.srcObject = remoteStream
    }
  }, [remoteStream])

  const [videoMuted, setVideoMuted] = useState(false)
  const [audioMuted, setAudioMuted] = useState(false)

  return (
    <div className="video-call-panel">
      <div className="video-call-header">
        <h3>视频通话 - {peerUsername || '等待中...'}</h3>
        <button className="close-video-btn" onClick={onClose}>X</button>
      </div>
      <div className="video-area">
        <div className="video-box">
          {remoteStream ? (
            <video ref={remoteVideoRef} autoPlay playsInline />
          ) : (
            <div className="video-placeholder">
              {isCaller ? '呼叫中...' : '等待对方视频...'}
            </div>
          )}
          <span className="video-label">{peerUsername || '对方'}</span>
        </div>
        <div className="video-box local">
          {localStream ? (
            <video ref={localVideoRef} autoPlay playsInline muted />
          ) : (
            <div className="video-placeholder">摄像头未开启</div>
          )}
          <span className="video-label">你</span>
        </div>
      </div>
      <div className="video-controls">
        <button
          className={`video-ctrl-btn mute-btn ${audioMuted ? 'muted' : ''}`}
          onClick={() => { onToggleMute('audio'); setAudioMuted(!audioMuted) }}
          title={audioMuted ? '取消静音' : '静音'}
        >
          {audioMuted ? 'M' : 'A'}
        </button>
        <button
          className={`video-ctrl-btn mute-btn ${videoMuted ? 'muted' : ''}`}
          onClick={() => { onToggleMute('video'); setVideoMuted(!videoMuted) }}
          title={videoMuted ? '开启摄像头' : '关闭摄像头'}
        >
          {videoMuted ? 'V' : 'C'}
        </button>
        <button className="video-ctrl-btn hangup-btn" onClick={onHangUp} title="挂断">
          T
        </button>
      </div>
    </div>
  )
}
