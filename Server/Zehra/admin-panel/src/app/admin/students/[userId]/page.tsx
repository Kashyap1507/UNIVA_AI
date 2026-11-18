'use client';

import React from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ArrowLeft } from 'lucide-react';
import adminUniversityApi, { StudentDetail, usd, shortDate, taskLabel } from '@/lib/api/admin-university';
import { Page, Loading, ErrorNotice, Empty, useLoader } from '@/components/admin/university/Common';

const COUNT_LABELS: [keyof StudentDetail['counts'], string][] = [
  ['materials', 'Course files'],
  ['flashcards', 'Flashcards'],
  ['flashcard_reviews', 'Card reviews'],
  ['practice_attempts', 'Practice attempts'],
  ['tutor_sessions', 'Tutor chats'],
  ['work_checks', 'Work checks'],
  ['deadlines', 'Deadlines'],
  ['study_groups', 'Study groups'],
  ['research_projects', 'Research projects'],
  ['papers', 'Papers'],
];

export default function StudentDetailPage() {
  const params = useParams<{ userId: string }>();
  const userId = params?.userId as string;
  const { data: s, error, loading, reload } = useLoader(() => adminUniversityApi.student(userId), [userId]);

  return (
    <Page
      title={s?.name || 'Student'}
      subtitle={s ? [s.email, s.auth_provider && `signed in with ${s.auth_provider}`].filter(Boolean).join(' · ') : undefined}
      actions={
        <Link href="/admin/students">
          <Button variant="outline" className="bg-white">
            <ArrowLeft className="h-4 w-4 mr-2" />
            All students
          </Button>
        </Link>
      }
    >
      {error && <ErrorNotice message={error} onRetry={reload} />}
      {loading && !s ? (
        <Loading />
      ) : s ? (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="bg-white border-0 shadow-lg">
              <CardHeader>
                <CardTitle className="text-lg">Profile</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <Row label="University" value={s.university} />
                <Row label="Degree" value={s.degree_level === 'MASTER' ? "Master's" : s.degree_level === 'BACHELOR' ? "Bachelor's" : null} />
                <Row label="Program" value={s.program} />
                <Row label="Semester" value={s.current_semester ? String(s.current_semester) : null} />
                <Row label="Joined" value={shortDate(s.created_at)} />
                <Row label="Last studied" value={shortDate(s.last_active)} />
                <Row label="Days studied" value={String(s.study_days_total)} />
                <Row label="Status" value={s.is_active ? 'Active' : 'Inactive'} />
              </CardContent>
            </Card>
            <Card className="bg-white border-0 shadow-lg lg:col-span-2">
              <CardHeader>
                <CardTitle className="text-lg">Activity</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
                  {COUNT_LABELS.map(([key, label]) => (
                    <div key={key} className="rounded-xl border border-gray-100 p-3">
                      <div className="text-xs text-gray-500">{label}</div>
                      <div className="text-lg font-semibold text-gray-900">{s.counts[key].toLocaleString()}</div>
                    </div>
                  ))}
                </div>
                {s.counts.materials_failed > 0 && (
                  <p className="text-sm text-red-600 mt-3">{s.counts.materials_failed} course file(s) failed to process.</p>
                )}
                <p className="text-xs text-gray-500 mt-4">Admins see counts only. Course files, notes, tutor chats and submissions stay private to the student.</p>
              </CardContent>
            </Card>
          </div>

          <Card className="bg-white border-0 shadow-lg overflow-hidden">
            <CardHeader>
              <CardTitle className="text-lg">Courses</CardTitle>
            </CardHeader>
            {s.courses.length === 0 ? (
              <Empty>No courses yet.</Empty>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Course</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Outline</TableHead>
                      <TableHead className="text-right">Files</TableHead>
                      <TableHead>Created</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {s.courses.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          <div className="font-medium text-gray-900">{c.name}</div>
                          {c.code && <div className="text-xs text-gray-500">{c.code}</div>}
                        </TableCell>
                        <TableCell>
                          <Badge variant={c.status === 'ARCHIVED' ? 'outline' : 'secondary'}>{c.status.toLowerCase()}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={c.structure_status === 'FAILED' ? 'destructive' : 'outline'}>{c.structure_status.toLowerCase()}</Badge>
                        </TableCell>
                        <TableCell className="text-right">{c.material_count}</TableCell>
                        <TableCell>{shortDate(c.created_at)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </Card>

          <Card className="bg-white border-0 shadow-lg overflow-hidden">
            <CardHeader>
              <CardTitle className="text-lg">AI usage, last 30 days</CardTitle>
              <p className="text-sm text-gray-600">
                {usd(s.ai_usage_30d.cost_usd)} · {s.ai_usage_30d.calls.toLocaleString()} runs · {(s.ai_usage_30d.tokens_in + s.ai_usage_30d.tokens_out).toLocaleString()} tokens
              </p>
            </CardHeader>
            {s.ai_usage_30d.by_task.length === 0 ? (
              <Empty>No AI usage in the last 30 days.</Empty>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Feature</TableHead>
                      <TableHead className="text-right">Runs</TableHead>
                      <TableHead className="text-right">Tokens in</TableHead>
                      <TableHead className="text-right">Tokens out</TableHead>
                      <TableHead className="text-right">Cost</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {s.ai_usage_30d.by_task.map((t) => (
                      <TableRow key={t.task}>
                        <TableCell>{taskLabel(t.task)}</TableCell>
                        <TableCell className="text-right">{t.calls.toLocaleString()}</TableCell>
                        <TableCell className="text-right">{t.tokens_in.toLocaleString()}</TableCell>
                        <TableCell className="text-right">{t.tokens_out.toLocaleString()}</TableCell>
                        <TableCell className="text-right font-mono text-sm">{usd(t.cost_usd)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </Card>
        </>
      ) : null}
    </Page>
  );
}

function Row({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-gray-500">{label}</span>
      <span className="text-gray-900 text-right">{value || <span className="text-gray-400">Not set</span>}</span>
    </div>
  );
}
