export function getUsernameInitial(username?: string | null) {
  const normalizedUsername = username?.trim()
  if (!normalizedUsername) return '用'
  return Array.from(normalizedUsername)[0].toUpperCase()
}
