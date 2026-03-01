import React, { createContext, useContext, useState, ReactNode } from 'react';

interface TimeFormatContextProps {
  is24Hour: boolean;
  setIs24Hour: (val: boolean) => void;
  formatTime: (timeStr: string) => string;
}

const TimeFormatContext = createContext<TimeFormatContextProps | undefined>(undefined);

export const TimeFormatProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [is24Hour, setIs24Hour] = useState(true); // Default to 24h

  const formatTime = (timeStr: string): string => {
    if (!timeStr || !timeStr.includes(':')) return timeStr;
    if (is24Hour) return timeStr;

    const [h, m] = timeStr.split(':');
    let hour = parseInt(h, 10);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12;
    hour = hour ? hour : 12; // 0 becomes 12
    return `${hour}:${m} ${ampm}`;
  };

  return (
    <TimeFormatContext.Provider value={{ is24Hour, setIs24Hour, formatTime }}>
      {children}
    </TimeFormatContext.Provider>
  );
};

export const useTimeFormat = () => {
  const context = useContext(TimeFormatContext);
  if (!context) {
    throw new Error('useTimeFormat must be used within a TimeFormatProvider');
  }
  return context;
};
