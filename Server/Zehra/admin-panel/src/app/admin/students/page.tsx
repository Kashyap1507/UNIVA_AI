'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Search } from 'lucide-react';
import adminUniversityApi, { DegreeLevel, usd, shortDate } from '@/lib/api/admin-university';
import { Page, RefreshButton, Loading, ErrorNotice, Empty, useLoader } from '@/components/admin/university/Common';

export default function StudentsPage() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [degree, setDegree] = useState<DegreeLevel | ''>('');

  // Search after typing stops
  useEffect(() => {
    const t = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  const { data, error, loading, reload } = useLoader(() => adminUniversityApi.students({ q: query, degree }), [query, degree]);

  return (
    <Page
      title="Students"
      subtitle={data ? `${data.total.toLocaleString()} student${data.total === 1 ? '' : 's'}` : 'Everyone using Univa AI'}
      actions={<RefreshButton onClick={reload} busy={loading} />}
    >
      <Card className="bg-white border-0 shadow-lg">
        <CardContent className="p-4 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search name, email, university or program"
              className="pl-9"
            />
          </div>
          <Select value={degree || 'ALL'} onValueChange={(v) => setDegree(v === 'ALL' ? '' : (v as DegreeLevel))}>
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder="Degree" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All degrees</SelectItem>
              <SelectItem value="BACHELOR">Bachelor&apos;s</SelectItem>
              <SelectItem value="MASTER">Master&apos;s</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      {error && <ErrorNotice message={error} onRetry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : data ? (
        <Card className="bg-white border-0 shadow-lg overflow-hidden">
          {data.students.length === 0 ? (
            <Empty>No students match.</Empty>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Student</TableHead>
                    <TableHead>University</TableHead>
                    <TableHead>Degree</TableHead>
                    <TableHead className="text-right">Courses</TableHead>
                    <TableHead>Last studied</TableHead>
                    <TableHead className="text-right">AI, 30 days</TableHead>
                    <TableHead>Joined</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.students.map((s) => (
                    <TableRow key={s.id} className="cursor-pointer hover:bg-slate-50">
                      <TableCell>
                        <Link href={`/admin/students/${s.id}`} className="block">
                          <div className="font-medium text-gray-900">{s.name || 'No name'}</div>
                          <div className="text-xs text-gray-500">{s.email}</div>
                        </Link>
                      </TableCell>
                      <TableCell className="max-w-[220px]">
                        <div className="truncate">{s.university || <span className="text-gray-400">Not set</span>}</div>
                        {s.program && <div className="text-xs text-gray-500 truncate">{s.program}</div>}
                      </TableCell>
                      <TableCell>
                        {s.degree_level ? (
                          <Badge variant={s.degree_level === 'MASTER' ? 'default' : 'secondary'}>{s.degree_level === 'MASTER' ? "Master's" : "Bachelor's"}</Badge>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                        {!s.is_active && <Badge variant="destructive" className="ml-2">Inactive</Badge>}
                      </TableCell>
                      <TableCell className="text-right">{s.course_count}</TableCell>
                      <TableCell>{shortDate(s.last_active)}</TableCell>
                      <TableCell className="text-right font-mono text-sm">{usd(s.ai_cost_30d)}</TableCell>
                      <TableCell>{shortDate(s.created_at)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </Card>
      ) : null}
    </Page>
  );
}
