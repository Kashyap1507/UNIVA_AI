import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Univa AI Admin',
  description: 'Admin panel for Univa AI, the study assistant for university students',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className="bg-gray-50 min-h-screen">
        <div className="min-h-screen">
          {children}
        </div>
      </body>
    </html>
  )
}