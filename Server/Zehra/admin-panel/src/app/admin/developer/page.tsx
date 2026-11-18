'use client';

import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Code,
  TestTube,
  Settings,
  Wrench
} from "lucide-react";
import ApiTestingTool from "@/components/admin/developer/ApiTestingTool";

export default function DeveloperPage() {
  return (
    <div className="p-6 space-y-6 bg-gradient-to-br from-slate-50 to-blue-50 min-h-screen">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold text-gray-900 mb-2">Developer Tools</h1>
          <p className="text-gray-600">API testing and development utilities for Univa AI</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="p-2 bg-indigo-100 rounded-lg">
            <Code className="h-5 w-5 text-indigo-600" />
          </div>
        </div>
      </div>

      {/* Developer Tools Tabs */}
      <Tabs defaultValue="api-testing" className="w-full">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="api-testing" className="flex items-center gap-2">
            <TestTube className="h-4 w-4" />
            API Testing
          </TabsTrigger>
          <TabsTrigger value="database" className="flex items-center gap-2">
            <Settings className="h-4 w-4" />
            Database Tools
          </TabsTrigger>
          <TabsTrigger value="performance" className="flex items-center gap-2">
            <Wrench className="h-4 w-4" />
            Performance
          </TabsTrigger>
          <TabsTrigger value="utilities" className="flex items-center gap-2">
            <Code className="h-4 w-4" />
            Utilities
          </TabsTrigger>
        </TabsList>

        <TabsContent value="api-testing" className="space-y-6">
          <ApiTestingTool />
        </TabsContent>

        <TabsContent value="database" className="space-y-6">
          <Card className="bg-white border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <div className="p-1.5 bg-blue-100 rounded-lg">
                  <Settings className="h-4 w-4 text-blue-600" />
                </div>
                Database Management Tools
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-center py-8">
                <Settings className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500">Database management tools coming soon.</p>
                <p className="text-sm text-gray-400">Tools for managing Firebase collections, bulk operations, and data validation.</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="performance" className="space-y-6">
          <Card className="bg-white border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <div className="p-1.5 bg-green-100 rounded-lg">
                  <Wrench className="h-4 w-4 text-green-600" />
                </div>
                Performance Monitoring
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-center py-8">
                <Wrench className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500">Performance monitoring tools coming soon.</p>
                <p className="text-sm text-gray-400">API response times, database query performance, and system health monitoring.</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="utilities" className="space-y-6">
          <Card className="bg-white border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <div className="p-1.5 bg-purple-100 rounded-lg">
                  <Code className="h-4 w-4 text-purple-600" />
                </div>
                Development Utilities
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-center py-8">
                <Code className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500">Development utilities coming soon.</p>
                <p className="text-sm text-gray-400">Code generators, data transformers, and debugging tools.</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}