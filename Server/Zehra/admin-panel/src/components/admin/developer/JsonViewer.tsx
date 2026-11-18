'use client';

import React from 'react';
import { Button } from "@/components/ui/button";
import { Copy, CheckCircle } from "lucide-react";
import { toast } from "sonner";

interface JsonViewerProps {
  data: any;
  title?: string;
  className?: string;
}

export default function JsonViewer({ data, title, className = "" }: JsonViewerProps) {
  const [copied, setCopied] = React.useState(false);

  const jsonString = JSON.stringify(data, null, 2);

  const copyToClipboard = () => {
    navigator.clipboard.writeText(jsonString);
    setCopied(true);
    toast.success('JSON copied to clipboard');
    setTimeout(() => setCopied(false), 2000);
  };

  // Simple syntax highlighting for JSON
  const highlightJson = (json: string) => {
    return json
      .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, (match) => {
        let cls = 'text-gray-800';
        if (/^"/.test(match)) {
          if (/:$/.test(match)) {
            cls = 'text-blue-600 font-medium'; // Keys
          } else {
            cls = 'text-green-600'; // String values
          }
        } else if (/true|false/.test(match)) {
          cls = 'text-purple-600'; // Booleans
        } else if (/null/.test(match)) {
          cls = 'text-red-600'; // Null
        } else if (/-?\d+/.test(match)) {
          cls = 'text-orange-600'; // Numbers
        }
        return `<span class="${cls}">${match}</span>`;
      });
  };

  return (
    <div className={`bg-gray-50 rounded-lg border ${className}`}>
      {title && (
        <div className="flex items-center justify-between px-4 py-2 border-b bg-gray-100 rounded-t-lg">
          <h4 className="text-sm font-medium text-gray-700">{title}</h4>
          <Button
            variant="ghost"
            size="sm"
            onClick={copyToClipboard}
            className="h-6 px-2"
          >
            {copied ? (
              <CheckCircle className="h-3 w-3 text-green-600" />
            ) : (
              <Copy className="h-3 w-3" />
            )}
          </Button>
        </div>
      )}
      <div className="p-4 max-h-96 overflow-auto">
        <pre
          className="text-xs font-mono leading-relaxed"
          dangerouslySetInnerHTML={{
            __html: highlightJson(jsonString)
          }}
        />
      </div>
    </div>
  );
}