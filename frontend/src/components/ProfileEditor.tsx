import React, { useState } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api } from '../api/client';
import type { CreateProfileRequest } from '../api/generated';

// Define Validation Schema
const timeSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time format (HH:MM)"),
  value: z.number().min(0, "Value must be positive"),
});

const icrSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time format (HH:MM)"),
  value: z.number().min(1.0, "ICR must be >= 1.0 g/U").max(50.0, "ICR must be <= 50.0 g/U"),
});

const isfSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time format (HH:MM)"),
  value: z.number().min(10.0, "ISF must be >= 10.0 mg/dL").max(200.0, "ISF must be <= 200.0 mg/dL"),
});

const profileSchema = z.object({
  name: z.string().min(1, "Name is required"),
  insulinType: z.string().min(1, "Insulin type is required"),
  durationOfAction: z.number().min(1, "Duration must be positive (minutes)"),
  basal: z.array(timeSegmentSchema).nonempty("At least one basal segment required"),
  icr: z.array(icrSegmentSchema),
  isf: z.array(isfSegmentSchema)
}).refine((data) => {
  if (!data.basal || data.basal.length === 0) return true;
  let totalDailyBasal = 0;
  for (let i = 0; i < data.basal.length; i++) {
    const current = data.basal[i];
    // Need a chronological check so the calculation makes sense
    let nextTimeStr = "24:00";
    if (i + 1 < data.basal.length) {
       nextTimeStr = data.basal[i + 1].startTime;
    }
    
    const [currH, currM] = current.startTime.split(':').map(Number);
    const [nextH, nextM] = nextTimeStr.split(':').map(Number);
    
    const currMinutes = currH * 60 + currM;
    const nextMinutes = nextH === 24 ? 24 * 60 : nextH * 60 + nextM;
    
    const durationHours = (nextMinutes - currMinutes) / 60.0;
    if (durationHours <= 0) return false; // Must be in ascending chronological order
    
    totalDailyBasal += current.value * durationHours;
  }
  return totalDailyBasal <= 150.0;
}, {
  message: "Total Daily Basal exceeds safe clinical limit of 150.0 U/day or segments overlap",
  path: ["basal"]
});

type ProfileFormValues = z.infer<typeof profileSchema>;

interface ProfileEditorProps {
  userId: string;
  onProfileSaved?: () => void;
}

export const ProfileEditor: React.FC<ProfileEditorProps> = ({ userId, onProfileSaved }) => {
  const { register, control, handleSubmit, formState: { errors } } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      name: '',
      insulinType: 'Humalog',
      durationOfAction: 360,
      basal: [{ startTime: '00:00', value: 0.5 }],
      icr: [],
      isf: [],
    },
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

  const [apiError, setApiError] = useState<string | null>(null);

  const onSubmit = async (data: ProfileFormValues) => {
    setApiError(null);
    try {
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
      
      await api.createProfile(userId, request);
      onProfileSaved?.();
    } catch (err: any) {
      console.error(err);
      setApiError(err.message || "Failed to save profile");
    }
  };

  return (
    <div className="profile-editor">
      <h3>Create Profile</h3>
      {apiError && <div className="error">{apiError}</div>}
      <form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="name">Name</label>
          <input id="name" {...register("name")} />
          {errors.name && <span>{errors.name.message}</span>}
        </div>

        <div>
           <label htmlFor="insulinType">Insulin Type</label>
           <input id="insulinType" {...register("insulinType")} />
           {errors.insulinType && <span>{errors.insulinType.message}</span>}
        </div>

        <div>
           <label htmlFor="durationOfAction">Duration of Action (min)</label>
           <input id="durationOfAction" type="number" {...register("durationOfAction", { valueAsNumber: true })} />
           {errors.durationOfAction && <span>{errors.durationOfAction.message}</span>}
        </div>

        <h4>Basal Schedule</h4>
        {basalFields.map((field, index) => (
          <div key={field.id} className="segment-row">
            <input 
                type="time"
                {...register(`basal.${index}.startTime` as const)} 
                placeholder="00:00"
                aria-label={`Start Time ${index}`}
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
        <button type="button" onClick={() => appendBasal({ startTime: "", value: 0 })}>Add Segment</button>
        {errors.basal && <div className="error-text">{errors.basal.message}</div>}

        <h4>Insulin to Carb Ratio (ICR)</h4>
        {icrFields.map((field, index) => (
          <div key={field.id} className="segment-row">
            <input 
                type="time"
                {...register(`icr.${index}.startTime` as const)} 
                placeholder="00:00"
                aria-label={`ICR Start Time ${index}`}
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
        <button type="button" onClick={() => appendIcr({ startTime: "", value: 10.0 })}>Add ICR Segment</button>
        {errors.icr && <div className="error-text">{errors.icr.message}</div>}

        <h4>Insulin Sensitivity Factor (ISF)</h4>
        {isfFields.map((field, index) => (
          <div key={field.id} className="segment-row">
            <input 
                type="time"
                {...register(`isf.${index}.startTime` as const)} 
                placeholder="00:00"
                aria-label={`ISF Start Time ${index}`}
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
        <button type="button" onClick={() => appendIsf({ startTime: "", value: 50.0 })}>Add ISF Segment</button>
        {errors.isf && <div className="error-text">{errors.isf.message}</div>}

        <div style={{ marginTop: '20px' }}>
            <button type="submit">Save Profile</button>
        </div>
      </form>
    </div>
  );
};
