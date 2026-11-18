'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ResponsiveContainer, LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import {
  GraduationCap,
  UserPlus,
  Activity,
  BookOpen,
  FileText,
  Layers,
  ClipboardCheck,
  MessageSquare,
  ScanLine,
  Users,
  FlaskConical,
  ShieldAlert,
  DollarSign,
} from 'lucide-react';
import adminUniversityApi, { RangeDays, usd, shortDate } from '@/lib/api/admin-university';
import { Page, RangeSelect, RefreshButton, StatCard, Loading, ErrorNotice, useLoader } from '@/components/admin/university/Common';

export default function Dashboard() {
  const [days, setDays] = useState<RangeDays>(7);
  const { data, error, loading, reload } = useLoader(() => adminUniversityApi.overview(days), [days]);

  const chartData = (data?.daily || []).map((d) => ({ ...d, label: shortDate(d.date).replace(/,? \d{4}$/, '') }));

  return (
    <Page
      title="Dashboard"
      subtitle="Students, study activity and AI spend across Univa AI"
      actions={
        <>
          <RangeSelect value={days} onChange={setDays} />
          <RefreshButton onClick={reload} busy={loading} />
        </>
      }
    >
      {error && <ErrorNotice message={error} onRetry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : data ? (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              label="Students"
              value={data.students.total.toLocaleString()}
              hint={`${data.students.bachelor} Bachelor's · ${data.students.master} Master's${data.students.profile_incomplete ? ` · ${data.students.profile_incomplete} unset` : ''}`}
              icon={GraduationCap}
            />
            <StatCard label={`New in ${days} days`} value={data.students.new.toLocaleString()} icon={UserPlus} color="bg-green-100 text-green-700" />
            <StatCard
              label={`Active in ${days} days`}
              value={data.students.active.toLocaleString()}
              hint={data.students.total ? `${Math.round((data.students.active / data.students.total) * 100)}% of students studied` : undefined}
              icon={Activity}
              color="bg-purple-100 text-purple-700"
            />
            <StatCard
              label={`AI spend, ${days} days`}
              value={usd(data.ai.cost_usd)}
              hint={`${data.ai.calls.toLocaleString()} AI runs`}
              icon={DollarSign}
              color="bg-amber-100 text-amber-700"
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card className="bg-white border-0 shadow-lg">
              <CardHeader>
                <CardTitle className="text-lg">Study activity per day</CardTitle>
              </CardHeader>
              <CardContent className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" />
                    <XAxis dataKey="label" fontSize={12} interval="preserveStartEnd" />
                    <YAxis allowDecimals={false} fontSize={12} width={32} />
                    <Tooltip />
                    <Legend iconType="plainline" wrapperStyle={{ fontSize: 12 }} />
                    <Line type="monotone" dataKey="active_students" name="Active students" stroke="#2563eb" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="flashcard_reviews" name="Card reviews" stroke="#16a34a" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
            <Card className="bg-white border-0 shadow-lg">
              <CardHeader>
                <CardTitle className="text-lg">AI spend per day</CardTitle>
              </CardHeader>
              <CardContent className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" />
                    <XAxis dataKey="label" fontSize={12} interval="preserveStartEnd" />
                    <YAxis fontSize={12} width={48} tickFormatter={(v) => `$${v}`} />
                    <Tooltip formatter={(v: number) => usd(v)} />
                    <Bar dataKey="ai_cost_usd" name="AI spend" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="bg-white border-0 shadow-lg lg:col-span-2">
              <CardHeader>
                <CardTitle className="text-lg">Study activity</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  <Metric icon={BookOpen} label="Courses" value={data.content.courses} />
                  <Metric
                    icon={FileText}
                    label="Course files"
                    value={data.content.materials}
                    extra={data.content.materials_failed ? <Badge variant="destructive">{data.content.materials_failed} failed</Badge> : undefined}
                  />
                  <Metric icon={Layers} label={`Card reviews (${days}d)`} value={data.content.flashcard_reviews} />
                  <Metric icon={ClipboardCheck} label={`Practice attempts (${days}d)`} value={data.content.practice_attempts} />
                  <Metric icon={MessageSquare} label={`Tutor chats (${days}d)`} value={data.content.tutor_sessions} />
                  <Metric icon={ScanLine} label={`Work checks (${days}d)`} value={data.content.work_checks} />
                  <Metric icon={Users} label="Study groups" value={data.content.study_groups} />
                  <Metric icon={FlaskConical} label="Research papers" value={data.content.papers} />
                  <Link href="/admin/moderation" className="block">
                    <Metric
                      icon={ShieldAlert}
                      label="Open reports"
                      value={data.moderation.open_reports}
                      extra={data.moderation.open_reports ? <Badge variant="destructive">Review</Badge> : undefined}
                    />
                  </Link>
                </div>
              </CardContent>
            </Card>
            <Card className="bg-white border-0 shadow-lg">
              <CardHeader>
                <CardTitle className="text-lg">Top universities</CardTitle>
              </CardHeader>
              <CardContent>
                {data.top_universities.length === 0 ? (
                  <p className="text-sm text-gray-500">No students have set a university yet.</p>
                ) : (
                  <ul className="space-y-3">
                    {data.top_universities.map((u) => (
                      <li key={u.name} className="flex items-center justify-between gap-3">
                        <span className="text-sm text-gray-800 truncate">{u.name}</span>
                        <Badge variant="secondary">{u.students}</Badge>
                      </li>
                    ))}
                  </ul>
                )}
                <Link href="/admin/catalog" className="text-sm text-blue-600 hover:underline mt-4 inline-block">
                  Open catalog →
                </Link>
              </CardContent>
            </Card>
          </div>
        </>
      ) : null}
    </Page>
  );
}

function Metric({
  icon: Icon,
  label,
  value,
  extra,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  extra?: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-gray-100 p-4 hover:bg-gray-50 transition-colors">
      <div className="flex items-center gap-2 text-gray-600 text-sm">
        <Icon className="h-4 w-4" />
        <span className="truncate">{label}</span>
      </div>
      <div className="flex items-center gap-2 mt-2">
        <span className="text-xl font-semibold text-gray-900">{value.toLocaleString()}</span>
        {extra}
      </div>
    </div>
  );
}
