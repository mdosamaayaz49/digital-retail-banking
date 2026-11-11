import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          charts: ["recharts"],
          identity: ["keycloak-js"],
          forms: ["react-hook-form", "zod", "@hookform/resolvers/zod"],
        },
      },
    },
  },
  server: { host: "0.0.0.0", proxy: { "/api": "http://localhost:8080" } },
});
