const TOKEN_KEY = 'diet.auth.token'
export const AUTH_CHANGED_EVENT = 'diet-auth-changed'

export function authToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function saveAuthToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token)
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
}

export function clearAuthToken() {
  sessionStorage.removeItem(TOKEN_KEY)
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
}
