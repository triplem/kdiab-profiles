import { DefaultApi } from './generated';
import axios from 'axios';

// Create a single axios instance
const axiosInstance = axios.create({
  baseURL: '/api/v1', // Proxy will handle this or direct URL
});

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID();
  }

  // Retrieve token from oidc storage
  const authority = "http://localhost:8081/realms/kdiab-profiles";
  const clientId = "kdiab-frontend";
  const oidcStorageKey = `oidc.user:${authority}:${clientId}`;
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
