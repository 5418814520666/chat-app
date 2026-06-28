export default function UserList({ users, currentUsername, onCall, activeCall }) {
  if (users.length === 0) {
    return (
      <div className="user-list">
        <div style={{ padding: '16px', textAlign: 'center', color: '#aaa', fontSize: '13px' }}>
          暂无其他用户
        </div>
      </div>
    )
  }

  return (
    <div className="user-list">
      {users.map((user) => {
        const initial = (user.username || '?')[0].toUpperCase()
        const inCall = activeCall && activeCall.peerId === user.id
        return (
          <div
            key={user.id}
            className="user-item"
            onClick={() => onCall(user)}
            title={`点击呼叫 ${user.username}`}
          >
            <div className="user-avatar">{initial}</div>
            <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {user.username}
              {inCall && <span style={{ marginLeft: 6, fontSize: 11, color: '#4caf50' }}>(通话中)</span>}
            </span>
            <span className="user-online" />
          </div>
        )
      })}
    </div>
  )
}
