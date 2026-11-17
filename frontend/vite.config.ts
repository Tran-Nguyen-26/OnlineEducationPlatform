import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  // Keep Vite's pre-bundle cache off the project drive (the NTFS volume is nearly out of file records).
  cacheDir: process.env.VITE_CACHE_DIR ?? '/tmp/oep-vite-cache',
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
})
