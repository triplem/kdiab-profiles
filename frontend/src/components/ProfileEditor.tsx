import React, { useState } from 'react';
import { useForm, useFieldArray, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api } from '../api/client';
import type { CreateProfileRequest, Profile } from '../api/generated';
import { useQuery } from '@tanstack/react-query';
import { useTimeFormat } from '../context/TimeFormatContext';
import { TimeInput } from './TimeInput';

export const getNextSegment = <T extends { startTime: string, value: number }>(
  fields: T[], 
  defaultValue: number
) => {
  if (fields.length === 0) return { startTime: "00:00", value: defaultValue };
  
  // Sort fields to find the actual last one chronologically
  const sorted = [...fields].sort((a, b) => a.startTime.localeCompare(b.startTime));
  const last = sorted[sorted.length - 1];
  
  const [h, m] = last.startTime.split(':').map(Number);
  const totalMinutes = h * 60 + m;
  const nextTotalMinutes = Math.min(totalMinutes + 60, 23 * 60 + 45); // Max 23:45
  
  const nextH = Math.floor(nextTotalMinutes / 60);
  const nextM = nextTotalMinutes % 60;
  
  const nextStartTime = `${String(nextH).padStart(2, '0')}:${String(nextM).padStart(2, '0')}`;
  
  // If we already reached the end, just return the last one + a tiny increment if possible, 
  // but usually 60m is fine for UX.
  return { startTime: nextStartTime, value: last.value };
};


// Define Validation Schema
const timeSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(0, "Value must be positive"),
});

const validateChronological = (arr: { startTime: string }[]) => {
  if (arr.length <= 1) return true;
  for (let i = 0; i < arr.length - 1; i++) {
    if (arr[i].startTime >= arr[i+1].startTime) return false;
  }
  return true;
};

const icrSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(1.0, "ICR must be >= 1.0 g/U").max(50.0, "ICR must be <= 50.0 g/U"),
});

const isfSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(10.0, "ISF must be >= 10.0 mg/dL").max(200.0, "ISF must be <= 200.0 mg/dL"),
});

const profileSchema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  insulinType: z.string().min(1, "Insulin type is required"),
  durationOfAction: z.number().int().min(1, "Duration must be positive (minutes)"),
  basal: z.array(timeSegmentSchema)
    .nonempty("At least one basal segment required")
    .refine(arr => arr[0].startTime === "00:00", "Basal must start at 00:00")
    .refine(validateChronological, "Basal segments must be chronological"),
  icr: z.array(icrSegmentSchema)
    .refine(arr => arr.length === 0 || arr[0].startTime === "00:00", "ICR must start at 00:00")
    .refine(validateChronological, "ICR segments must be chronological"),
  isf: z.array(isfSegmentSchema)
    .refine(arr => arr.length === 0 || arr[0].startTime === "00:00", "ISF must start at 00:00")
    .refine(validateChronological, "ISF segments must be chronological")
}).refine((data) => {
  if (!data.basal || data.basal.length === 0) return true;
  let totalDailyBasal = 0;
  for (let i = 0; i < data.basal.length; i++) {
    const current = data.basal[i];
    let nextTimeStr = "24:00";
    if (i + 1 < data.basal.length) {
       nextTimeStr = data.basal[i + 1].startTime;
    }
    
    const [currH, currM] = current.startTime.split(':').map(Number);
    const [nextH, nextM] = nextTimeStr.split(':').map(Number);
    
    const currMinutes = currH * 60 + currM;
    const nextMinutes = nextH === 24 ? 24 * 60 : nextH * 60 + nextM;
    
    const durationHours = (nextMinutes - currMinutes) / 60.0;
    totalDailyBasal += current.value * durationHours;
  }
  return totalDailyBasal <= 150.0;
}, {
  message: "Total Daily Basal exceeds safe clinical limit of 150.0 U/day",
  path: ["basal"]
});

type ProfileFormValues = z.infer<typeof profileSchema>;

interface ProfileEditorProps {
  userId: string;
  initialProfile?: Profile;
  onProfileSaved?: () => void;
}

const generateNextName = (currentName: string) => {
  const match = currentName.match(/(.*)-(\d+)$/);
  if (match) {
    const base = match[1];
    const num = parseInt(match[2], 10) + 1;
    return `${base}-${num}`;
  }
  return `${currentName}-1`;
};

