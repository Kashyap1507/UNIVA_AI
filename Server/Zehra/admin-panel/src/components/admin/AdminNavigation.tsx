'use client';

import React, { useState, useCallback, useEffect } from 'react';
import adminAuthApi from '@/lib/api/admin-auth';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { Button } from "@/components/ui/button";
import { useClientSideNavigation } from './ClientSideNavigation';
import {
  LayoutDashboard,
  Users,
  BookOpen,
  BarChart3,
  Settings,
  LogOut,
  GraduationCap,
  Menu,
  X,
  HelpCircle,
  User,
  Code,
  Brain,
  FileText
} from "lucide-react";
import { navigationItems } from '@/components/admin/nav-items';

export default function AdminNavigation() {
  const pathname = usePathname();
  const router = useRouter();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [adminUser, setAdminUser] = useState({ name: 'Admin', email: '' });

  // The signed-in admin, read after mount (localStorage is browser-only)
  useEffect(() => {
    const user = adminAuthApi.getUser() as { name?: string; email?: string } | null;
    if (user) setAdminUser({ name: user.name || 'Admin', email: user.email || '' });
  }, []);
  const { navigateWithCache, preloadRoute, isNavigating } = useClientSideNavigation();

  const handleLogout = () => {
    // Clear admin authentication
    localStorage.removeItem('admin_token');
    sessionStorage.removeItem('admin_token');
    router.push('/admin/login');
  };

  const isCurrentPath = (href: string) => {
    return pathname === href || pathname.startsWith(href + '/');
  };

  const handleNavigation = useCallback((e: React.MouseEvent, href: string) => {
    e.preventDefault();
    if (isMobileMenuOpen) {
      setIsMobileMenuOpen(false);
    }
    navigateWithCache(href);
  }, [navigateWithCache, isMobileMenuOpen]);

  const handleMouseEnter = useCallback((href: string) => {
    preloadRoute(href);
  }, [preloadRoute]);

  return (
    <>
      {/* Desktop Sidebar */}
      <div className="hidden lg:flex lg:w-64 lg:flex-col lg:fixed lg:inset-y-0 lg:border-r lg:border-gray-200 lg:bg-white lg:shadow-sm">
        {/* Logo */}
        <div className="flex items-center space-x-3 p-6 border-b border-gray-100">
          <div className="bg-gradient-to-r from-purple-400 to-pink-400 p-2 rounded-xl">
            <GraduationCap className="h-6 w-6 text-white" />
          </div>
          <div>
            <h1 className="font-semibold text-gray-900">Wizly AI</h1>
            <p className="text-xs text-gray-500">Admin Panel</p>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="p-4 space-y-2">
          {navigationItems.map((item) => {
            const Icon = item.icon;
            const isActive = isCurrentPath(item.href);
            
            return (
              <Link
                key={item.name}
                href={item.href}
                onClick={(e) => handleNavigation(e, item.href)}
                onMouseEnter={() => handleMouseEnter(item.href)}
                className={`w-full justify-start gap-3 h-12 flex items-center px-3 py-2 text-sm font-medium rounded-lg transition-colors ${
                  isActive 
                    ? 'bg-gradient-to-r from-purple-50 to-pink-50 border border-purple-200' 
                    : 'hover:bg-gray-50'
                } ${
                  isNavigating ? 'opacity-75 pointer-events-none' : ''
                }`}
              >
                <div className={`p-1.5 rounded-lg ${item.color}`}>
                  <Icon className="h-4 w-4" />
                </div>
                {item.name}
              </Link>
            );
          })}
        </nav>

        {/* User Profile & Logout */}
        <div className="mt-auto p-4 border-t border-gray-100">
          <div className="flex items-center gap-3 mb-4">
            <div className="bg-blue-100 p-2 rounded-full">
              <User className="h-4 w-4 text-blue-600" />
            </div>
            <div>
              <p className="text-sm font-medium text-gray-900">{adminUser.name}</p>
              <p className="text-xs text-gray-500 truncate">{adminUser.email}</p>
            </div>
          </div>
          <Button 
            variant="outline" 
            size="sm" 
            className="w-full text-red-600 hover:text-red-700 hover:bg-red-50"
            onClick={handleLogout}
          >
            <LogOut className="h-4 w-4 mr-2" />
            Logout
          </Button>
        </div>
      </div>

      {/* Mobile Header */}
      <div className="lg:hidden">
        <div className="flex items-center justify-between h-16 px-4 bg-white border-b border-gray-200 shadow-sm">
          {/* Mobile menu button */}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          >
            {isMobileMenuOpen ? (
              <X className="h-5 w-5" />
            ) : (
              <Menu className="h-5 w-5" />
            )}
          </Button>

          {/* Logo */}
          <div className="flex items-center">
            <div className="w-8 h-8 bg-gradient-to-r from-purple-500 to-pink-500 rounded-lg flex items-center justify-center">
              <GraduationCap className="h-5 w-5 text-white" />
            </div>
            <span className="ml-2 text-lg font-semibold text-gray-800">Wizly AI</span>
          </div>

          {/* Profile */}
          <div className="bg-blue-100 p-2 rounded-full">
            <User className="h-4 w-4 text-blue-600" />
          </div>
        </div>

        {/* Mobile Menu */}
        {isMobileMenuOpen && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <div className="fixed inset-0 bg-gray-600 bg-opacity-75" onClick={() => setIsMobileMenuOpen(false)} />
            <div className="relative flex-1 flex flex-col max-w-xs w-full bg-white">
              <div className="absolute top-0 right-0 -mr-12 pt-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setIsMobileMenuOpen(false)}
                  className="text-white"
                >
                  <X className="h-6 w-6" />
                </Button>
              </div>

              {/* Mobile Logo */}
              <div className="flex items-center h-16 flex-shrink-0 px-4 border-b border-gray-200">
                <div className="flex items-center">
                  <div className="w-8 h-8 bg-gradient-to-r from-purple-500 to-pink-500 rounded-lg flex items-center justify-center">
                    <GraduationCap className="h-5 w-5 text-white" />
                  </div>
                  <span className="ml-3 text-xl font-semibold text-gray-800">Wizly AI</span>
                </div>
              </div>

              {/* Mobile Navigation */}
              <div className="mt-5 flex-1 h-0 overflow-y-auto">
                <nav className="px-4 space-y-2">
                  {navigationItems.map((item) => {
                    const Icon = item.icon;
                    const isActive = isCurrentPath(item.href);
                    
                    return (
                      <Link
                        key={item.name}
                        href={item.href}
                        onClick={(e) => handleNavigation(e, item.href)}
                        onMouseEnter={() => handleMouseEnter(item.href)}
                        className={`w-full justify-start gap-3 h-12 flex items-center px-3 py-2 text-sm font-medium rounded-lg transition-colors ${
                          isActive 
                            ? 'bg-gradient-to-r from-purple-50 to-pink-50 border border-purple-200' 
                            : 'hover:bg-gray-50'
                        } ${
                          isNavigating ? 'opacity-75 pointer-events-none' : ''
                        }`}
                      >
                        <div className={`p-1.5 rounded-lg ${item.color}`}>
                          <Icon className="h-4 w-4" />
                        </div>
                        {item.name}
                      </Link>
                    );
                  })}
                </nav>
              </div>

              {/* Mobile Profile Section */}
              <div className="flex-shrink-0 border-t border-gray-100 p-4">
                <div className="flex items-center gap-3 mb-4">
                  <div className="bg-blue-100 p-2 rounded-full">
                    <User className="h-4 w-4 text-blue-600" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-gray-900">{adminUser.name}</p>
                    <p className="text-xs text-gray-500 truncate">{adminUser.email}</p>
                  </div>
                </div>
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="w-full text-red-600 hover:text-red-700 hover:bg-red-50"
                  onClick={handleLogout}
                >
                  <LogOut className="h-4 w-4 mr-2" />
                  Logout
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  );
}