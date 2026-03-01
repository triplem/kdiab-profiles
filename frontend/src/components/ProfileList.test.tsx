import { render, screen, waitFor } from '@testing-library/react';
import { ProfileList } from './ProfileList';
import { api } from '../api/client';
import { Profile, ProfileStatusEnum } from '../api/generated';
import { vi, test, expect, Mock } from 'vitest';

// Mock the API client
vi.mock('../api/client', () => ({
  api: {
    listProfiles: vi.fn(),
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

test('renders profile list', async () => {
  (api.listProfiles as Mock).mockResolvedValue({ data: mockProfiles });

  render(<ProfileList userId="user-1" />);

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

  render(<ProfileList userId="user-1" />);

  await waitFor(() => {
    expect(screen.getByText(/failed to fetch profiles/i)).toBeInTheDocument();
  });
});
