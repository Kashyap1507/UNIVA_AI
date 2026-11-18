'use client';

import React, { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { EyeOff, FileText, MessageSquare, User, Users } from 'lucide-react';
import { toast } from 'sonner';
import adminUniversityApi, { ModerationAction, ModerationItem, shortDate } from '@/lib/api/admin-university';
import { errorMessage } from '@/lib/api/client';
import { Page, RefreshButton, Loading, ErrorNotice, Empty, useLoader } from '@/components/admin/university/Common';

const REASON_LABELS: Record<string, string> = {
  SPAM: 'Spam',
  INAPPROPRIATE: 'Inappropriate',
  COPYRIGHT: 'Copyright',
  ACADEMIC_INTEGRITY: 'Academic integrity',
  OTHER: 'Other',
};

const TARGET_LABELS = { NOTE: 'Group post', MEMBER: 'Group member', GROUP: 'Whole group' } as const;

const REMOVE_TEXT = {
  NOTE: { button: 'Remove post', done: 'Post removed', confirm: 'The post is removed for everyone in the group.' },
  MEMBER: { button: 'Remove member', done: 'Member removed', confirm: 'The student is removed from this group.' },
  GROUP: { button: 'Close group', done: 'Group closed', confirm: 'The whole group closes for all members.' },
} as const;

export default function ModerationPage() {
  const [status, setStatus] = useState<'OPEN' | 'RESOLVED'>('OPEN');
  const { data, error, loading, reload } = useLoader(() => adminUniversityApi.moderation(status), [status]);
  const [pending, setPending] = useState<{ item: ModerationItem; action: ModerationAction } | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    if (!pending) return;
    setBusy(true);
    try {
      const res = await adminUniversityApi.resolve(pending.item.id, pending.action, note.trim());
      const done = pending.action === 'REMOVE' ? REMOVE_TEXT[pending.item.target_type].done : 'Dismissed';
      toast.success(`${done} · ${res.resolved_reports} report${res.resolved_reports === 1 ? '' : 's'} resolved`);
      setPending(null);
      setNote('');
      reload();
    } catch (e) {
      toast.error(errorMessage(e, 'Could not resolve this report'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page
      title="Moderation"
      subtitle="Reports from study groups. Posts with three reports are hidden until you review them."
      actions={
        <>
          <Tabs value={status} onValueChange={(v) => setStatus(v as 'OPEN' | 'RESOLVED')}>
            <TabsList>
              <TabsTrigger value="OPEN">Open</TabsTrigger>
              <TabsTrigger value="RESOLVED">Resolved</TabsTrigger>
            </TabsList>
          </Tabs>
          <RefreshButton onClick={reload} busy={loading} />
        </>
      }
    >
      {error && <ErrorNotice message={error} onRetry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : data && data.items.length === 0 ? (
        <Card className="bg-white border-0 shadow-lg">
          <Empty>{status === 'OPEN' ? 'Nothing to review. 🎉' : 'No resolved reports yet.'}</Empty>
        </Card>
      ) : (
        <div className="space-y-4">
          {data?.items.map((item) => (
            <ReportCard
              key={item.id}
              item={item}
              onAction={status === 'OPEN' ? (action) => setPending({ item, action }) : undefined}
            />
          ))}
        </div>
      )}

      <AlertDialog open={!!pending} onOpenChange={(open) => !open && !busy && setPending(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {pending?.action === 'REMOVE' ? `${REMOVE_TEXT[pending.item.target_type].button}?` : 'Dismiss these reports?'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {pending?.action === 'REMOVE'
                ? REMOVE_TEXT[pending.item.target_type].confirm
                : 'Nothing is removed. A hidden post becomes visible again.'}{' '}
              All {pending?.item.report_count} open report{pending?.item.report_count === 1 ? '' : 's'} on it will be marked resolved.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <Textarea value={note} onChange={(e) => setNote(e.target.value.slice(0, 500))} placeholder="Note for other admins (optional)" rows={3} />
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={busy}
              onClick={(e) => {
                e.preventDefault();
                confirm();
              }}
              className={pending?.action === 'REMOVE' ? 'bg-red-600 hover:bg-red-700' : ''}
            >
              {busy ? 'Saving…' : pending?.action === 'REMOVE' ? REMOVE_TEXT[pending.item.target_type].button : 'Dismiss'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Page>
  );
}

function ReportCard({ item, onAction }: { item: ModerationItem; onAction?: (action: ModerationAction) => void }) {
  const t = item.target;
  const Icon = item.target_type === 'NOTE' ? (t.kind === 'MATERIAL' ? FileText : MessageSquare) : item.target_type === 'MEMBER' ? User : Users;
  return (
    <Card className="bg-white border-0 shadow-lg">
      <CardContent className="p-5 space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-3 min-w-0">
            <div className="p-2 rounded-xl bg-rose-100 text-rose-700 shrink-0">
              <Icon className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-gray-900">{TARGET_LABELS[item.target_type]}</span>
                <span className="text-gray-400">in</span>
                <span className="text-gray-800 truncate">{t.group_name || 'Unknown group'}</span>
                {t.hidden && !t.removed && (
                  <Badge variant="outline" className="border-amber-300 text-amber-700">
                    <EyeOff className="h-3 w-3 mr-1" />
                    Hidden
                  </Badge>
                )}
                {t.removed && <Badge variant="destructive">Removed</Badge>}
                {t.group_deleted && item.target_type !== 'GROUP' && <Badge variant="outline">Group closed</Badge>}
              </div>
              <p className="text-xs text-gray-500 mt-1">
                {item.report_count} report{item.report_count === 1 ? '' : 's'} · first {shortDate(item.first_reported_at)} · latest {shortDate(item.last_reported_at)}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-1">
            {Object.entries(item.reasons).map(([reason, n]) => (
              <Badge key={reason} variant={reason === 'ACADEMIC_INTEGRITY' ? 'destructive' : 'secondary'}>
                {REASON_LABELS[reason] || reason}
                {n > 1 ? ` ×${n}` : ''}
              </Badge>
            ))}
          </div>
        </div>

        <div className="rounded-xl bg-slate-50 border border-slate-100 p-4 text-sm">
          {item.target_type === 'NOTE' && (
            <>
              <p className="text-xs text-gray-500 mb-1">
                {t.author_name || 'Unknown'} · {shortDate(t.posted_at)}
                {t.kind === 'MATERIAL' && ' · shared a file'}
              </p>
              {t.title && <p className="font-medium text-gray-900">{t.title}</p>}
              {t.body ? <p className="text-gray-800 whitespace-pre-wrap break-words">{t.body}</p> : !t.title && <p className="text-gray-400 italic">No text</p>}
            </>
          )}
          {item.target_type === 'MEMBER' && (
            <p className="text-gray-800">
              {t.member_name || 'Unknown member'} <span className="text-gray-500">({(t.member_status || 'unknown').toLowerCase()})</span>
            </p>
          )}
          {item.target_type === 'GROUP' && <p className="text-gray-800 whitespace-pre-wrap">{t.description || <span className="text-gray-400 italic">No description</span>}</p>}
        </div>

        <details className="text-sm">
          <summary className="cursor-pointer text-gray-600 hover:text-gray-900">Reporter comments</summary>
          <ul className="mt-2 space-y-2">
            {item.details.map((d, i) => (
              <li key={i} className="flex flex-col sm:flex-row sm:gap-2">
                <span className="text-gray-500 shrink-0">
                  {d.reporter || 'Student'} · {REASON_LABELS[d.reason] || d.reason}:
                </span>
                <span className="text-gray-800">{d.details || <span className="text-gray-400 italic">no comment</span>}</span>
              </li>
            ))}
          </ul>
        </details>

        {item.resolution && (
          <p className="text-sm text-gray-600">
            <Badge variant={item.resolution.action === 'REMOVE' ? 'destructive' : 'secondary'} className="mr-2">
              {item.resolution.action === 'REMOVE' ? 'Removed' : 'Dismissed'}
            </Badge>
            {shortDate(item.resolution.at)}
            {item.resolution.note && <span className="ml-2">“{item.resolution.note}”</span>}
          </p>
        )}

        {onAction && (
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={() => onAction('DISMISS')}>
              Dismiss
            </Button>
            <Button variant="destructive" onClick={() => onAction('REMOVE')}>
              {REMOVE_TEXT[item.target_type].button}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
