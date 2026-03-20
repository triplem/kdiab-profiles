import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Insulin } from '../api/generated';

interface AdminInsulinManagerProps {
  // If we wanted to pass specific admin tokens or anything, we could here
}

export const AdminInsulinManager: React.FC<AdminInsulinManagerProps> = () => {
  const queryClient = useQueryClient();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState<string>('');
  const [newName, setNewName] = useState<string>('');

  const { data: insulins = [], isLoading, error } = useQuery<Insulin[]>({
    queryKey: ['insulins-admin'],
    queryFn: () => api.getInsulins().then(res => res.data),
  });

  const createMutation = useMutation({
    mutationFn: (name: string) => api.createInsulin({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
      setNewName('');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, name }: { id: string, name: string }) => api.updateInsulin(id, { name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
      setEditingId(null);
      setEditName('');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteInsulin(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
    },
  });

  if (isLoading) return <div>Loading insulins...</div>;
  if (error) return <div>Error loading insulins: {(error as Error).message}</div>;

  return (
    <div className="admin-container" style={{ padding: '20px', maxWidth: '600px', margin: '0 auto', border: '1px solid var(--border-color, #444)', borderRadius: '8px' }}>
      <h2>Manage Global Insulins</h2>
      <p style={{ fontSize: '0.9rem', color: 'var(--text-color, #ccc)', marginBottom: '20px' }}>
        Renaming or deleting insulins only affects the autocomplete list for <b>new</b> profiles. Users' existing profiles are never modified by these changes.
      </p>

      <ul style={{ listStyle: 'none', padding: 0 }}>
        {insulins.map((insulin) => (
          <li key={insulin.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid var(--border-color, #444)' }}>
            {editingId === insulin.id ? (
              <div style={{ display: 'flex', gap: '8px', flex: 1, marginRight: '16px' }}>
                <input 
                  type="text" 
                  value={editName} 
                  onChange={(e) => setEditName(e.target.value)} 
                  autoFocus
                  style={{ flex: 1, padding: '4px' }}
                />
                <button 
                  onClick={() => updateMutation.mutate({ id: insulin.id, name: editName })}
                  disabled={updateMutation.isPending || !editName.trim()}
                  className="btn primary"
                >Save</button>
                <button 
                  onClick={() => { setEditingId(null); setEditName(''); }}
                  className="btn"
                >Cancel</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flex: 1 }}>
                <span>{insulin.name}</span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button 
                    onClick={() => { setEditingId(insulin.id); setEditName(insulin.name); }}
                    className="btn"
                    style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                  >Rename</button>
                  <button 
                    onClick={() => {
                      if (confirm(`Are you sure you want to delete ${insulin.name}?`)) {
                        deleteMutation.mutate(insulin.id);
                      }
                    }}
                    className="btn danger"
                    style={{ fontSize: '0.8rem', padding: '2px 8px', background: '#e53e3e', color: 'white' }}
                  >Delete</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div style={{ marginTop: '20px', display: 'flex', gap: '8px' }}>
        <input 
          type="text" 
          value={newName} 
          onChange={(e) => setNewName(e.target.value)} 
          placeholder="New insulin name..."
          style={{ flex: 1, padding: '8px' }}
        />
        <button 
          onClick={() => createMutation.mutate(newName)}
          disabled={createMutation.isPending || !newName.trim()}
          className="btn primary"
        >
          Add Insulin
        </button>
      </div>
    </div>
  );
};
