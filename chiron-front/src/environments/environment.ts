/**
 * Environment configuration for development.
 * Defines the base API URL and marks the build as non-production.
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:9090/api',
  // PWA Olympus (sous-domaine dédié). En dev : serveur Vite local.
  olympusUrl: 'http://localhost:5174/',
  glauxUrl: 'http://localhost:4300/'
};
