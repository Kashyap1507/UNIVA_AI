'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import adminAuthApi from '@/lib/api/admin-auth';
import { GraduationCap, Loader2, Mail, Lock, Eye, EyeOff, Shield } from 'lucide-react';

export default function AdminLoginPage() {
  const router = useRouter();
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState<{ [key: string]: string }>({});

  // Check if already authenticated
  useEffect(() => {
    if (adminAuthApi.isAuthenticated()) {
      const redirectPath = sessionStorage.getItem('admin_redirect_after_login') || '/admin/dashboard';
      sessionStorage.removeItem('admin_redirect_after_login');
      router.push(redirectPath);
    }
  }, [router]);

  const validateForm = (): boolean => {
    const newErrors: { [key: string]: string } = {};

    if (!formData.email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Invalid email format';
    }

    if (!formData.password.trim()) {
      newErrors.password = 'Password is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    console.log('[LoginPage] Form submitted');
    if (!validateForm()) {
      console.log('[LoginPage] Form validation failed');
      return;
    }

    console.log('[LoginPage] Starting login process');
    setIsLoading(true);

    try {
      console.log('[LoginPage] Calling adminAuthApi.login with email:', formData.email.trim().toLowerCase());
      const response = await adminAuthApi.login({
        email: formData.email.trim().toLowerCase(),
        password: formData.password
      });

      console.log('[LoginPage] Login API response:', response);
      console.log('[LoginPage] Token stored:', adminAuthApi.getToken() ? 'Yes' : 'No');
      console.log('[LoginPage] User stored:', adminAuthApi.getUser());

      toast.success('Admin login successful! Welcome back.');

      // Redirect to intended page or default admin page
      const redirectPath = sessionStorage.getItem('admin_redirect_after_login') || '/admin/dashboard';
      console.log('[LoginPage] Redirect path:', redirectPath);
      sessionStorage.removeItem('admin_redirect_after_login');

      // Small delay to show success message
      console.log('[LoginPage] Scheduling redirect in 1 second');
      setTimeout(() => {
        console.log('[LoginPage] Executing router.push to:', redirectPath);
        router.push(redirectPath);
      }, 1000);

    } catch (error: any) {
      console.error('[LoginPage] Admin login error:', error);
      console.error('[LoginPage] Error details:', error.response?.data);
      const errorMessage = error.response?.data?.error ||
                          error.message ||
                          'Login failed. Please try again.';
      toast.error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 via-pink-50 to-blue-50 flex items-center justify-center p-4">
      <Toaster position="top-right" richColors />
      
      {/* Background Decorations */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-10 -right-10 w-40 h-40 bg-yellow-200/30 rounded-full blur-3xl" />
        <div className="absolute top-1/3 -left-20 w-60 h-60 bg-purple-200/20 rounded-full blur-3xl" />
        <div className="absolute bottom-20 right-1/3 w-32 h-32 bg-pink-200/25 rounded-full blur-2xl" />
      </div>

      {/* Login Card */}
      <Card className="w-full max-w-md bg-white/80 backdrop-blur-sm border-0 shadow-2xl">
        <CardHeader className="text-center space-y-6 pb-8">
          {/* Logo */}
          <div className="flex justify-center">
            <div className="bg-gradient-to-r from-purple-500 to-pink-500 p-4 rounded-2xl shadow-lg">
              <GraduationCap className="h-8 w-8 text-white" />
            </div>
          </div>
          
          {/* Title */}
          <div className="space-y-2">
            <CardTitle className="text-2xl text-gray-900">Welcome Back!</CardTitle>
            <p className="text-gray-600">Sign in to the Univa AI admin panel</p>
          </div>
        </CardHeader>

        <CardContent className="space-y-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Email Field */}
            <div className="space-y-2">
              <Label htmlFor="email" className="text-gray-700">Email Address</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                <Input
                  id="email"
                  name="email"
                  type="email"
                  placeholder="admin@example.com"
                  value={formData.email}
                  onChange={handleChange}
                  className={`pl-10 h-12 bg-gray-50/50 border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500/20 ${errors.email ? 'border-red-300 focus:border-red-500' : ''}`}
                  disabled={isLoading}
                />
              </div>
              {errors.email && (
                <p className="text-sm text-red-600">{errors.email}</p>
              )}
            </div>

            {/* Password Field */}
            <div className="space-y-2">
              <Label htmlFor="password" className="text-gray-700">Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                <Input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Enter your password"
                  value={formData.password}
                  onChange={handleChange}
                  className={`pl-10 pr-10 h-12 bg-gray-50/50 border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500/20 ${errors.password ? 'border-red-300 focus:border-red-500' : ''}`}
                  disabled={isLoading}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="absolute right-2 top-1/2 transform -translate-y-1/2 h-8 w-8 p-0"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={isLoading}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4 text-gray-400" />
                  ) : (
                    <Eye className="h-4 w-4 text-gray-400" />
                  )}
                </Button>
              </div>
              {errors.password && (
                <p className="text-sm text-red-600">{errors.password}</p>
              )}
            </div>

            {/* Sign In Button */}
            <Button
              type="submit"
              className="w-full h-12 bg-gradient-to-r from-purple-500 to-pink-500 hover:from-purple-600 hover:to-pink-600 text-white rounded-xl font-medium text-base shadow-lg"
              disabled={isLoading}
            >
              {isLoading ? (
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Signing In...
                </div>
              ) : (
                "Sign In"
              )}
            </Button>
          </form>

          {/* Demo Credentials */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mt-6">
            <p className="text-sm text-blue-800 mb-2">Admin Access:</p>
            <div className="text-xs text-blue-700 space-y-1">
              <p><strong>Note:</strong> Only registered admin users can access the admin panel.</p>
              <p>Contact your system administrator if you need admin access.</p>
            </div>
          </div>

          {/* Footer */}
          <div className="text-center pt-4 border-t border-gray-100">
            <p className="text-xs text-gray-500">
              © 2026 Univa AI
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}