import { LayoutDashboard, GraduationCap, Library, ShieldAlert, DollarSign, Brain, Code } from 'lucide-react';

/**
 * Admin sidebar (one list for the desktop and mobile navigation).
 */
export const navigationItems = [
  { name: 'Dashboard', href: '/admin/dashboard', icon: LayoutDashboard, color: 'bg-blue-100 text-blue-700' },
  { name: 'Students', href: '/admin/students', icon: GraduationCap, color: 'bg-green-100 text-green-700' },
  { name: 'Catalog', href: '/admin/catalog', icon: Library, color: 'bg-purple-100 text-purple-700' },
  { name: 'Moderation', href: '/admin/moderation', icon: ShieldAlert, color: 'bg-rose-100 text-rose-700' },
  { name: 'AI Costs', href: '/admin/ai-costs', icon: DollarSign, color: 'bg-amber-100 text-amber-700' },
  { name: 'AI Workflows', href: '/admin/ai-workflows', icon: Brain, color: 'bg-cyan-100 text-cyan-700' },
  { name: 'Developer', href: '/admin/developer', icon: Code, color: 'bg-indigo-100 text-indigo-700' },
];
