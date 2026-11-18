'use client';

import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Brain,
  FileEdit,
  Calendar,
  Image as ImageIcon,
  ClipboardList,
  CheckCircle2,
  XCircle,
  Clock,
  DollarSign,
  Activity,
  Loader2,
  ChevronLeft,
  ChevronRight,
  RefreshCw
} from 'lucide-react';
import adminAIWorkflowsApi, { AIWorkflow, AIWorkflowLog, AIWorkflowLogDetail } from '@/lib/api/admin-ai-workflows';

// Task names come from the server's model router; unknown tasks fall back to a generic icon
const WORKFLOW_ICONS: Record<string, any> = {
  tutor_chat: Brain,
  work_feedback: FileEdit,
  work_feedback_extract: FileEdit,
  work_feedback_verify: FileEdit,
  flashcard_generation: ClipboardList,
  exam_generation: ClipboardList,
  exam_grading: ClipboardList,
  syllabus_extraction: Calendar,
  document_ingestion: ImageIcon,
};

const WORKFLOW_COLORS: Record<string, string> = {
  tutor_chat: 'bg-blue-100 text-blue-600',
  work_feedback: 'bg-purple-100 text-purple-600',
  work_feedback_extract: 'bg-purple-100 text-purple-600',
  work_feedback_verify: 'bg-purple-100 text-purple-600',
  flashcard_generation: 'bg-pink-100 text-pink-600',
  exam_generation: 'bg-pink-100 text-pink-600',
  exam_grading: 'bg-pink-100 text-pink-600',
  syllabus_extraction: 'bg-green-100 text-green-600',
  document_ingestion: 'bg-orange-100 text-orange-600',
};

