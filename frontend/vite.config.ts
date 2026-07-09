import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발시 /api 요청을 백엔드(9080)로 프록시한다.
// 운영 배포는 백엔드와 같은 오리진에 정적 파일로 배포하는 것을 전제로 한다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 테넌트 서브도메인 병행 방식 로컬 테스트용: /etc/hosts나 브라우저 host-resolver로
    // {코드}.webatt.local을 127.0.0.1에 매핑해 접속한다(프록시가 Host를 보존하므로
    // 백엔드는 APP_TENANT_BASE_DOMAIN=webatt.local로 기동).
    allowedHosts: ['.webatt.local'],
    proxy: {
      // changeOrigin을 끄고 원래 Host를 백엔드에 전달한다 — 서브도메인 병행 방식은
      // Host 헤더로 테넌트를 해석하므로 프록시가 이를 보존해야 한다(운영 리버스 프록시도 동일).
      '/api': { target: 'http://localhost:9080', changeOrigin: false },
    },
  },
})
