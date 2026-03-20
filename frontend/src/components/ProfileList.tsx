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
  const [expandedProfileId, setExpandedProfileId] = React.useState<string | null>(null);

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

  const activateMutation = useMutation({
    mutationFn: (profileId: string) => api.activateProfile(userId, profileId),
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

  const proposedProfiles = profiles.filter(p => (p.status as string || '') === 'PROPOSED');
  const otherProfiles = profiles.filter(p => (p.status as string || '') === 'ACTIVE' || (p.status as string || '') === 'DRAFT');

  const toggleExpand = (profileId: string) => {
    setExpandedProfileId(prev => prev === profileId ? null : profileId);
  };

  const renderProfileCard = (profile: Profile) => {
    const isExpanded = expandedProfileId === profile.id;
    return (
      <div key={profile.id} className="profile-card" onClick={() => {
        toggleExpand(profile.id);
      }}>
        <div className="profile-card-header">
          <strong>{profile.name}</strong>
          <div>
            <span className={`status-badge status-${(profile.status as string || 'Unknown').toLowerCase()}`}>{profile.status as string || 'Unknown'}</span>
            <button 
              onClick={(e) => { e.stopPropagation(); onSelectProfile?.(profile); }}
              className="btn"
              style={{ marginLeft: '10px', padding: '2px 8px', fontSize: '0.8rem' }}
            >
              Edit
            </button>
            {profile.status !== 'ACTIVE' && profile.status !== 'PROPOSED' && (
              <button 
                onClick={(e) => { e.stopPropagation(); activateMutation.mutate(profile.id); }}
                className="btn primary"
                style={{ marginLeft: '10px', padding: '2px 8px', fontSize: '0.8rem' }}
                disabled={activateMutation.isPending}
              >
                {activateMutation.isPending && activateMutation.variables === profile.id ? 'Activating...' : 'Activate'}
              </button>
            )}
          </div>
        </div>
        <div className="profile-card-body">
          <p>Insulin: {profile.insulinType || 'N/A'} • Action: {profile.durationOfAction || 0}m</p>
          <p className="segments-count">
            {profile.basal?.length || 0} Basal • {profile.icr?.length || 0} ICR • {profile.isf?.length || 0} ISF
          </p>
          {isExpanded && (
            <div className="profile-details">
              {profile.basal && profile.basal.length > 0 && (
                <div className="detail-section">
                  <h4>Basal Segments</h4>
                  <ul>
                    {profile.basal.map((b, i) => <li key={i}>{b?.startTime} - {b?.value} U/hr</li>)}
                  </ul>
                </div>
              )}
              {profile.icr && profile.icr.length > 0 && (
                <div className="detail-section">
                  <h4>ICR Segments</h4>
                  <ul>
                    {profile.icr.map((icr, i) => <li key={i}>{icr?.startTime} - {icr?.value} g/U</li>)}
                  </ul>
                </div>
              )}
              {profile.isf && profile.isf.length > 0 && (
                <div className="detail-section">
                  <h4>ISF Segments</h4>
                  <ul>
                    {profile.isf.map((isf, i) => <li key={i}>{isf?.startTime} - {isf?.value} mg/dL</li>)}
                  </ul>
                </div>
              )}
            </div>
          )}
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
  };

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
