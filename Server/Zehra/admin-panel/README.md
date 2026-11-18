# WhizKids Admin - Question Management Panel

A modern Next.js 14/15 TypeScript application for generating and managing AI-powered educational questions.

## Features

### 🎯 Context Selection
- **Cascading Dropdowns**: Grade → Subject → Topic → Concept → Skill
- **Real-time Data**: Fetches from existing Flask backend APIs
- **Intelligent Filtering**: Each selection filters the next dropdown level
- **Flexible Context**: Works with partial selections (Grade + Subject minimum)

### 🤖 AI Question Generation
- **Smart Prompts**: Builds context-aware prompts for AI generation
- **Multiple Formats**: Multiple choice, true/false, fill-in-blank, short answer
- **Batch Generation**: Generate 5-25 questions at once
- **Custom Context**: Additional instructions for AI generation

### 📋 Question Management
- **Inline Editing**: Edit generated questions before approval
- **Approval Workflow**: Approve/reject individual questions
- **Status Tracking**: Visual indicators for pending/approved/rejected
- **Batch Operations**: Save multiple approved questions at once

### 🔍 Preview System
- **Prompt Preview**: See exactly what prompt will be sent to AI
- **Collapsible Interface**: Clean, organized UI
- **Context Summary**: Clear display of selected parameters

## Tech Stack

- **Frontend**: Next.js 14/15 with App Router
- **Language**: TypeScript for type safety
- **Styling**: Tailwind CSS with custom components
- **API Client**: Axios with error handling
- **State Management**: React hooks with local state

## Project Structure

```
src/
├── app/
│   ├── admin/questions/page.tsx    # Main question management page
│   ├── layout.tsx                  # Root layout
│   └── globals.css                 # Global styles
├── components/admin/questions/
│   ├── ContextSelector.tsx         # Cascading dropdown component
│   └── SystemPromptPreview.tsx     # AI prompt preview
├── lib/
│   ├── api/
│   │   ├── client.ts              # Base API client
│   │   ├── hierarchy.ts           # Hierarchy API functions
│   │   └── questions.ts           # Question generation APIs
│   └── types/
│       ├── hierarchy.ts           # Educational hierarchy types
│       └── questions.ts           # Question data types
└── next.config.js                 # Next.js configuration
```

## API Integration

### Flask Backend Endpoints Used
- `GET /api/grades` - Load grade levels
- `GET /api/hierarchy?grade_id={id}` - Load educational hierarchy
- `POST /api/v1/questions/generate` - Generate questions
- `POST /api/v1/questions/preview` - Preview AI prompt
- `POST /api/v1/questions/validate` - Save approved questions

### Data Flow
1. **Grade Selection** → Loads subjects from hierarchy API
2. **Subject Selection** → Filters topics from hierarchy response
3. **Topic Selection** → Filters concepts from hierarchy response
4. **Concept Selection** → Filters skills from hierarchy response
5. **Generate Button** → Calls Flask API with selected context
6. **Approval Process** → Validates and saves to Firebase

## Setup Instructions

### Prerequisites
- Node.js 18+ installed
- Flask backend running on `http://localhost:5000`
- Firebase credentials configured in Flask backend

### Installation
```bash
cd whizkids-admin
npm install --legacy-peer-deps
```

### Development
```bash
npm run dev
```

The application will be available at `http://localhost:3000`

### Production Build
```bash
npm run build
npm start
```

## Usage Guide

### 1. Context Selection
1. Select **Grade** from the dropdown
2. Choose **Subject** (loads automatically after grade selection)
3. Optionally select **Topic**, **Concept**, and **Skill** for more specific questions
4. Set **Number of Questions** (5-25)
5. Choose **Question Format** (Multiple Choice, True/False, etc.)
6. Add **Additional Context** for specific instructions

### 2. Prompt Preview
- Click on "System Prompt Preview" to expand
- Review the AI prompt that will be generated
- Verify context parameters are correct

### 3. Question Generation
- Click **"Generate Questions"** button
- Wait for AI processing (may take 15-30 seconds)
- Generated questions appear in cards below

### 4. Question Review
- **Edit**: Click "Edit" button to modify question text
- **Approve**: Click ✓ to approve questions for saving
- **Reject**: Click ✗ to reject questions
- Use **tabs** to filter by status (All/Approved/Rejected)

### 5. Batch Save
- Once questions are approved, click "Save Approved Questions to Database"
- Questions are saved to Firebase through Flask API

## Features in Detail

### Cascading Dropdowns
- Uses existing Firebase data through Flask APIs
- No authentication required for grade/hierarchy endpoints
- Intelligent caching and error handling
- Progressive disclosure (only shows relevant options)

### Question Cards
- **Visual Status**: Green border for approved, red for rejected
- **Inline Editing**: Edit question text directly
- **Rich Display**: Shows options, difficulty, skill, animation
- **Batch Operations**: Select multiple for approval/rejection

### Error Handling
- Network error recovery
- API timeout handling
- User-friendly error messages
- Loading states throughout the interface

### Responsive Design
- Works on desktop and tablet devices
- Clean, professional interface
- Accessible color scheme and typography

## Configuration

### API Base URL
Edit `src/lib/api/client.ts` to change Flask backend URL:
```typescript
constructor(baseURL: string = 'http://localhost:5000')
```

### Question Generation Limits
Adjust in `src/components/admin/questions/ContextSelector.tsx`:
```typescript
<option value={5}>5</option>
<option value={10}>10</option>
<option value={15}>15</option>
<option value={20}>20</option>
<option value={25}>25</option>
```

## Next Steps

1. **Authentication**: Integrate with Flask JWT authentication system
2. **Real AI**: Replace Flask AI stub with OpenAI/Claude API
3. **Advanced UI**: Add drag-drop, keyboard shortcuts, bulk operations  
4. **Analytics**: Question generation metrics and usage tracking
5. **Export**: PDF/Word export of generated questions
6. **Templates**: Save and reuse generation contexts

This admin panel provides a complete workflow for AI-powered educational question generation, perfectly integrated with your existing Flask backend and Firebase data structure.