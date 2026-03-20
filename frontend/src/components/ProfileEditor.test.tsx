import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ProfileEditor } from './ProfileEditor';
import { api } from '../api/client';
import { vi, test, expect, Mock } from 'vitest';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../context/TimeFormatContext';

// Mock the API client
vi.mock('../api/client', () => ({
  api: {
    createProfile: vi.fn(),
    getInsulins: vi.fn().mockResolvedValue({ data: [{ id: 'Fiasp', name: 'Fiasp' }] }),
    createInsulin: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

test('renders profile editor form', () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
  expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/insulin type/i)).toBeInTheDocument();
});

test('validates form and submits', async () => {
  const user = userEvent.setup();
  (api.createProfile as Mock).mockResolvedValue({ data: {} });
  const onSaved = vi.fn();

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor userId="user-1" onProfileSaved={onSaved} />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  // Fill form
  const nameInput = screen.getByLabelText(/name/i);
  await user.clear(nameInput);
  await user.type(nameInput, 'Test Profile');

  // Wait for insulins to load
  await waitFor(() => {
    expect(screen.getByRole('option', { name: 'Fiasp' })).toBeInTheDocument();
  });

  const insulinInput = screen.getByLabelText(/insulin type/i);
  await user.selectOptions(insulinInput, 'Fiasp');
  
  // Basal is pre-filled with one segment in defaultValues, just check it exists
  expect(screen.getByDisplayValue('0.5')).toBeInTheDocument();

  // Submit
  await user.click(screen.getByText(/save profile/i));

  await waitFor(() => {
    expect(api.createProfile).toHaveBeenCalledWith('user-1', expect.objectContaining({
      name: 'Test Profile',
      insulinType: 'Fiasp',
      durationOfAction: 300,
      basal: expect.arrayContaining([
        expect.objectContaining({ startTime: '00:00', value: 0.5 })
      ])
    }));
    expect(onSaved).toHaveBeenCalled();
  });
});

test('shows validation error for negative duration', async () => {
  const user = userEvent.setup();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  const durationInput = screen.getByLabelText(/duration/i);
  await user.clear(durationInput);
  await user.type(durationInput, '-10');

  await user.click(screen.getByText(/save profile/i));

  await waitFor(() => {
    expect(screen.getByText(/duration must be positive/i)).toBeInTheDocument();
  });
});
