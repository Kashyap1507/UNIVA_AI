'use client';

import React, { useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Building2, Search } from 'lucide-react';
import adminUniversityApi, { CatalogUniversity } from '@/lib/api/admin-university';
import { Page, RefreshButton, Loading, ErrorNotice, Empty, useLoader } from '@/components/admin/university/Common';

export default function CatalogPage() {
  const { data, error, loading, reload } = useLoader(() => adminUniversityApi.catalog(), []);
  const [selected, setSelected] = useState<string | null>(null);
  const [filter, setFilter] = useState('');

  const universities = data?.universities || [];
  const current: CatalogUniversity | undefined =
    universities.find((u) => (u.name ?? '') === selected) ?? universities[0];

  const courses = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    const all = current?.courses || [];
    return needle ? all.filter((c) => `${c.code ?? ''} ${c.name ?? ''}`.toLowerCase().includes(needle)) : all;
  }, [current, filter]);

  return (
    <Page
      title="Catalog"
      subtitle="Universities, programs and course codes, built from what students enter"
      actions={<RefreshButton onClick={reload} busy={loading} />}
    >
      {error && <ErrorNotice message={error} onRetry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : universities.length === 0 ? (
        <Card className="bg-white border-0 shadow-lg">
          <Empty>No students yet.</Empty>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Card className="bg-white border-0 shadow-lg h-fit">
            <CardHeader>
              <CardTitle className="text-lg">Universities</CardTitle>
            </CardHeader>
            <CardContent className="p-2">
              <ul className="space-y-1">
                {universities.map((u) => {
                  const active = u === current;
                  return (
                    <li key={u.name ?? '__none'}>
                      <button
                        type="button"
                        onClick={() => {
                          setSelected(u.name ?? '');
                          setFilter('');
                        }}
                        className={`w-full text-left rounded-lg px-3 py-2 flex items-center justify-between gap-3 transition-colors ${
                          active ? 'bg-blue-50 text-blue-800' : 'hover:bg-gray-50 text-gray-800'
                        }`}
                      >
                        <span className="flex items-center gap-2 min-w-0">
                          <Building2 className="h-4 w-4 shrink-0 text-gray-400" />
                          <span className={`truncate ${u.name ? '' : 'italic text-gray-500'}`}>{u.name ?? 'No university set'}</span>
                        </span>
                        <Badge variant="secondary">{u.students}</Badge>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </CardContent>
          </Card>

          {current && (
            <div className="lg:col-span-2 space-y-6">
              <Card className="bg-white border-0 shadow-lg">
                <CardHeader>
                  <CardTitle className="text-lg">{current.name ?? 'No university set'}</CardTitle>
                  <p className="text-sm text-gray-600">
                    {current.students} student{current.students === 1 ? '' : 's'} · {current.bachelor} Bachelor&apos;s · {current.master} Master&apos;s ·{' '}
                    {current.course_count} course{current.course_count === 1 ? '' : 's'}
                  </p>
                </CardHeader>
                <CardContent>
                  <h3 className="text-sm font-semibold text-gray-700 mb-2">Programs</h3>
                  {current.programs.length === 0 ? (
                    <p className="text-sm text-gray-500">No programs entered.</p>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      {current.programs.map((p) => (
                        <Badge key={p.name} variant="outline" className="py-1">
                          {p.name} <span className="ml-1 text-gray-500">{p.students}</span>
                        </Badge>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card className="bg-white border-0 shadow-lg overflow-hidden">
                <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between space-y-0">
                  <CardTitle className="text-lg">Courses</CardTitle>
                  <div className="relative sm:w-64">
                    <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter by code or name" className="pl-9" />
                  </div>
                </CardHeader>
                {courses.length === 0 ? (
                  <Empty>No courses.</Empty>
                ) : (
                  <div className="overflow-x-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Code</TableHead>
                          <TableHead>Name</TableHead>
                          <TableHead className="text-right">Students</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {courses.map((c, i) => (
                          <TableRow key={`${c.code}-${c.name}-${i}`}>
                            <TableCell className="font-mono text-sm">{c.code ?? <span className="text-gray-400">—</span>}</TableCell>
                            <TableCell>{c.name ?? <span className="text-gray-400">—</span>}</TableCell>
                            <TableCell className="text-right">{c.students}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
                {current.course_count > current.courses.length && (
                  <p className="text-xs text-gray-500 px-6 pb-4">Showing the {current.courses.length} most common of {current.course_count} courses.</p>
                )}
              </Card>
            </div>
          )}
        </div>
      )}
    </Page>
  );
}
