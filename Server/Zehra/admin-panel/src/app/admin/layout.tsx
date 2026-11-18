'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import AdminNavigation from '@/components/admin/AdminNavigation';
import AuthGuard from '@/components/admin/AuthGuard';
import { ClientSideNavigationWrapper } from '@/components/admin/ClientSideNavigation';
import { Toaster } from '@/components/ui/sonner';
import { navigationItems } from '@/components/admin/nav-items';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const isLoginPage = pathname === '/admin/login';

  return (
    <AuthGuard>
      <ClientSideNavigationWrapper navigationItems={navigationItems}>
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50">
          {/* Only show navigation if not on login page */}
          {!isLoginPage && <AdminNavigation />}
          
          {/* Main content - adjust padding based on whether navigation is shown */}
          <main className={isLoginPage ? '' : 'lg:pl-64'}>
            {children}
          </main>
          <Toaster />
        </div>
      </ClientSideNavigationWrapper>
    </AuthGuard>
  );
}