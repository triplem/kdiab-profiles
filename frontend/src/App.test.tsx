import { render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { vi, test, expect } from 'vitest';
import { api } from './api/client';

// Mock the API client globally for this test file
vi.mock('./api/client', () => ({
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

  render(<App />);
  const heading = screen.getByText(/T1D Profile Manager/i);
  expect(heading).toBeInTheDocument();
  
  // Also check if navigation buttons are present
  expect(screen.getByText(/Create New/i)).toBeInTheDocument();
});
