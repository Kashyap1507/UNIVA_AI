# iOS Delete Homework Implementation Guide

## Overview
This guide provides the Swift code needed to complete the delete homework feature on iOS.

The Kotlin side is complete. You need to:
1. Add `deleteDocument` callback to CorrectionsCallbacks initialization in ContentView.swift
2. Add `deleteDocument` method to IOSCorrectionsRepositoryBridge.swift

---

## Step 1: Update ContentView.swift

**File**: `iosApp/iosApp/ContentView.swift`

**Location**: Find where `let correctionsCallbacks = CorrectionsCallbacks(` is defined

**Add this callback** after the existing callbacks (around where `getCorrectionDetail` is):

```swift
deleteDocument: { documentId, onResult in
    print("🗑️ Swift: Delete document callback invoked for '\(documentId)'")

    Task {
        do {
            // 1. Delete from Core Data (instant UI update)
            try await CoreDataManager.shared.deleteDocument(documentId: documentId)
            print("   ✅ Document deleted from Core Data")

            // 2. Delete from Firestore in background
            Task.detached {
                do {
                    try await FirestoreService.shared.deleteDocument(documentId: documentId)
                    print("   ✅ Document deleted from Firestore")
                } catch {
                    print("   ⚠️ Failed to delete from Firestore: \(error)")
                    // Don't fail - local delete already succeeded
                }
            }

            // Return success
            _ = onResult(true, nil)
        } catch {
            print("   ❌ Failed to delete document: \(error)")
            _ = onResult(false, error.localizedDescription)
        }
    }
},
```

---

## Step 2: Add deleteDocument to CorrectionsCallbacks Protocol

**File**: `shared/build/generated/iosMain/Shared.framework/Headers/Shared.h` (auto-generated)

The CorrectionsCallbacks class should automatically include the new `deleteDocument` parameter after you add it to ContentView.swift and rebuild.

If you get a compile error about missing parameter, the protocol is defined in the auto-generated Kotlin/Native bridge. Simply adding the callback in ContentView.swift should work.

---

## Step 3: Add deleteDocument to CoreDataManager.swift (if not exists)

**File**: `iosApp/iosApp/Database/CoreDataManager.swift`

**Add this method**:

```swift
/// Delete a document from Core Data
func deleteDocument(documentId: String) async throws {
    let context = container.viewContext

    return try await context.perform {
        // Fetch the document
        let fetchRequest: NSFetchRequest<NSFetchRequestResult> = NSFetchRequest(entityName: "Document")
        fetchRequest.predicate = NSPredicate(format: "id == %@", documentId)
        fetchRequest.fetchLimit = 1

        guard let results = try? context.fetch(fetchRequest),
              let document = results.first as? NSManagedObject else {
            print("⚠️ Document not found in Core Data: \(documentId)")
            throw NSError(domain: "CoreData", code: 404, userInfo: [NSLocalizedDescriptionKey: "Document not found"])
        }

        // Delete the document
        context.delete(document)

        // Save changes
        try context.save()
        print("✅ Document deleted from Core Data: \(documentId)")
    }
}
```

---

## Step 4: Add deleteDocument to FirestoreService.swift (if not exists)

**File**: `iosApp/iosApp/Services/FirestoreService.swift`

**Add this method**:

```swift
/// Delete a document from Firestore via backend API
func deleteDocument(documentId: String) async throws {
    guard let token = UserDefaults.standard.string(forKey: "auth_token") else {
        throw NSError(domain: "Auth", code: 401, userInfo: [NSLocalizedDescriptionKey: "No auth token"])
    }

    let baseUrl = NetworkConstants.getApiUrl()
    guard let url = URL(string: "\(baseUrl)/api/documents/\(documentId)") else {
        throw NSError(domain: "Network", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
    }

    var request = URLRequest(url: url)
    request.httpMethod = "DELETE"
    request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")

    let (data, response) = try await URLSession.shared.data(for: request)

    guard let httpResponse = response as? HTTPURLResponse else {
        throw NSError(domain: "Network", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
    }

    guard httpResponse.statusCode == 200 else {
        let errorMessage = String(data: data, encoding: .utf8) ?? "Unknown error"
        throw NSError(domain: "API", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: errorMessage])
    }

    // Parse response to verify success
    if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
       let success = json["success"] as? Bool,
       success {
        print("✅ Document deleted from Firestore via API: \(documentId)")
    } else {
        throw NSError(domain: "API", code: 500, userInfo: [NSLocalizedDescriptionKey: "Delete failed"])
    }
}
```

