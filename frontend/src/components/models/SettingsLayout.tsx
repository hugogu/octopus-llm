'use client';

import React from 'react';
import { Settings, KeyRound, Plus, BrainCircuit } from 'lucide-react';

interface SettingsLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
}

export default function SettingsLayout({ children, title, subtitle }: SettingsLayoutProps) {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        <div className="mb-8 sm:mb-10">
          <div className="flex items-center gap-3 mb-2">
            <Settings className="w-6 h-6 text-blue-600" />
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100">{title}</h1>
          </div>
          {subtitle && (
            <p className="text-gray-600 dark:text-gray-400 text-sm sm:text-base">{subtitle}</p>
          )}
        </div>
        
        <div className="space-y-6">
          {children}
        </div>
      </div>
    </div>
  );
}

export function SettingsSection({
  title,
  icon: Icon,
  action,
  children,
  emptyState,
}: {
  title: string;
  icon?: React.ElementType;
  action?: React.ReactNode;
  children: React.ReactNode;
  emptyState?: React.ReactNode;
}) {
  const hasContent = React.Children.count(children) > 0;
  
  return (
    <section className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm">
      <div className="px-5 sm:px-6 py-4 sm:py-5 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          {Icon && <Icon className="w-5 h-5 text-gray-500 dark:text-gray-400" />}
          <h2 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
        </div>
        {action && <div>{action}</div>}
      </div>
      <div className="px-5 sm:px-6 py-4 sm:py-5">
        {hasContent ? children : emptyState}
      </div>
    </section>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon?: React.ElementType;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="text-center py-8 sm:py-12">
      {Icon && (
        <div className="mx-auto w-12 h-12 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center mb-4">
          <Icon className="w-6 h-6 text-gray-400" />
        </div>
      )}
      <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-1">{title}</h3>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">{description}</p>
      {action && <div>{action}</div>}
    </div>
  );
}
