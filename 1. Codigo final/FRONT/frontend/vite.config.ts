import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  // En desarrollo, /api se reenvía a la API de Spring Boot (VITE_API_URL vacío).
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
