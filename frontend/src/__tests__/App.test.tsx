import { render, screen, waitFor } from '@testing-library/react';
import App from '../App';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../context/TimeFormatContext';
import { vi, test, expect } from 'vitest';
import { api } from '../api/client';

vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(),
}));
import { useAuth } from 'react-oidc-context';

// Mock the API client globally for this test file
vi.mock('../api/client', () => ({
  api: {
    listProfiles: vi.fn(),
    createProfile: vi.fn(),
    getProfileHistory: vi.fn(),
  },
}));

test('renders main heading', async () => {
  // Mock listProfiles to return empty list or promise that doesn't resolve immediately
  // to avoid "act" warnings if possible, though mostly harmless here.
  (api.listProfiles as any).mockResolvedValue({ data: [] });

  (useAuth as any).mockReturnValue({
    isLoading: false,
    error: null,
    isAuthenticated: true,
    user: {
      profile: {
        sub: '11111111-1111-1111-1111-111111111111',
        name: 'Sarah Patient',
        preferred_username: 'sarah'
      }
    },
    signinRedirect: vi.fn(),
    removeUser: vi.fn()
  });

  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <App />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
  const heading = screen.getByText(/T1D Profile Manager/i);
  expect(heading).toBeInTheDocument();
  
  // Also check if navigation buttons are present
  expect(screen.getByText(/Create New Profile/i)).toBeInTheDocument();
});
