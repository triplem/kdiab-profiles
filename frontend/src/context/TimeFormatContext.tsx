import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface TimeFormatContextProps {
  is24Hour: boolean;
  formatTime: (timeStr: string) => string;
}

const TimeFormatContext = createContext<TimeFormatContextProps | undefined>(undefined);

export const TimeFormatProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [is24Hour, setIs24Hour] = useState(true); // Default to 24h

  useEffect(() => {
    const updateFormat = () => {
      try {
        const locale = document.documentElement.lang || navigator.language || 'en';
        const opts = new Intl.DateTimeFormat(locale, { hour: 'numeric' }).resolvedOptions();
        setIs24Hour(opts.hourCycle !== 'h11' && opts.hourCycle !== 'h12');
      } catch (e) {
        setIs24Hour(true);
      }
    };

    updateFormat(); // Initial setup

    // Watch for lang changes on <html> (which App.tsx dynamically updates based on Keycloak profile)
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'attributes' && mutation.attributeName === 'lang') {
          updateFormat();
        }
      });
    });

    observer.observe(document.documentElement, { attributes: true });

    return () => observer.disconnect();
  }, []);

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
    <TimeFormatContext.Provider value={{ is24Hour, formatTime }}>
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
