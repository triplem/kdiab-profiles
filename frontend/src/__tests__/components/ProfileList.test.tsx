import { render, screen, waitFor } from '@testing-library/react';
import { ProfileList } from '../../components/ProfileList';
import { api } from '../../api/client';
import { Profile, ProfileStatusEnum } from '../../api/generated';
import { vi, test, expect, Mock } from 'vitest';

// Mock the API client
vi.mock('../../api/client', () => ({
  api: {
    listProfiles: vi.fn(),
    activateProfile: vi.fn(),
  },
}));

const mockProfiles: Profile[] = [
  {
    id: '1',
    userId: 'user-1',
    name: 'Morning Profile',
    insulinType: 'Humalog',
    durationOfAction: 360,
    status: ProfileStatusEnum.Active,
  },
  {
    id: '2',
    userId: 'user-1',
    name: 'Weekend Profile',
    insulinType: 'Humalog',
    durationOfAction: 360,
    status: ProfileStatusEnum.Draft,
  },
];

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

test('renders profile list', async () => {
  (api.listProfiles as Mock).mockResolvedValue({ data: mockProfiles });

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ProfileList userId="user-1" />
    </QueryClientProvider>
  );

  // Should show loading initially
  expect(screen.getByText(/loading/i)).toBeInTheDocument();

  // Should show profiles after fetch
  await waitFor(() => {
    expect(screen.getByText(/Morning Profile/i)).toBeInTheDocument();
    expect(screen.getByText(/Weekend Profile/i)).toBeInTheDocument();
  });
});

test('handles fetch error', async () => {
  (api.listProfiles as Mock).mockRejectedValue(new Error('Network error'));

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ProfileList userId="user-1" />
    </QueryClientProvider>
  );

  await waitFor(() => {
    expect(screen.getByText(/Network error/i)).toBeInTheDocument();
  });
});

import userEvent from '@testing-library/user-event';

test('activates a draft profile', async () => {
  const user = userEvent.setup();
  (api.listProfiles as Mock).mockResolvedValue({ data: mockProfiles });
  (api.activateProfile as Mock).mockResolvedValue({ data: {} });

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ProfileList userId="user-1" />
    </QueryClientProvider>
  );

  await waitFor(() => {
    expect(screen.getByText(/Weekend Profile/i)).toBeInTheDocument();
  });

  // The Activate button should be present for the Draft profile
  const activateButton = screen.getByText(/Activate/i);
  expect(activateButton).toBeInTheDocument();

  await user.click(activateButton);

  await waitFor(() => {
    expect(api.activateProfile).toHaveBeenCalledWith('user-1', '2');
  });
});
