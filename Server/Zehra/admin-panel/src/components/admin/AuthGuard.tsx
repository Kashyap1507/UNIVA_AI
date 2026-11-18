'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import adminAuthApi from '@/lib/api/admin-auth';

interface AuthGuardProps {
  children: React.ReactNode;
}

export default function AuthGuard({ children }: AuthGuardProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    checkAuthentication();
  }, [pathname]);

  const checkAuthentication = async () => {
    try {
      console.log('[AuthGuard] Starting authentication check for path:', pathname);
      setIsLoading(true);

      // Check if we have a token
      const hasToken = adminAuthApi.isAuthenticated();
      console.log('[AuthGuard] Has token:', hasToken);

      if (!hasToken) {
        console.log('[AuthGuard] No token found, redirecting to login');
        redirectToLogin();
        return;
      }

      // Verify token with backend
      console.log('[AuthGuard] Verifying token with backend');
      const verification = await adminAuthApi.verifyToken();
      console.log('[AuthGuard] Verification result:', verification);

      if (verification.valid && verification.user?.is_admin) {
        console.log('[AuthGuard] Token valid and user is admin, granting access');
        setIsAuthenticated(true);
      } else {
        console.log('[AuthGuard] Invalid token or not admin, clearing auth');
        // Invalid token or not admin, clear auth and redirect
        adminAuthApi.clearAuth();
        redirectToLogin();
      }
    } catch (error) {
      console.error('[AuthGuard] Authentication check failed:', error);
      adminAuthApi.clearAuth();
      redirectToLogin();
    } finally {
      console.log('[AuthGuard] Authentication check complete, isLoading:', false);
      setIsLoading(false);
    }
  };

  const redirectToLogin = () => {
    // Don't redirect if we're already on login page
    if (pathname === '/admin/login') {
      setIsLoading(false);
      return;
    }

    // Store current path for redirect after login
    sessionStorage.setItem('admin_redirect_after_login', pathname);
    router.push('/admin/login');
  };

  // Show loading spinner while checking authentication
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin h-8 w-8 border-2 border-blue-500 border-t-transparent rounded-full mx-auto mb-4"></div>
          <p className="text-gray-600">Verifying authentication...</p>
        </div>
      </div>
    );
  }

  // Don't render protected content if not authenticated
  if (!isAuthenticated && pathname !== '/admin/login') {
    return null;
  }

  // Render children if authenticated or on login page
  return <>{children}</>;
}