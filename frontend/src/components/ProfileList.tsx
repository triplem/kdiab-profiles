import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, customApi } from '../api/client';
import type { Profile } from '../api/generated';

interface ProfileListProps {
  userId: string;
  onSelectProfile?: (profile: Profile) => void;
}

export const ProfileList: React.FC<ProfileListProps> = ({ userId, onSelectProfile }) => {
  const queryClient = useQueryClient();

  const { data: profiles = [] as Profile[], isLoading, isError, error } = useQuery<Profile[]>({
    queryKey: ['profiles', userId],
    queryFn: async () => {
      const response = await api.listProfiles(userId);
      return response.data;
    },
    enabled: !!userId,
  });

  const acceptMutation = useMutation({
    mutationFn: (profileId: string) => customApi.acceptProposedProfile(userId, profileId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['profiles', userId] }),
  });

  const rejectMutation = useMutation({
    mutationFn: (profileId: string) => customApi.rejectProposedProfile(userId, profileId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['profiles', userId] }),
  });

  const handleAccept = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation();
    acceptMutation.mutate(profileId);
  };

  const handleReject = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation();
    rejectMutation.mutate(profileId);
  };

  if (isLoading) return <div className="loading">Loading profiles...</div>;
  if (isError) return <div className="error">Error: {(error as Error).message}</div>;

  const proposedProfiles = profiles.filter(p => (p.status as string) === 'PROPOSED');
  const otherProfiles = profiles.filter(p => (p.status as string) !== 'PROPOSED');

  const renderProfileCard = (profile: Profile) => (
    <div key={profile.id} className="profile-card" onClick={() => onSelectProfile?.(profile)}>
      <div className="profile-card-header">
        <strong>{profile.name}</strong>
        <span className={`status-badge status-${profile.status.toLowerCase()}`}>{profile.status}</span>
      </div>
      <div className="profile-card-body">
        <p>Insulin: {profile.insulinType} • Action: {profile.durationOfAction}m</p>
        <p className="segments-count">
          {profile.basal?.length || 0} Basal • {profile.icr?.length || 0} ICR • {profile.isf?.length || 0} ISF
        </p>
      </div>
      {(profile.status as string) === 'PROPOSED' && (
        <div className="proposal-actions">
          <button 
            onClick={(e) => handleAccept(e, profile.id)} 
            className="btn primary"
            disabled={acceptMutation.isPending}
          >
            {acceptMutation.isPending && acceptMutation.variables === profile.id ? 'Accepting...' : 'Accept'}
          </button>
          <button 
            onClick={(e) => handleReject(e, profile.id)} 
            className="btn danger outline"
            disabled={rejectMutation.isPending}
          >
            {rejectMutation.isPending && rejectMutation.variables === profile.id ? 'Rejecting...' : 'Reject'}
          </button>
        </div>
      )}
    </div>
  );

  return (
    <div className="profile-list">
      <h2>Profiles</h2>
      {profiles.length === 0 ? (
        <p>No profiles found.</p>
      ) : (
        <>
          {proposedProfiles.length > 0 && (
            <div className="proposed-section" style={{ marginBottom: '2rem' }}>
              <h3 style={{ color: '#fbbf24', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                ⚠️ Pending Doctor Recommendations
              </h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                Accepting a proposed profile will automatically archive your current active configuration.
              </p>
              <div className="card-grid">
                {proposedProfiles.map(renderProfileCard)}
              </div>
            </div>
          )}

          {otherProfiles.length > 0 && (
            <div className="active-section">
              <h3>Your Configurations</h3>
              <div className="card-grid">
                {otherProfiles.map(renderProfileCard)}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};
