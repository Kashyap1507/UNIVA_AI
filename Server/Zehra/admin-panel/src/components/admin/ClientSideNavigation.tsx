'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { Button } from "@/components/ui/button";

interface NavigationItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}

interface ClientSideNavigationProps {
  items: NavigationItem[];
  currentPath: string;
  onNavigate?: (path: string) => void;
}

// Simple cache implementation
const navigationCache = new Map<string, any>();

export function useClientSideNavigation() {
  const router = useRouter();
  const pathname = usePathname();
  const [isNavigating, setIsNavigating] = useState(false);

  const navigateWithCache = useCallback(async (href: string) => {
    if (pathname === href) return;
    
    setIsNavigating(true);
    
    try {
      // Check if data is already cached
      const cachedData = navigationCache.get(href);
      if (cachedData) {
        console.log(`Using cached data for ${href}`);
        // Use cached data to prevent unnecessary API calls
      }
      
      // Perform the navigation
      router.push(href);
    } catch (error) {
      console.error('Navigation error:', error);
    } finally {
      setIsNavigating(false);
    }
  }, [router, pathname]);

  const preloadRoute = useCallback((href: string) => {
    // Preload the route for faster navigation
    router.prefetch(href);
  }, [router]);

  const cacheData = useCallback((key: string, data: any) => {
    navigationCache.set(key, {
      data,
      timestamp: Date.now(),
      expires: Date.now() + 5 * 60 * 1000 // 5 minutes cache
    });
  }, []);

  const getCachedData = useCallback((key: string) => {
    const cached = navigationCache.get(key);
    if (cached && cached.expires > Date.now()) {
      return cached.data;
    }
    // Remove expired cache
    navigationCache.delete(key);
    return null;
  }, []);

  const clearCache = useCallback(() => {
    navigationCache.clear();
  }, []);

  return {
    navigateWithCache,
    preloadRoute,
    cacheData,
    getCachedData,
    clearCache,
    isNavigating
  };
}

export function ClientSideNavigationWrapper({ 
  children, 
  navigationItems 
}: { 
  children: React.ReactNode;
  navigationItems: NavigationItem[];
}) {
  const { preloadRoute } = useClientSideNavigation();

  useEffect(() => {
    // Preload all navigation routes on component mount
    navigationItems.forEach(item => {
      preloadRoute(item.href);
    });
  }, [navigationItems, preloadRoute]);

  return <>{children}</>;
}