export const ProfileEditor: React.FC<ProfileEditorProps> = ({ userId, initialProfile, onProfileSaved }) => {
  const { register, control, handleSubmit, setValue, getValues, formState: { errors } } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: initialProfile ? {
      name: generateNextName(initialProfile.name),
      insulinType: initialProfile.insulinType || 'Humalog',
      durationOfAction: initialProfile.durationOfAction || 300,
      basal: initialProfile.basal?.length ? initialProfile.basal : [{ startTime: '00:00', value: 0.5 }],
      icr: initialProfile.icr || [],
      isf: initialProfile.isf || [],
    } : {
      name: '',
      insulinType: 'Humalog',
      durationOfAction: 300,
      basal: [{ startTime: '00:00', value: 0.5 }],
      icr: [],
      isf: [],
    },
  });

  const { is24Hour } = useTimeFormat();

  const { data: insulins = [] } = useQuery({
    queryKey: ['insulins'],
    queryFn: () => api.getInsulins().then(res => res.data)
  });

  const { data: allProfiles = [] } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => api.listProfiles(userId).then(res => res.data),
    enabled: !!initialProfile
  });

  
  const { fields: basalFields, append: appendBasal, remove: removeBasal } = useFieldArray({
    control,
    name: "basal"
  });

  const { fields: icrFields, append: appendIcr, remove: removeIcr } = useFieldArray({
    control,
    name: "icr"
  });

  const { fields: isfFields, append: appendIsf, remove: removeIsf } = useFieldArray({
    control,
    name: "isf"
  });

  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf'>('basal');
  const [apiError, setApiError] = useState<string | null>(null);
  const [isAddingNewInsulin, setIsAddingNewInsulin] = useState(false);

  const onSubmit = async (data: ProfileFormValues) => {
    setApiError(null);
    try {
      if (data.insulinType && !insulins.find(i => i.name === data.insulinType)) {
        await api.createInsulin({ name: data.insulinType });
      }

      // Map form data to API request
      // Note: The generated CreateProfileRequest interface might be strict.
      // We are casting for MVP simplicity, connecting real fields.
      const request: CreateProfileRequest = {
        name: data.name,
        insulinType: data.insulinType,
        durationOfAction: data.durationOfAction,
        basal: data.basal,
        icr: data.icr,
        isf: data.isf,
        targets: []
      };
      
      if (initialProfile?.id) {
        await api.updateProfile(userId, initialProfile.id, { ...initialProfile, ...request } as Profile);
      } else {
        await api.createProfile(userId, request);
      }
      onProfileSaved?.();
    } catch (err: any) {
      console.error(err);
      let errorMessage = "Failed to save profile";
      if (err.response?.data) {
        if (typeof err.response.data === 'string') {
          errorMessage = err.response.data;
        } else if (err.response.data.message) {
          errorMessage = err.response.data.message;
        } else if (err.response.data.detail) {
          errorMessage = err.response.data.detail;
        } else if (typeof err.response.data === 'object') {
          // Sometimes errors are wrapped or have different keys, stringify as a fallback
          try {
            errorMessage = JSON.stringify(err.response.data);
          } catch (e) {
             errorMessage = err.message;
          }
        }
      } else if (err.message) {
        errorMessage = err.message;
      }
      setApiError(errorMessage);
    }
  };

  return (
    <div className="profile-editor">
      <h3>{initialProfile ? 'Edit Profile' : 'Create Profile'}</h3>
      
      {initialProfile && (
        <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '1rem', padding: '0.75rem', background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '6px' }}>
          <div style={{ marginBottom: '4px' }}>
            <strong>Activation Date:</strong> {initialProfile.createdAt ? new Date(initialProfile.createdAt).toLocaleString(navigator.language, { dateStyle: 'short', timeStyle: 'short', hour12: !is24Hour }) : 'N/A'}
          </div>
          {initialProfile.status === 'ARCHIVED' && (
            <div>
              <strong>Deactivation Date:</strong> {(() => {
                const nextP = allProfiles.find(p => p.previousProfileId === initialProfile.id && (p.status === 'ACTIVE' || p.status === 'ARCHIVED'));
                return nextP?.createdAt ? new Date(nextP.createdAt).toLocaleString(navigator.language, { dateStyle: 'short', timeStyle: 'short', hour12: !is24Hour }) : 'N/A';
              })()}
            </div>
          )}
          {initialProfile.status === 'ACTIVE' && <div style={{ color: 'var(--accent-success)', marginTop: '4px' }}>Currently Active Configuration</div>}
        </div>
      )}

      {apiError && <div className="error">{apiError}</div>}
      <form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="name">Name</label>
          <input id="name" {...register("name")} />
          {errors.name && <span>{errors.name.message}</span>}
        </div>

        <div>
           <label htmlFor="insulinType">Insulin Type</label>
           {!isAddingNewInsulin ? (
             <div style={{ display: 'flex', gap: '8px' }}>
               <select 
                 id="insulinType" 
                 {...(() => {
                   const { onChange, ...rest } = register("insulinType");
                   return {
                     ...rest,
                     onChange: (e: React.ChangeEvent<HTMLSelectElement>) => {
                       if (e.target.value === '___ADD_NEW___') {
                         setIsAddingNewInsulin(true);
                         setValue('insulinType', '');
                       } else {
                         onChange(e);
                       }
                     }
                   };
                 })()}
                 style={{ 
                   padding: '8px', 
                   maxWidth: '300px', 
                   flex: '0 1 auto', 
                   borderRadius: '4px', 
                   border: '1px solid var(--border-color)',
                   backgroundColor: 'var(--surface-color)',
                   color: 'var(--text-primary)'
                 }}
               >
                 <option value="">-- Select Insulin --</option>
                 {insulins.map((insulin) => (
                   <option key={insulin.id} value={insulin.name}>{insulin.name}</option>
                 ))}
                 <option value="___ADD_NEW___">+ Add New...</option>
               </select>
             </div>
           ) : (
             <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
               <input 
                 id="insulinType" 
                 {...register("insulinType")} 
                 placeholder="Enter new insulin name" 
                 autoFocus 
                 style={{ flex: 1 }}
               />
               <button 
                 type="button" 
                 onClick={() => { 
                   setIsAddingNewInsulin(false); 
                   setValue('insulinType', insulins[0]?.name || ''); 
                 }}
                 style={{ padding: '2px 8px', fontSize: '0.8rem' }}
               >
                 Cancel
               </button>
             </div>
           )}
           {errors.insulinType && <span>{errors.insulinType.message}</span>}
        </div>

        <div>
           <label htmlFor="durationOfAction">Duration of Action (min)</label>
           <input id="durationOfAction" type="number" {...register("durationOfAction", { valueAsNumber: true })} />
           {errors.durationOfAction && <span>{errors.durationOfAction.message}</span>}
        </div>

        <div className="tabs">
          <button 
            type="button" 
            className={`tab-button ${activeTab === 'basal' ? 'active' : ''}`} 
            onClick={() => setActiveTab('basal')}
          >
            Basal
          </button>
          <button 
            type="button" 
            className={`tab-button ${activeTab === 'icr' ? 'active' : ''}`} 
            onClick={() => setActiveTab('icr')}
          >
            ICR
          </button>
          <button 
            type="button" 
            className={`tab-button ${activeTab === 'isf' ? 'active' : ''}`} 
            onClick={() => setActiveTab('isf')}
          >
            ISF
          </button>
        </div>

        {activeTab === 'basal' && (
          <div className="tab-content">
            <h4>Basal Schedule</h4>
            {basalFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`basal.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="0.05"
                    {...register(`basal.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Rate (U/hr)"
                    aria-label={`Value ${index}`}
                />
                <button type="button" onClick={() => removeBasal(index)}>Remove</button>
                {errors.basal?.[index]?.startTime && <span>Start Time Error</span>}
                {errors.basal?.[index]?.value && <span>Value Error</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendBasal(getNextSegment(getValues('basal') || basalFields, 0.5))}>Add Segment</button>
            {errors.basal && <div className="error-text">{errors.basal.message}</div>}
          </div>
        )}

        {activeTab === 'icr' && (
          <div className="tab-content">
            <h4>Insulin to Carb Ratio (ICR)</h4>
            {icrFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`icr.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="0.1"
                    {...register(`icr.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Ratio (g/U)"
                    aria-label={`ICR Value ${index}`}
                />
                <button type="button" onClick={() => removeIcr(index)}>Remove</button>
                {errors.icr?.[index]?.startTime && <span>Start Time Error</span>}
                {errors.icr?.[index]?.value && <span>{errors.icr[index]?.value?.message || "Value Error"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendIcr(getNextSegment(getValues('icr') || icrFields, 10.0))}>Add ICR Segment</button>
            {errors.icr && <div className="error-text">{errors.icr.message}</div>}
          </div>
        )}

        {activeTab === 'isf' && (
          <div className="tab-content">
            <h4>Insulin Sensitivity Factor (ISF)</h4>
            {isfFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`isf.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="1"
                    {...register(`isf.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Factor (mg/dL)"
                    aria-label={`ISF Value ${index}`}
                />
                <button type="button" onClick={() => removeIsf(index)}>Remove</button>
                {errors.isf?.[index]?.startTime && <span>Start Time Error</span>}
                {errors.isf?.[index]?.value && <span>{errors.isf[index]?.value?.message || "Value Error"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendIsf(getNextSegment(getValues('isf') || isfFields, 50.0))}>Add ISF Segment</button>
            {errors.isf && <div className="error-text">{errors.isf.message}</div>}
          </div>
        )}

        <div style={{ marginTop: '20px' }}>
            <button type="submit">Save Profile</button>
        </div>
      </form>
    </div>
  );
};
