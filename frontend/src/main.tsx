import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AppProvider } from './app/AppContext'
import App from './App'
//한글 UI 품질의 기준 폰트 — 동적 서브셋(필요한 글리프 범위만 로드), 번들 자산이라 런타임 외부 요청 없음
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './app.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProvider>
      <App />
    </AppProvider>
  </StrictMode>,
)
