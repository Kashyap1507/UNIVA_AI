'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import adminAuthApi from '@/lib/api/admin-auth';

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    // Check if user is authenticated as admin
    if (adminAuthApi.isAuthenticated()) {
      // Get redirect path from session storage or default to dashboard
      const redirectPath = sessionStorage.getItem('admin_redirect_after_login') || '/admin/dashboard';
      sessionStorage.removeItem('admin_redirect_after_login');
      router.push(redirectPath);
    } else {
      // Not authenticated, redirect to login
      router.push('/admin/login');
    }
  }, [router]);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <div className="animate-spin h-8 w-8 border-2 border-primary-500 border-t-transparent rounded-full mx-auto mb-4"></div>
        <p className="text-gray-600">Checking authentication...</p>
      </div>
    </div>
  );
}