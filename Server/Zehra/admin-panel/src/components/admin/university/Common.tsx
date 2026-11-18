'use client';

import React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Loader2, RefreshCw } from 'lucide-react';
import type { RangeDays } from '@/lib/api/admin-university';

/** Page shell used by the university admin pages. */
export function Page({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 p-4 sm:p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">{title}</h1>
            {subtitle && <p className="text-gray-600 mt-1">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
        </div>
        {children}
      </div>
    </div>
  );
}

export function RangeSelect({ value, onChange }: { value: RangeDays; onChange: (days: RangeDays) => void }) {
  return (
    <Select value={String(value)} onValueChange={(v) => onChange(Number(v) as RangeDays)}>
      <SelectTrigger className="w-[150px] bg-white">
        <SelectValue placeholder="Time range" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="7">Last 7 days</SelectItem>
        <SelectItem value="30">Last 30 days</SelectItem>
        <SelectItem value="90">Last 90 days</SelectItem>
      </SelectContent>
    </Select>
  );
}

export function RefreshButton({ onClick, busy }: { onClick: () => void; busy?: boolean }) {
  return (
    <Button onClick={onClick} variant="outline" size="icon" className="bg-white" aria-label="Refresh" disabled={busy}>
      <RefreshCw className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`} />
    </Button>
  );
}

export function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  color = 'bg-blue-100 text-blue-700',
}: {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  icon?: React.ComponentType<{ className?: string }>;
  color?: string;
}) {
  return (
    <Card className="bg-white border-0 shadow-lg">
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm font-medium text-gray-600">{label}</p>
            <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
            {hint && <p className="text-xs text-gray-500 mt-1">{hint}</p>}
          </div>
          {Icon && (
            <div className={`p-3 rounded-2xl shrink-0 ${color}`}>
              <Icon className="h-5 w-5" />
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-24 text-gray-600">
      <Loader2 className="h-6 w-6 animate-spin text-blue-600" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorNotice({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Card className="border-red-200 bg-red-50">
      <CardContent className="p-4 flex items-center justify-between gap-4">
        <p className="text-red-700 text-sm">{message}</p>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry}>
            Try again
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-gray-500 py-8 text-center">{children}</p>;
}

/** Loads data for a page and re-runs when deps change. */
export function useLoader<T>(load: () => Promise<T>, deps: React.DependencyList) {
  const [data, setData] = React.useState<T | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const run = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await load());
    } catch (e: any) {
      setError(e?.response?.data?.error || e?.message || 'Could not load this page');
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  React.useEffect(() => {
    run();
  }, [run]);
  return { data, error, loading, reload: run };
}
