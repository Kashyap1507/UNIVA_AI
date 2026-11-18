import React from 'react';
import { ExternalLink, Activity, Bug, BarChart3, GitBranch, Shield } from 'lucide-react';

/**
 * Monitoring Dashboard - Quick Links Version
 *
 * This is a simple link-based monitoring dashboard that provides quick access
 * to all external monitoring services (Sentry, Firebase, GitHub Actions).
 *
 * For embedded metrics version, see:
 * /docs/guides/MONITORING_DASHBOARD_INTEGRATION.md
 */

interface MonitoringLink {
  name: string;
  url: string;
  description: string;
  icon: React.ReactNode;
  badge?: string;
  category: 'errors' | 'analytics' | 'cicd' | 'security';
}

export default function MonitoringDashboard() {
  const monitoringLinks: MonitoringLink[] = [
    // Error Monitoring
    {
      name: 'Sentry - iOS App',
      url: 'https://sentry.io/organizations/wizly/projects/wizly-ios/',
      description: 'Monitor iOS app crashes, errors, and performance issues',
      icon: <Bug className="h-6 w-6" />,
      badge: 'Errors',
      category: 'errors',
    },
    {
      name: 'Sentry - Android App',
      url: 'https://sentry.io/organizations/wizly/projects/wizly-android/',
      description: 'Monitor Android app crashes, errors, and performance issues',
      icon: <Bug className="h-6 w-6" />,
      badge: 'Errors',
      category: 'errors',
    },
    {
      name: 'Sentry - Backend API',
      url: 'https://sentry.io/organizations/wizly/projects/wizly-backend/',
      description: 'Monitor backend API errors, exceptions, and slow queries',
      icon: <Bug className="h-6 w-6" />,
      badge: 'Errors',
      category: 'errors',
    },

    // Analytics
    {
      name: 'Firebase Analytics',
      url: 'https://console.firebase.google.com/project/_/analytics',
      description: 'View user analytics, app events, and engagement metrics',
      icon: <BarChart3 className="h-6 w-6" />,
      badge: 'Analytics',
      category: 'analytics',
    },
    {
      name: 'Firebase Crashlytics',
      url: 'https://console.firebase.google.com/project/_/crashlytics',
      description: 'Android crash reports (redundant with Sentry)',
      icon: <Shield className="h-6 w-6" />,
      badge: 'Analytics',
      category: 'analytics',
    },
    {
      name: 'Firebase Remote Config',
      url: 'https://console.firebase.google.com/project/_/config',
      description: 'Manage feature flags and remote configuration parameters',
      icon: <Activity className="h-6 w-6" />,
      badge: 'Config',
      category: 'analytics',
    },

    // CI/CD
    {
      name: 'GitHub Actions - All Workflows',
      url: 'https://github.com/sparxlabs-ai/wizly/actions',
      description: 'View all CI/CD pipeline runs (iOS, Android, Backend)',
      icon: <GitBranch className="h-6 w-6" />,
      badge: 'CI/CD',
      category: 'cicd',
    },
    {
      name: 'GitHub Actions - iOS CI',
      url: 'https://github.com/sparxlabs-ai/wizly/actions/workflows/ios-ci.yml',
      description: 'iOS build and test workflow status',
      icon: <GitBranch className="h-6 w-6" />,
      badge: 'CI/CD',
      category: 'cicd',
    },
    {
      name: 'GitHub Actions - Android CI',
      url: 'https://github.com/sparxlabs-ai/wizly/actions/workflows/android-ci.yml',
      description: 'Android build, test, and lint workflow status',
      icon: <GitBranch className="h-6 w-6" />,
      badge: 'CI/CD',
      category: 'cicd',
    },
    {
      name: 'GitHub Actions - Backend CI',
      url: 'https://github.com/sparxlabs-ai/wizly/actions/workflows/backend-ci.yml',
      description: 'Backend test, lint, and security scan workflow status',
      icon: <GitBranch className="h-6 w-6" />,
      badge: 'CI/CD',
      category: 'cicd',
    },
  ];

  const getCategoryColor = (category: string) => {
    switch (category) {
      case 'errors':
        return 'border-red-200 hover:border-red-500 hover:bg-red-50';
      case 'analytics':
        return 'border-blue-200 hover:border-blue-500 hover:bg-blue-50';
      case 'cicd':
        return 'border-green-200 hover:border-green-500 hover:bg-green-50';
      case 'security':
        return 'border-purple-200 hover:border-purple-500 hover:bg-purple-50';
      default:
        return 'border-gray-200 hover:border-gray-500';
    }
  };

  const getBadgeColor = (badge: string) => {
    switch (badge) {
      case 'Errors':
        return 'bg-red-100 text-red-800';
      case 'Analytics':
        return 'bg-blue-100 text-blue-800';
      case 'CI/CD':
        return 'bg-green-100 text-green-800';
      case 'Config':
        return 'bg-purple-100 text-purple-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto p-6">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">
            Monitoring & Analytics Dashboard
          </h1>
          <p className="text-gray-600">
            Quick access to all monitoring services for iOS, Android, and Backend
          </p>
        </div>

        {/* Alert Box */}
        <div className="mb-8 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="flex items-start">
            <Activity className="h-5 w-5 text-blue-600 mt-0.5 mr-3" />
            <div>
              <h3 className="font-semibold text-blue-900 mb-1">
                External Monitoring Services
              </h3>
              <p className="text-sm text-blue-800">
                All monitoring data is hosted on external platforms. Click any card below
                to open the full dashboard. For embedded metrics in this admin panel,
                see <code className="bg-blue-100 px-1 py-0.5 rounded">/docs/guides/MONITORING_DASHBOARD_INTEGRATION.md</code>
              </p>
            </div>
          </div>
        </div>

        {/* Error Monitoring Section */}
        <section className="mb-12">
          <h2 className="text-2xl font-bold text-gray-900 mb-4 flex items-center">
            <Bug className="h-6 w-6 mr-2 text-red-600" />
            Error & Crash Monitoring
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {monitoringLinks
              .filter((link) => link.category === 'errors')
              .map((link) => (
                <MonitoringCard key={link.name} link={link} getCategoryColor={getCategoryColor} getBadgeColor={getBadgeColor} />
              ))}
          </div>
        </section>

        {/* Analytics Section */}
        <section className="mb-12">
          <h2 className="text-2xl font-bold text-gray-900 mb-4 flex items-center">
            <BarChart3 className="h-6 w-6 mr-2 text-blue-600" />
            Analytics & Configuration
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {monitoringLinks
              .filter((link) => link.category === 'analytics')
              .map((link) => (
                <MonitoringCard key={link.name} link={link} getCategoryColor={getCategoryColor} getBadgeColor={getBadgeColor} />
              ))}
          </div>
        </section>

        {/* CI/CD Section */}
        <section className="mb-12">
          <h2 className="text-2xl font-bold text-gray-900 mb-4 flex items-center">
            <GitBranch className="h-6 w-6 mr-2 text-green-600" />
            CI/CD Pipeline Status
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {monitoringLinks
              .filter((link) => link.category === 'cicd')
              .map((link) => (
                <MonitoringCard key={link.name} link={link} getCategoryColor={getCategoryColor} getBadgeColor={getBadgeColor} />
              ))}
          </div>
        </section>

        {/* Footer Info */}
        <div className="mt-12 p-6 bg-white border border-gray-200 rounded-lg">
          <h3 className="text-lg font-semibold mb-3">Quick Access Tips</h3>
          <ul className="space-y-2 text-sm text-gray-700">
            <li className="flex items-start">
              <span className="font-mono bg-gray-100 px-2 py-1 rounded mr-2 text-xs">⌘+Click</span>
              <span>Open links in a new tab (Mac)</span>
            </li>
            <li className="flex items-start">
              <span className="font-mono bg-gray-100 px-2 py-1 rounded mr-2 text-xs">Ctrl+Click</span>
              <span>Open links in a new tab (Windows/Linux)</span>
            </li>
            <li className="flex items-start">
              <span className="font-mono bg-gray-100 px-2 py-1 rounded mr-2 text-xs">Bookmark</span>
              <span>Bookmark this page for quick access to all monitoring dashboards</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}

// Monitoring Card Component
interface MonitoringCardProps {
  link: MonitoringLink;
  getCategoryColor: (category: string) => string;
  getBadgeColor: (badge: string) => string;
}

function MonitoringCard({ link, getCategoryColor, getBadgeColor }: MonitoringCardProps) {
  return (
    <a
      href={link.url}
      target="_blank"
      rel="noopener noreferrer"
      className={`block p-6 border-2 rounded-lg transition-all duration-200 shadow-sm hover:shadow-md bg-white ${getCategoryColor(
        link.category
      )}`}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="p-2 bg-gray-100 rounded-lg">
          {link.icon}
        </div>
        {link.badge && (
          <span className={`px-2 py-1 text-xs font-semibold rounded ${getBadgeColor(link.badge)}`}>
            {link.badge}
          </span>
        )}
      </div>

      <h3 className="text-lg font-bold text-gray-900 mb-2 flex items-center">
        {link.name}
        <ExternalLink className="h-4 w-4 ml-2 text-gray-400" />
      </h3>

      <p className="text-sm text-gray-600 leading-relaxed">
        {link.description}
      </p>
    </a>
  );
}
