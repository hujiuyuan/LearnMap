import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  server: {
    // H5 开发时把 /api 转发到本地后端
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
