import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit()],
	// Dev-only proxy so the same relative paths used in production (behind nginx)
	// also work with `npm run dev`. In Docker, nginx handles this routing instead.
	server: {
		proxy: {
			'/api': 'http://localhost:8082',
			'/auth': 'http://localhost:8081'
		}
	}
});
