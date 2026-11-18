'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';
import { DollarSign, Zap, Users, Hash } from 'lucide-react';
import adminUniversityApi, { RangeDays, usd, shortDate, taskLabel } from '@/lib/api/admin-university';
import { Page, RangeSelect, RefreshButton, StatCard, Loading, ErrorNotice, Empty, useLoader } from '@/components/admin/university/Common';

export default function AICostsPage() {
  const [days, setDays] = useState<RangeDays>(30);
  const { data, error, loading, reload } = useLoader(() => adminUniversityApi.aiCosts(days), [days]);
  const chartData = (data?.daily || []).map((d) => ({ ...d, label: shortDate(d.date).replace(/,? \d{4}$/, '') }));
  const taskTotal = (data?.by_task || []).reduce((sum, t) => sum + t.cost_usd, 0);

  return (
    <Page
      title="AI Costs"
      subtitle="Gemini spend from real token counts, per day, feature and student"
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
            <StatCard label={`Spend, ${days} days`} value={usd(data.totals.cost_usd)} icon={DollarSign} color="bg-amber-100 text-amber-700" />
            <StatCard label="AI runs" value={data.totals.calls.toLocaleString()} icon={Zap} />
            <StatCard
              label="Students using AI"
              value={data.totals.students.toLocaleString()}
              hint={`${usd(data.totals.cost_per_student)} per student`}
              icon={Users}
              color="bg-green-100 text-green-700"
            />
            <StatCard
              label="Tokens"
              value={(data.totals.tokens_in + data.totals.tokens_out).toLocaleString()}
              hint={`${data.totals.tokens_in.toLocaleString()} in · ${data.totals.tokens_out.toLocaleString()} out`}
              icon={Hash}
              color="bg-purple-100 text-purple-700"
            />
          </div>

          <Card className="bg-white border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="text-lg">Spend per day</CardTitle>
            </CardHeader>
            <CardContent className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" />
                  <XAxis dataKey="label" fontSize={12} interval="preserveStartEnd" />
                  <YAxis fontSize={12} width={56} tickFormatter={(v) => `$${v}`} />
                  <Tooltip formatter={(v: number, name: string) => (name === 'Spend' ? usd(v) : v.toLocaleString())} />
                  <Bar dataKey="cost_usd" name="Spend" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card className="bg-white border-0 shadow-lg overflow-hidden">
              <CardHeader>
                <CardTitle className="text-lg">By feature</CardTitle>
              </CardHeader>
              {data.by_task.length === 0 ? (
                <Empty>No AI usage in this period.</Empty>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Feature</TableHead>
                        <TableHead className="text-right">Runs</TableHead>
                        <TableHead className="text-right">Cost</TableHead>
                        <TableHead className="w-[30%]">Share</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.by_task.map((t) => {
                        const share = taskTotal ? (t.cost_usd / taskTotal) * 100 : 0;
                        return (
                          <TableRow key={t.task}>
                            <TableCell>{taskLabel(t.task)}</TableCell>
                            <TableCell className="text-right">{t.calls.toLocaleString()}</TableCell>
                            <TableCell className="text-right font-mono text-sm">{usd(t.cost_usd)}</TableCell>
                            <TableCell>
                              <div className="h-2 rounded-full bg-gray-100 overflow-hidden" title={`${share.toFixed(1)}%`}>
                                <div className="h-full bg-amber-400" style={{ width: `${share}%` }} />
                              </div>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </div>
              )}
            </Card>

            <Card className="bg-white border-0 shadow-lg overflow-hidden">
              <CardHeader>
                <CardTitle className="text-lg">Top students</CardTitle>
              </CardHeader>
              {data.top_students.length === 0 ? (
                <Empty>No AI usage in this period.</Empty>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Student</TableHead>
                        <TableHead className="text-right">Runs</TableHead>
                        <TableHead className="text-right">Cost</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.top_students.map((s) => (
                        <TableRow key={s.user_id}>
                          <TableCell>
                            <Link href={`/admin/students/${s.user_id}`} className="hover:underline">
                              <div className="font-medium text-gray-900">{s.name || s.email || s.user_id}</div>
                              {s.university && <div className="text-xs text-gray-500">{s.university}</div>}
                            </Link>
                          </TableCell>
                          <TableCell className="text-right">{s.calls.toLocaleString()}</TableCell>
                          <TableCell className="text-right font-mono text-sm">{usd(s.cost_usd)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </Card>
          </div>
          <p className="text-xs text-gray-500">
            Costs use Gemini list prices per 1M tokens (2.5 Flash $0.30 in / $2.50 out, 2.5 Pro $1.25 / $10). Embeddings are estimated from text length. Runs before this page
            existed are not included.
          </p>
        </>
      ) : null}
    </Page>
  );
}