export default function AIWorkflowsPage() {
  const [workflows, setWorkflows] = useState<AIWorkflow[]>([]);
  const [logs, setLogs] = useState<AIWorkflowLog[]>([]);
  const [selectedLog, setSelectedLog] = useState<AIWorkflowLogDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [workflowFilter, setWorkflowFilter] = useState<string>('all');
  const [successFilter, setSuccessFilter] = useState<string>('all');
  const [daysFilter, setDaysFilter] = useState<number>(7);

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [totalLogs, setTotalLogs] = useState(0);
  const logsPerPage = 20;

  // Dialog state
  const [isDetailDialogOpen, setIsDetailDialogOpen] = useState(false);

  // Fetch workflows
  const fetchWorkflows = async () => {
    setLoading(true);
    setError(null);

    const result = await adminAIWorkflowsApi.getWorkflows(daysFilter);

    if (result.workflows) {
      setWorkflows(result.workflows);
    } else {
      setError('Failed to load workflows');
    }

    setLoading(false);
  };

  // Fetch logs
  const fetchLogs = async () => {
    setLogsLoading(true);

    const params: any = {
      days: daysFilter,
      limit: logsPerPage,
      offset: (currentPage - 1) * logsPerPage
    };

    if (workflowFilter !== 'all') {
      params.workflow_type = workflowFilter;
    }

    if (successFilter !== 'all') {
      params.success = successFilter === 'success';
    }

    const result = await adminAIWorkflowsApi.getLogs(params);

    if (result.logs) {
      setLogs(result.logs);
      setTotalLogs(result.pagination.total);
    } else {
      setError('Failed to load logs');
    }

    setLogsLoading(false);
  };

  // Fetch log detail
  const fetchLogDetail = async (logId: string) => {
    setDetailLoading(true);
    setSelectedLog(null);

    const result = await adminAIWorkflowsApi.getLogDetail(logId);

    if (result) {
      setSelectedLog(result);
    }

    setDetailLoading(false);
  };

  // Initial load
  useEffect(() => {
    fetchWorkflows();
    fetchLogs();
  }, []);

  // Reload logs when filters change
  useEffect(() => {
    if (!loading) {
      setCurrentPage(1);
      fetchLogs();
    }
  }, [workflowFilter, successFilter, daysFilter]);

  // Reload logs when page changes
  useEffect(() => {
    if (!loading) {
      fetchLogs();
    }
  }, [currentPage]);

  const handleLogClick = (log: AIWorkflowLog) => {
    fetchLogDetail(log.id);
    setIsDetailDialogOpen(true);
  };

  const handleRefresh = () => {
    fetchWorkflows();
    fetchLogs();
  };

  const totalPages = Math.ceil(totalLogs / logsPerPage);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 p-8">
      <div className="max-w-7xl mx-auto space-y-8">
        {/* Header */}
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">AI Workflow Debug Panel</h1>
            <p className="text-gray-600 mt-1">
              Monitor and debug AI-powered workflows
            </p>
          </div>
          <div className="flex gap-3">
            <Select value={daysFilter.toString()} onValueChange={(v) => setDaysFilter(parseInt(v))}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Time range" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="1">Last 24 hours</SelectItem>
                <SelectItem value="7">Last 7 days</SelectItem>
                <SelectItem value="30">Last 30 days</SelectItem>
              </SelectContent>
            </Select>
            <Button onClick={handleRefresh} variant="outline" size="icon">
              <RefreshCw className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Error message */}
        {error && (
          <Card className="border-red-200 bg-red-50">
            <CardContent className="pt-6">
              <p className="text-red-600">{error}</p>
            </CardContent>
          </Card>
        )}

        {/* Workflow Overview Cards */}
        {loading ? (
          <div className="flex justify-center items-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {workflows.map((workflow) => {
              const Icon = WORKFLOW_ICONS[workflow.type] || Activity;
              const colorClass = WORKFLOW_COLORS[workflow.type] || 'bg-gray-100 text-gray-600';

              return (
                <Card key={workflow.type} className="shadow-lg hover:shadow-xl transition-shadow">
                  <CardHeader>
                    <div className="flex justify-between items-start">
                      <div className="flex-1">
                        <CardTitle className="text-lg">{workflow.name}</CardTitle>
                        <CardDescription className="text-sm mt-1">
                          {workflow.model}
                        </CardDescription>
                      </div>
                      <div className={`p-3 rounded-2xl ${colorClass}`}>
                        <Icon className="h-6 w-6" />
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <p className="text-xs text-gray-500">Total Runs</p>
                        <p className="text-2xl font-semibold">{workflow.total_executions}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500">Success Rate</p>
                        <p className="text-2xl font-semibold">{workflow.success_rate}%</p>
                      </div>
                    </div>

                    <div className="flex gap-2">
                      <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
                        <CheckCircle2 className="h-3 w-3 mr-1" />
                        {workflow.successful}
                      </Badge>
                      <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
                        <XCircle className="h-3 w-3 mr-1" />
                        {workflow.failed}
                      </Badge>
                    </div>

                    <div className="pt-4 border-t space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-600 flex items-center gap-1">
                          <Clock className="h-4 w-4" />
                          Avg Latency
                        </span>
                        <span className="font-medium">{workflow.avg_latency_ms}ms</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-600 flex items-center gap-1">
                          <DollarSign className="h-4 w-4" />
                          Estimated Cost
                        </span>
                        <span className="font-medium">${workflow.total_cost_usd.toFixed(6)}</span>
                      </div>
                    </div>

                    {workflow.last_execution && (
                      <div className="text-xs text-gray-500">
                        Last run: {new Date(workflow.last_execution).toLocaleString()}
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}

        {/* Execution History Table */}
        <Card className="shadow-lg">
          <CardHeader>
            <div className="flex justify-between items-center">
              <CardTitle>Execution History</CardTitle>
              <div className="flex gap-2">
                <Select value={workflowFilter} onValueChange={setWorkflowFilter}>
                  <SelectTrigger className="w-[180px]">
                    <SelectValue placeholder="Filter by workflow" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Workflows</SelectItem>
                    {workflows.map((w) => (
                      <SelectItem key={w.type} value={w.type}>{w.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Select value={successFilter} onValueChange={setSuccessFilter}>
                  <SelectTrigger className="w-[140px]">
                    <SelectValue placeholder="Filter by status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Status</SelectItem>
                    <SelectItem value="success">Success Only</SelectItem>
                    <SelectItem value="failure">Failures Only</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {logsLoading ? (
              <div className="flex justify-center items-center py-8">
                <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
              </div>
            ) : logs.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                No logs found for the selected filters
              </div>
            ) : (
              <>
                <div className="border rounded-lg overflow-hidden">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Timestamp</TableHead>
                        <TableHead>Workflow</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Latency</TableHead>
                        <TableHead>Cost</TableHead>
                        <TableHead>Student</TableHead>
                        <TableHead>Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {logs.map((log) => (
                        <TableRow key={log.id} className="cursor-pointer hover:bg-gray-50">
                          <TableCell className="text-sm">
                            {new Date(log.timestamp).toLocaleString()}
                          </TableCell>
                          <TableCell>
                            <Badge variant="secondary">{log.workflow_type}</Badge>
                          </TableCell>
                          <TableCell>
                            {log.success ? (
                              <Badge className="bg-green-100 text-green-800">
                                <CheckCircle2 className="h-3 w-3 mr-1" />
                                Success
                              </Badge>
                            ) : (
                              <Badge className="bg-red-100 text-red-800">
                                <XCircle className="h-3 w-3 mr-1" />
                                Failed
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-sm">{log.latency_ms}ms</TableCell>
                          <TableCell className="text-sm">
                            ${log.estimated_cost_usd?.toFixed(6) || '0'}
                          </TableCell>
                          <TableCell className="text-xs text-gray-600">
                            {log.user_id?.substring(0, 8) || 'N/A'}
                            {log.course_id && ` / ${log.course_id.substring(0, 8)}`}
                          </TableCell>
                          <TableCell>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleLogClick(log)}
                            >
                              View Details
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>

                {/* Pagination */}
                <div className="flex justify-between items-center mt-4">
                  <div className="text-sm text-gray-600">
                    Showing {(currentPage - 1) * logsPerPage + 1} to{' '}
                    {Math.min(currentPage * logsPerPage, totalLogs)} of {totalLogs} logs
                  </div>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                      disabled={currentPage === 1}
                    >
                      <ChevronLeft className="h-4 w-4" />
                      Previous
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                      disabled={currentPage >= totalPages}
                    >
                      Next
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        {/* Detail Dialog */}
        <Dialog open={isDetailDialogOpen} onOpenChange={setIsDetailDialogOpen}>
          <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Execution Detail</DialogTitle>
            </DialogHeader>

            {detailLoading ? (
              <div className="flex justify-center items-center py-12">
                <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
              </div>
            ) : selectedLog ? (
              <Tabs defaultValue="overview" className="w-full">
                <TabsList className="grid w-full grid-cols-4">
                  <TabsTrigger value="overview">Overview</TabsTrigger>
                  <TabsTrigger value="input">Input</TabsTrigger>
                  <TabsTrigger value="prompt">Prompt</TabsTrigger>
                  <TabsTrigger value="response">Response</TabsTrigger>
                </TabsList>

                <TabsContent value="overview" className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-sm text-gray-600">Workflow Type</p>
                      <p className="font-medium">{selectedLog.workflow_type}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Model</p>
                      <p className="font-medium">{selectedLog.model}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Status</p>
                      <p className="font-medium">
                        {selectedLog.response.success ? (
                          <Badge className="bg-green-100 text-green-800">Success</Badge>
                        ) : (
                          <Badge className="bg-red-100 text-red-800">Failed</Badge>
                        )}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Latency</p>
                      <p className="font-medium">{selectedLog.metrics.latency_ms}ms</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Estimated Cost</p>
                      <p className="font-medium">
                        ${selectedLog.metrics.estimated_cost_usd?.toFixed(6) || '0'}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Timestamp</p>
                      <p className="font-medium text-sm">
                        {new Date(selectedLog.timestamp).toLocaleString()}
                      </p>
                    </div>
                  </div>

                  {selectedLog.response.error_message && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                      <p className="text-sm font-medium text-red-900">Error Message</p>
                      <p className="text-sm text-red-700 mt-1">
                        {selectedLog.response.error_message}
                      </p>
                    </div>
                  )}
                </TabsContent>

                <TabsContent value="input">
                  <ScrollArea className="h-[400px]">
                    <pre className="text-xs bg-gray-50 p-4 rounded-lg overflow-x-auto">
                      {JSON.stringify(selectedLog.request, null, 2)}
                    </pre>
                  </ScrollArea>
                </TabsContent>

                <TabsContent value="prompt">
                  <ScrollArea className="h-[400px]">
                    <div className="space-y-4">
                      <div>
                        <p className="text-sm font-medium mb-2">System Instruction</p>
                        <pre className="text-xs bg-gray-50 p-4 rounded-lg whitespace-pre-wrap">
                          {selectedLog.request.system_instruction || 'None'}
                        </pre>
                      </div>

                      <div>
                        <p className="text-sm font-medium mb-2">Prompt</p>
                        <pre className="text-xs bg-gray-50 p-4 rounded-lg whitespace-pre-wrap">
                          {selectedLog.request.prompt || 'None'}
                        </pre>
                      </div>
                    </div>
                  </ScrollArea>
                </TabsContent>

                <TabsContent value="response">
                  <ScrollArea className="h-[400px]">
                    <div className="space-y-4">
                      {selectedLog.response.parsed_json && (
                        <div>
                          <p className="text-sm font-medium mb-2">Parsed JSON</p>
                          <pre className="text-xs bg-gray-50 p-4 rounded-lg overflow-x-auto">
                            {JSON.stringify(selectedLog.response.parsed_json, null, 2)}
                          </pre>
                        </div>
                      )}

                      {selectedLog.response.raw_text && (
                        <div>
                          <p className="text-sm font-medium mb-2">Raw Response</p>
                          <pre className="text-xs bg-gray-50 p-4 rounded-lg whitespace-pre-wrap">
                            {selectedLog.response.raw_text}
                          </pre>
                        </div>
                      )}
                    </div>
                  </ScrollArea>
                </TabsContent>
              </Tabs>
            ) : (
              <p className="text-center text-gray-500 py-8">No detail available</p>
            )}
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}
