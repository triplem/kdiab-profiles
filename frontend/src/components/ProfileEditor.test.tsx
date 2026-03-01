import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ProfileEditor } from './ProfileEditor';
import { api } from '../api/client';
import { vi, test, expect, Mock } from 'vitest';
import userEvent from '@testing-library/user-event';

// Mock the API client
vi.mock('../api/client', () => ({
  api: {
    createProfile: vi.fn(),
  },
}));

test('renders profile editor form', () => {
  render(<ProfileEditor userId="user-1" />);
  expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/insulin type/i)).toBeInTheDocument();
});

test('validates form and submits', async () => {
  const user = userEvent.setup();
  (api.createProfile as Mock).mockResolvedValue({ data: {} });
  const onSaved = vi.fn();

  render(<ProfileEditor userId="user-1" onProfileSaved={onSaved} />);

  // Fill form
  const nameInput = screen.getByLabelText(/name/i);
  await user.clear(nameInput);
  await user.type(nameInput, 'Test Profile');

  const insulinInput = screen.getByLabelText(/insulin type/i);
  await user.clear(insulinInput);
  await user.type(insulinInput, 'Fiasp');
  
  // Basal is pre-filled with one segment in defaultValues, just check it exists
  expect(screen.getByDisplayValue('0.5')).toBeInTheDocument();

  // Submit
  await user.click(screen.getByText(/save profile/i));

  await waitFor(() => {
    expect(api.createProfile).toHaveBeenCalledWith('user-1', expect.objectContaining({
      name: 'Test Profile',
      insulinType: 'Fiasp',
      durationOfAction: 360,
      basal: expect.arrayContaining([
        expect.objectContaining({ startTime: '00:00', value: 0.5 })
      ])
    }));
    expect(onSaved).toHaveBeenCalled();
  });
});

test('shows validation error for negative duration', async () => {
  const user = userEvent.setup();
  render(<ProfileEditor userId="user-1" />);

  const durationInput = screen.getByLabelText(/duration/i);
  await user.clear(durationInput);
  await user.type(durationInput, '-10');

  await user.click(screen.getByText(/save profile/i));

  await waitFor(() => {
    expect(screen.getByText(/duration must be positive/i)).toBeInTheDocument();
  });
});
