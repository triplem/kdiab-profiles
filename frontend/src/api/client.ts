import { DefaultApi } from './generated';
import axios from 'axios';

// OIDC config — override via .env (VITE_OIDC_AUTHORITY, VITE_OIDC_CLIENT_ID) for staging/prod
const OIDC_CONFIG = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/kdiab-profiles',
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'kdiab-frontend',
};

// Create a single axios instance
const axiosInstance = axios.create({
  baseURL: '/api/v1',
});

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID();
  }

  const oidcStorageKey = `oidc.user:${OIDC_CONFIG.authority}:${OIDC_CONFIG.clientId}`;
  const oidcStorageStr = sessionStorage.getItem(oidcStorageKey);

  if (oidcStorageStr) {
    try {
      const oidcStorage = JSON.parse(oidcStorageStr);
      if (oidcStorage?.access_token) {
        config.headers['Authorization'] = `Bearer ${oidcStorage.access_token}`;
      }
    } catch (e) {
      console.error('Failed to parse OIDC storage', e);
    }
  }

  return config;
});

axiosInstance.interceptors.response.use((response) => {
  const correlationId = response.headers['x-correlation-id'];
  if (correlationId) {
    console.debug(`[Response Correlation-ID: ${correlationId}]`);
  }
  return response;
});

// Export the API client
export const api = new DefaultApi(undefined, '/api/v1', axiosInstance);

export const customApi = {
  acceptProposedProfile: (userId: string, profileId: string) => 
    axiosInstance.post(`/users/${userId}/profiles/${profileId}/accept`),
  rejectProposedProfile: (userId: string, profileId: string) => 
    axiosInstance.post(`/users/${userId}/profiles/${profileId}/reject`),
};
