import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발시 /api 요청을 백엔드(9080)로 프록시한다.
// 운용은 백엔드와 같은 오리진에 정적 파일로 배포하는 것을 전제로 한다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:9080',
    },
  },
})