---

## Step 5: Build and Test

### Build Steps:
```bash
cd /Users/kashyap15/Documents/Projects/Univa_AI/UNIVA_AI/Wizly-App

# 1. Build shared framework
./gradlew :shared:linkDebugFrameworkIosArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Open in Xcode
open iosApp/iosApp.xcodeproj

# 3. Build from Xcode
# Product → Build (⌘B)
```

### Test Flow:
1. Open homework review (CorrectionCardScreen)
2. Tap delete button (dustbin icon) in top-right corner
3. Confirm deletion in dialog
4. Verify:
   - Screen navigates back to AllCorrections
   - Deleted homework disappears from list
   - iOS logs show Core Data deletion
   - Backend logs show Firestore deletion
   - FCM notification sent
   - Other devices update automatically

---

## Expected Console Output (Success):

```
📝 iOS: Deleting correction document 'doc_123'
🗑️ IOSCorrectionsBridge: Deleting document 'doc_123'
🗑️ Swift: Delete document callback invoked for 'doc_123'
   ✅ Document deleted from Core Data
   ✅ Document deleted from Firestore
   ✅ Correction deleted successfully
```

---

## Troubleshooting

### Compile Error: "Missing deleteDocument parameter"
- Make sure you added the callback to CorrectionsCallbacks initialization
- Rebuild the shared framework: `./gradlew :shared:linkDebugFrameworkIosArm64`
- Clean build in Xcode: Product → Clean Build Folder

### Runtime Error: "Corrections callbacks not set"
- Make sure IOSCallbackBridge.setCorrectionsCallbacks() is called in ContentView.swift
- Check that the app properly initialized before navigating to corrections

### Delete Succeeds Locally But Not on Server:
- Check auth token is valid
- Check network connectivity
- Check backend logs for API errors
- FCM notification should still work even if API fails

---

## Integration with Existing Code

The delete functionality integrates with existing FCM notification handling:

**File**: `RealTimeDataSyncManager.kt` (lines 123-126)
```kotlin
"homework_deleted" -> {
    println("🗑️ Homework deleted - refreshing documents")
    refreshDocuments(childId)
}
```

This automatically:
- Invalidates cache for `documents_$childId`
- Triggers UI refresh on ALL devices (Android + iOS)
- Works bi-directionally (Android deletes → iOS updates, iOS deletes → Android updates)

---

## Files Modified (Complete Implementation)

### Kotlin (✅ Complete):
1. `shared/src/commonMain/kotlin/com/kashyap/wizlyai/ui/screens/homework/CorrectionCardScreen.kt` - Delete button UI
2. `shared/src/androidMain/kotlin/com/kashyap/wizlyai/ui/viewmodel/CorrectionsViewModel.kt` - Android delete method
3. `shared/src/androidMain/kotlin/com/kashyap/wizlyai/data/repository/CorrectionsRepository.kt` - Android repository
4. `shared/src/commonMain/kotlin/com/kashyap/wizlyai/network/ApiClient.kt` - DELETE API
5. `shared/src/commonMain/kotlin/com/kashyap/wizlyai/ui/WizlyApp.kt` - Wire callback
6. `shared/src/iosMain/kotlin/com/kashyap/wizlyai/ui/viewmodel/CorrectionsViewModel.ios.kt` - iOS delete method
7. `shared/src/iosMain/kotlin/com/kashyap/wizlyai/ui/viewmodel/IOSCorrectionsBridge.ios.kt` - iOS bridge

### Backend (✅ Complete):
8. `Wizly-Server/app/services/document_service.py` - FCM notification on delete

### Swift (⏳ To Do):
9. `iosApp/iosApp/ContentView.swift` - Add deleteDocument callback
10. `iosApp/iosApp/Database/CoreDataManager.swift` - Add deleteDocument method (if not exists)
11. `iosApp/iosApp/Services/FirestoreService.swift` - Add deleteDocument method (if not exists)

---

## Summary

All Kotlin and backend changes are complete. You only need to add 3 Swift methods to complete the iOS implementation. The feature will then work identically on both Android and iOS with real-time cross-device sync via FCM.
