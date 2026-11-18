'use client';

import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Send,
  Copy,
  CheckCircle,
  AlertCircle,
  Loader2,
  TestTube,
  Code2,
  Clock,
  FileText
} from "lucide-react";
import { toast } from "sonner";
import apiClient from '@/lib/api/client';
import JsonViewer from './JsonViewer';

interface ApiResponse {
  data: any;
  status: number;
  statusText: string;
  responseTime: number;
  headers: Record<string, string>;
}

export default function ApiTestingTool() {
  const [method, setMethod] = useState<string>('GET');
  const [endpoint, setEndpoint] = useState<string>('/api/');
  const [requestBody, setRequestBody] = useState<string>('');
  const [response, setResponse] = useState<ApiResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const httpMethods = ['GET', 'POST', 'PUT', 'DELETE'];

  const commonEndpoints = [
    '/api/admin/overview?days=7',
    '/api/admin/students',
    '/api/admin/catalog',
    '/api/admin/moderation?status=OPEN',
    '/api/admin/ai-costs?days=30',
    '/api/admin/ai-workflows/stats',
    '/api/courses',
    '/api/today',
    '/api/student/profile'
  ];

  const getMethodColor = (method: string) => {
    switch (method) {
      case 'GET': return 'bg-blue-100 text-blue-700';
      case 'POST': return 'bg-green-100 text-green-700';
      case 'PUT': return 'bg-yellow-100 text-yellow-700';
      case 'DELETE': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const getStatusColor = (status: number) => {
    if (status >= 200 && status < 300) return 'bg-green-100 text-green-700';
    if (status >= 400 && status < 500) return 'bg-red-100 text-red-700';
    if (status >= 500) return 'bg-red-100 text-red-700';
    return 'bg-gray-100 text-gray-700';
  };

  const formatJsonString = (str: string): string => {
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  };

  const handleSendRequest = async () => {
    if (!endpoint.trim()) {
      setError('Please enter an API endpoint');
      return;
    }

    setIsLoading(true);
    setError(null);
    setResponse(null);

    const startTime = Date.now();

    try {
      let data = undefined;
      if ((method === 'POST' || method === 'PUT') && requestBody.trim()) {
        try {
          data = JSON.parse(requestBody);
        } catch (e) {
          setError('Invalid JSON in request body');
          setIsLoading(false);
          return;
        }
      }

      const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;

      const result = await apiClient.makeRequest(
        method as 'GET' | 'POST' | 'PUT' | 'DELETE',
        cleanEndpoint,
        data
      );

      const responseTime = Date.now() - startTime;

      setResponse({
        data: result.data,
        status: result.status,
        statusText: result.statusText,
        responseTime,
        headers: result.headers as Record<string, string>
      });

      toast.success(`Request completed in ${responseTime}ms`);

    } catch (err: any) {
      const responseTime = Date.now() - startTime;

      if (err.response) {
        // Server responded with error status
        setResponse({
          data: err.response.data || { error: err.message },
          status: err.response.status || 500,
          statusText: err.response.statusText || 'Error',
          responseTime,
          headers: err.response.headers || {}
        });
      } else {
        // Network or other error
        setError(err.message || 'Failed to send request');
        toast.error(err.message || 'Request failed');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const copyResponse = () => {
    if (response) {
      const responseText = JSON.stringify(response.data, null, 2);
      navigator.clipboard.writeText(responseText);
      toast.success('Response copied to clipboard');
    }
  };

  const setCommonEndpoint = (endpoint: string) => {
    setEndpoint(endpoint);
  };

  return (
    <div className="space-y-6">
      {/* Request Configuration */}
      <Card className="bg-white border-0 shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <div className="p-1.5 bg-blue-100 rounded-lg">
              <TestTube className="h-4 w-4 text-blue-600" />
            </div>
            API Request Configuration
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* HTTP Method and Endpoint */}
          <div className="flex gap-4">
            <div className="flex-shrink-0">
              <Label htmlFor="method">Method</Label>
              <Select value={method} onValueChange={setMethod}>
                <SelectTrigger className="w-32">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {httpMethods.map((httpMethod) => (
                    <SelectItem key={httpMethod} value={httpMethod}>
                      <Badge variant="secondary" className={getMethodColor(httpMethod)}>
                        {httpMethod}
                      </Badge>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex-grow">
              <Label htmlFor="endpoint">API Endpoint</Label>
              <Input
                id="endpoint"
                value={endpoint}
                onChange={(e) => setEndpoint(e.target.value)}
                placeholder="/api/admin/students?limit=5"
                className="font-mono"
              />
            </div>

            <div className="flex-shrink-0 flex items-end">
              <Button
                onClick={handleSendRequest}
                disabled={isLoading}
                className="bg-blue-600 hover:bg-blue-700"
              >
                {isLoading ? (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <Send className="h-4 w-4 mr-2" />
                )}
                Send Request
              </Button>
            </div>
          </div>

          {/* Common Endpoints */}
          <div>
            <Label>Common Endpoints</Label>
            <div className="flex flex-wrap gap-2 mt-2">
              {commonEndpoints.map((commonEndpoint) => (
                <Button
                  key={commonEndpoint}
                  variant="outline"
                  size="sm"
                  onClick={() => setCommonEndpoint(commonEndpoint)}
                  className="text-xs h-8"
                >
                  {commonEndpoint}
                </Button>
              ))}
            </div>
          </div>

          {/* Request Body (for POST/PUT) */}
          {(method === 'POST' || method === 'PUT') && (
            <div>
              <Label htmlFor="requestBody">Request Body (JSON)</Label>
              <Textarea
                id="requestBody"
                value={requestBody}
                onChange={(e) => setRequestBody(e.target.value)}
                placeholder='{\n  "key": "value"\n}'
                className="font-mono h-32"
              />
              {requestBody && (
                <div className="mt-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setRequestBody(formatJsonString(requestBody))}
                  >
                    <Code2 className="h-3 w-3 mr-1" />
                    Format JSON
                  </Button>
                </div>
              )}
            </div>
          )}

          {/* Authentication Info */}
          <Alert>
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              Requests automatically include your admin JWT token in the Authorization header.
              Content-Type is set to application/json for POST/PUT requests.
            </AlertDescription>
          </Alert>
        </CardContent>
      </Card>

      {/* Response Display */}
      {(response || error) && (
        <Card className="bg-white border-0 shadow-lg">
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-green-100 rounded-lg">
                  <FileText className="h-4 w-4 text-green-600" />
                </div>
                Response
              </div>
              {response && (
                <div className="flex items-center gap-2">
                  <Badge variant="secondary" className={getStatusColor(response.status)}>
                    {response.status} {response.statusText}
                  </Badge>
                  <Badge variant="outline" className="flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {response.responseTime}ms
                  </Badge>
                  <Button variant="outline" size="sm" onClick={copyResponse}>
                    <Copy className="h-3 w-3 mr-1" />
                    Copy
                  </Button>
                </div>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {error ? (
              <div className="space-y-4">
                <Alert className="border-red-200 bg-red-50">
                  <AlertCircle className="h-4 w-4 text-red-600" />
                  <AlertDescription className="text-red-700">
                    {error}
                  </AlertDescription>
                </Alert>
              </div>
            ) : response ? (
              <div className="space-y-4">
                {/* Response Headers */}
                {Object.keys(response.headers).length > 0 && (
                  <div>
                    <Label className="text-sm font-medium">Response Headers</Label>
                    <div className="bg-gray-50 rounded-lg p-3 mt-1">
                      <pre className="text-xs font-mono text-gray-700">
                        {Object.entries(response.headers)
                          .map(([key, value]) => `${key}: ${value}`)
                          .join('\n')}
                      </pre>
                    </div>
                  </div>
                )}

                {/* Response Body */}
                <div>
                  <JsonViewer
                    data={response.data}
                    title="Response Body"
                    className="mt-1"
                  />
                </div>
              </div>
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Quick Test Examples */}
      <Card className="bg-white border-0 shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <div className="p-1.5 bg-purple-100 rounded-lg">
              <Code2 className="h-4 w-4 text-purple-600" />
            </div>
            Quick Test Examples
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <h4 className="font-medium text-gray-900">Common GET Requests</h4>
              <div className="space-y-1 text-sm">
                <div className="font-mono bg-gray-50 p-2 rounded">GET /api/admin/students</div>
                <div className="font-mono bg-gray-50 p-2 rounded">GET /api/admin/moderation</div>
                <div className="font-mono bg-gray-50 p-2 rounded">GET /api/admin/ai-costs?days=30</div>
              </div>
            </div>
            <div className="space-y-2">
              <h4 className="font-medium text-gray-900">Example POST Body</h4>
              <div className="space-y-1 text-sm">
                <pre className="font-mono bg-gray-50 p-2 rounded text-xs">{`{
  "name": "Test User",
  "email": "test@example.com",
  "role": "admin"
}`}</pre>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
