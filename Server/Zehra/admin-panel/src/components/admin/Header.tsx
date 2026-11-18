import { Search, User, LogOut, GraduationCap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";

export function Header() {
  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between max-w-7xl mx-auto">
        {/* Logo */}
        <div className="flex items-center space-x-3">
          <div className="bg-gradient-to-r from-purple-400 to-pink-400 p-2 rounded-xl">
            <GraduationCap className="h-6 w-6 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Univa AI</h1>
            <p className="text-xs text-gray-500">Admin Panel</p>
          </div>
        </div>

        {/* Search */}
        <div className="flex-1 max-w-md mx-8">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
            <Input
              type="text"
              placeholder="Search students, courses..."
              className="pl-10 bg-gray-50 border-gray-200 rounded-lg"
            />
          </div>
        </div>

        {/* Profile & Logout */}
        <div className="flex items-center space-x-3">
          <Button variant="ghost" size="sm" className="text-gray-600 hover:text-gray-900">
            <User className="h-4 w-4 mr-2" />
            Profile
          </Button>
          <Avatar className="h-8 w-8">
            <AvatarFallback className="bg-blue-100 text-blue-600">AD</AvatarFallback>
          </Avatar>
          <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700">
            <LogOut className="h-4 w-4 mr-2" />
            Logout
          </Button>
        </div>
      </div>
    </header>
  );
}