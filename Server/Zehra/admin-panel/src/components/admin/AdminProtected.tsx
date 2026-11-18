'use client';

import React, { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import adminAuthApi from '@/lib/api/admin-auth';
import { Card, CardContent } from '@/components/ui/card';
import { Loader2 } from 'lucide-react';

interface AdminProtectedProps {
  children: React.ReactNode;
  permission?: string; // Optional specific permission required
  fallback?: React.ReactNode; // Custom loading component
}

interface AdminAuthState {
  isLoading: boolean;
  isAuthenticated: boolean;
  user: any | null;
  error: string | null;
}

export default function AdminProtected({ 
  children, 
  permission, 
  fallback 
}: AdminProtectedProps) {
  const router = useRouter();
  const hasVerifiedRef = useRef(false);
  const [authState, setAuthState] = useState<AdminAuthState>({
    isLoading: true,
    isAuthenticated: false,
    user: null,
    error: null
  });

  useEffect(() => {
    let mounted = true;

    const verifyAdminAuth = async () => {
      if (hasVerifiedRef.current) {
        return; // Prevent multiple calls
      }
      hasVerifiedRef.current = true;

      try {
        // Check if token exists
        if (!adminAuthApi.isAuthenticated()) {
          setAuthState({
            isLoading: false,
            isAuthenticated: false,
            user: null,
            error: 'No admin token found'
          });
          return;
        }

        // Verify token with server
        const verification = await adminAuthApi.verifyToken();
        
        if (verification.valid && verification.user) {
          // Check specific permission if required
          if (permission && !verification.user.admin_permissions.includes(permission) && 
              !verification.user.admin_permissions.includes('admin:all')) {
            setAuthState({
              isLoading: false,
              isAuthenticated: false,
              user: null,
              error: `Admin permission "${permission}" required`
            });
            return;
          }

          setAuthState({
            isLoading: false,
            isAuthenticated: true,
            user: verification.user,
            error: null
          });
        } else {
          // Clear invalid token
          adminAuthApi.clearAuth();
          setAuthState({
            isLoading: false,
            isAuthenticated: false,
            user: null,
            error: verification.error || 'Admin authentication failed'
          });
        }
      } catch (error) {
        console.error('Admin auth verification error:', error);
        adminAuthApi.clearAuth();
        setAuthState({
          isLoading: false,
          isAuthenticated: false,
          user: null,
          error: 'Authentication verification failed'
        });
      }
    };

    verifyAdminAuth();

    return () => {
      mounted = false;
    };
  }, []); // Remove permission dependency to prevent re-runs

  // Redirect to login if not authenticated (only after loading is complete)
  useEffect(() => {
    if (!authState.isLoading && !authState.isAuthenticated) {
      const currentPath = window.location.pathname;
      // Store the intended destination
      if (currentPath !== '/admin/login') {
        sessionStorage.setItem('admin_redirect_after_login', currentPath);
      }
      router.push('/admin/login');
    }
  }, [authState.isLoading, authState.isAuthenticated, router]);

  // Show loading state
  if (authState.isLoading) {
    if (fallback) {
      return <>{fallback}</>;
    }

    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 flex items-center justify-center">
        <Card className="w-full max-w-md">
          <CardContent className="flex flex-col items-center justify-center p-8">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-4" />
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              Verifying Admin Access
            </h2>
            <p className="text-sm text-gray-600 text-center">
              Please wait while we verify your admin credentials...
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // Show error state (shouldn't normally be seen due to redirect)
  if (!authState.isAuthenticated) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 flex items-center justify-center">
        <Card className="w-full max-w-md">
          <CardContent className="flex flex-col items-center justify-center p-8">
            <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center mb-4">
              <span className="text-red-600 text-xl">⚠</span>
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              Access Denied
            </h2>
            <p className="text-sm text-gray-600 text-center mb-4">
              {authState.error || 'Admin authentication required'}
            </p>
            <p className="text-xs text-gray-500 text-center">
              You will be redirected to the login page...
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // Render protected content
  return <>{children}</>;
}