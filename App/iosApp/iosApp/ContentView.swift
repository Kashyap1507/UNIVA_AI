import SwiftUI
@preconcurrency import shared

/// Helper to wrap KMP callbacks for Swift 6 sendability.
/// KMP closures aren't Sendable, so we wrap them in @unchecked Sendable.
private final class KMPCallbackWrapper<T>: @unchecked Sendable {
    let callback: (T) -> KotlinUnit
    init(_ callback: @escaping (T) -> KotlinUnit) {
        self.callback = callback
    }
}

struct ContentView: View {
	var body: some View {
		ComposeView()
			.ignoresSafeArea(.all) // Full screen Compose UI
	}
}

struct ComposeView: UIViewControllerRepresentable {
	func makeUIViewController(context: Context) -> UIViewController {
		// Create Google Sign-In handler that bridges to Swift implementation
		// Note: Kotlin Unit lambdas require KotlinUnit return type in Swift
		// Returns Firebase ID token for backend authentication
		let googleSignInHandler: (
			@escaping (String) -> KotlinUnit,
			@escaping (String) -> KotlinUnit
		) -> Void = { onSuccess, onError in
			print("🔵 Swift: Google Sign-In handler called")

			// Call the IOSGoogleSignInBridge
			IOSGoogleSignInBridge.shared.signInWithGoogle { firebaseToken, error in
				if let token = firebaseToken {
					print("✅ Swift: Sign-in successful, got Firebase token")
					print("   Token length: \(token.count) characters")
					_ = onSuccess(token)
				} else {
					let errorMessage = error?.localizedDescription ?? "Unknown error"
					print("❌ Swift: Sign-in failed: \(errorMessage)")
					_ = onError(errorMessage)
				}
			}
		}

		// Create Apple Sign-In handler that bridges to Swift implementation
		// Note: Kotlin Unit lambdas require KotlinUnit return type in Swift
		// Returns Firebase ID token for backend authentication
		let appleSignInHandler: (
			@escaping (String) -> KotlinUnit,
			@escaping (String) -> KotlinUnit
		) -> Void = { onSuccess, onError in
			print("🍎 Swift: Apple Sign-In handler called")

			// Wrap Kotlin callbacks in Sendable wrappers for concurrency safety
			let successWrapper = KMPCallbackWrapper(onSuccess)
			let errorWrapper = KMPCallbackWrapper(onError)

			// Call the IOSAppleSignInBridge
			IOSAppleSignInBridge.shared.signInWithApple { firebaseToken, error in
				if let token = firebaseToken {
					print("✅ Swift: Sign-in successful, got Firebase token")
					print("   Token length: \(token.count) characters")
					_ = successWrapper.callback(token)
				} else {
					let errorMessage = error?.localizedDescription ?? "Unknown error"
					print("❌ Swift: Sign-in failed: \(errorMessage)")
					_ = errorWrapper.callback(errorMessage)
				}
			}
		}

		// Create Bootstrap Sync callbacks that bridge to Swift implementation
		let bootstrapSyncCallbacks = BootstrapSyncCallbacks(
			syncBootstrapData: { bootstrapData, userId in
				print("\n🟦 ContentView: Bootstrap sync callback invoked from Kotlin")
				print("   Forwarding to IOSBootstrapSyncBridge...")
				IOSBootstrapSyncBridge.shared.syncBootstrapData(bootstrapData: bootstrapData, userId: userId)
			},
			clearSyncData: {
				print("\n🟦 ContentView: Clear sync data callback invoked from Kotlin")
				print("   Forwarding to IOSBootstrapSyncBridge...")
				IOSBootstrapSyncBridge.shared.clearSyncData()
			},
			smartSync: { force in
				print("\n🟦 ContentView: Smart sync callback invoked from Kotlin (force: \(force.boolValue))")
				print("   Forwarding to IOSBootstrapSyncBridge...")
				IOSBootstrapSyncBridge.shared.smartSync(force: force.boolValue)
			}
		)

		// Create Document Storage callbacks that bridge to Swift implementation
		let documentStorageCallbacks = DocumentStorageCallbacks(
			saveDocument: { userId, title, type, imagePaths, onSuccess, onError in
				print("🔵 Swift: Save document callback invoked")
				IOSLocalDocumentStorageBridge.shared.saveDocument(
					userId: userId,
					title: title,
					type: type,
					imagePaths: imagePaths
				) { documentId, error in
					if let id = documentId {
						_ = onSuccess(id)
					} else {
						_ = onError(KotlinThrowable(message: error?.localizedDescription ?? "Unknown error"))
					}
				}
			},
			addPage: { documentId, imagePath, onSuccess, onError in
				print("🔵 Swift: Add page callback invoked")
				IOSLocalDocumentStorageBridge.shared.addPage(documentId: documentId, imagePath: imagePath) { error in
					if error == nil {
						_ = onSuccess()
					} else {
						_ = onError(KotlinThrowable(message: error!.localizedDescription))
					}
				}
			},
			deletePage: { documentId, pageId, userId, pageNumber, onSuccess, onError in
				print("🔵 Swift: Delete page callback invoked")
				IOSLocalDocumentStorageBridge.shared.deletePage(
					documentId: documentId,
					pageId: pageId,
					userId: userId,
					pageNumber: Int(pageNumber)
				) { error in
					if error == nil {
						_ = onSuccess()
					} else {
						_ = onError(KotlinThrowable(message: error!.localizedDescription))
					}
				}
			},
			deleteDocument: { documentId, onSuccess, onError in
				print("🔵 Swift: Delete document callback invoked")
				IOSLocalDocumentStorageBridge.shared.deleteDocument(documentId: documentId) { error in
					if error == nil {
						_ = onSuccess()
					} else {
						_ = onError(KotlinThrowable(message: error!.localizedDescription))
					}
				}
			},
			getDocument: { documentId, onSuccess, onError in
				print("🔵 Swift: Get document callback invoked")
				IOSLocalDocumentStorageBridge.shared.getDocument(documentId: documentId) { document, error in
					if let doc = document {
						_ = onSuccess(doc)
					} else {
						_ = onError(KotlinThrowable(message: error?.localizedDescription ?? "Unknown error"))
					}
				}
			},
			markForSync: { documentId, onSuccess, onError in
				print("🔵 Swift: Mark for sync callback invoked")
				IOSLocalDocumentStorageBridge.shared.markForSync(documentId: documentId) { error in
					if error == nil {
						_ = onSuccess()
					} else {
						_ = onError(KotlinThrowable(message: error!.localizedDescription))
					}
				}
			},
			getStorageUsed: { completion in
				print("🔵 Swift: Get storage used callback invoked")
				IOSLocalDocumentStorageBridge.shared.getStorageUsed { storageUsed in
					_ = completion(KotlinLong(value: storageUsed))
				}
			}
		)

		// Create Document Query callbacks that bridge to Swift implementation
		let documentQueryCallbacks = DocumentQueryCallbacks(
			getDocuments: { userId, onSuccess, onError in
				print("🔵 Swift: Get documents callback invoked for user \(userId)")
				IOSLocalDocumentStorageBridge.shared.getDocuments(userId: userId) { documents, error in
					if let docs = documents {
						_ = onSuccess(docs)
					} else {
						_ = onError(KotlinThrowable(message: error?.localizedDescription ?? "Unknown error"))
					}
				}
			},
			searchDocuments: { userId, query, limit, onSuccess, onError in
				print("🔵 Swift: Search documents callback invoked for user \(userId), query: \(query)")
				IOSLocalDocumentStorageBridge.shared.searchDocuments(
					userId: userId,
					query: query,
					limit: Int(limit)
				) { documents, error in
					if let docs = documents {
						_ = onSuccess(docs)
					} else {
						_ = onError(KotlinThrowable(message: error?.localizedDescription ?? "Unknown error"))
					}
				}
			},
			getDocumentCount: { userId, onSuccess, onError in
				print("🔵 Swift: Get document count callback invoked for user \(userId)")
				IOSLocalDocumentStorageBridge.shared.getDocumentCount(userId: userId) { count in
					_ = onSuccess(KotlinInt(value: Int32(count)))
				}
			},
			getDocumentWithPages: { documentId, onSuccess, onError in
				print("🔵 Swift: Get document with pages callback invoked for document \(documentId)")
				IOSLocalDocumentStorageBridge.shared.getDocumentWithPages(documentId: documentId) { docWithPages, error in
					if let doc = docWithPages {
						_ = onSuccess(doc)
					} else {
						_ = onError(KotlinThrowable(message: error?.localizedDescription ?? "Unknown error"))
					}
				}
			}
		)

		// Create Network Detector callbacks that bridge to Swift implementation
		let networkDetectorCallbacks = NetworkDetectorCallbacks(
			isOnline: { completion in
				let isOnline = IOSNetworkDetector.shared.isOnline()
				print("🌐 Swift: Network isOnline check: \(isOnline)")
				_ = completion(KotlinBoolean(bool: isOnline))
			},
			isOnWiFi: { completion in
				let isWiFi = IOSNetworkDetector.shared.isOnWiFi()
				print("📶 Swift: Network isOnWiFi check: \(isWiFi)")
				_ = completion(KotlinBoolean(bool: isWiFi))
			},
			isOnCellular: { completion in
				let isCellular = IOSNetworkDetector.shared.isOnCellular()
				print("📱 Swift: Network isOnCellular check: \(isCellular)")
				_ = completion(KotlinBoolean(bool: isCellular))
			},
			isOnEthernet: { completion in
				// iOS doesn't distinguish Ethernet, return false
				_ = completion(KotlinBoolean(bool: false))
			},
			getNetworkType: { completion in
				let networkType = IOSNetworkDetector.shared.getNetworkType()
				print("🔍 Swift: Network type: \(networkType)")
				_ = completion(networkType)
			},
			isMeteredConnection: { completion in
				// Cellular connections are metered on iOS
				let isCellular = IOSNetworkDetector.shared.isOnCellular()
				_ = completion(KotlinBoolean(bool: isCellular))
			}
		)

		// Create Image Quality Processor callbacks that bridge to Swift implementation
		let imageQualityProcessorCallbacks = ImageQualityProcessorCallbacks(
			processImage: { inputPath, quality, outputPath, completion in
				print("📸 Swift: Process image callback invoked with quality: \(quality.displayName)")
				print("   Input: \(inputPath)")

				let result = IOSImageQualityProcessor.shared.processImageFile(
					inputPath: inputPath,
					quality: quality,
					outputPath: outputPath
				)

				// Pass Swift Result directly - Kotlin interop should handle conversion
				_ = completion(result as! Result<NSString, any Error>)
			},
			shouldProcess: { imagePath, quality, completion in
				print("📸 Swift: Should process image callback invoked")
				let should = IOSImageQualityProcessor.shared.shouldProcessFile(imagePath: imagePath, quality: quality)
				_ = completion(KotlinBoolean(bool: should))
			},
			getImageDimensions: { imagePath, completion in
				print("📸 Swift: Get image dimensions callback invoked")
				let (width, height) = IOSImageQualityProcessor.shared.getImageDimensionsFromFile(imagePath: imagePath)
				_ = completion(KotlinInt(value: Int32(width)), KotlinInt(value: Int32(height)))
			}
		)

		// Create Camera callbacks that bridge to Swift implementation
		let cameraCallbacks = CameraCallbacks(
			setupCameraPreview: { width, height in
				print("📸 Swift: Setup camera preview callback invoked (\(Int(width))x\(Int(height)))")
				return IOSCameraCallbacks.setupCameraPreview(width: CGFloat(width), height: CGFloat(height))
			},
			startPreview: {
				print("📸 Swift: Start preview callback invoked")
				IOSCameraCallbacks.startPreview()
			},
			stopPreview: {
				print("📸 Swift: Stop preview callback invoked")
				IOSCameraCallbacks.stopPreview()
			},
			capturePhoto: { onSuccess, onError in
				print("📸 Swift: Capture photo callback invoked")
				IOSCameraCallbacks.capturePhoto(
					onSuccess: { imagePath in
						print("✅ Swift: Photo captured at \(imagePath)")
						_ = onSuccess(imagePath)
					},
					onError: { errorMessage in
						print("❌ Swift: Photo capture failed: \(errorMessage)")
						_ = onError(errorMessage)
					}
				)
			},
			setFlashEnabled: { enabled in
				print("💡 Swift: Set flash enabled callback invoked: \(enabled)")
				IOSCameraCallbacks.setFlashEnabled(enabled.boolValue)
			},
			isFlashEnabled: {
				let isEnabled = IOSCameraCallbacks.isFlashEnabled()
				print("💡 Swift: Is flash enabled callback invoked: \(isEnabled)")
				return KotlinBoolean(value: isEnabled)
			},
			checkCameraPermission: { completion in
				print("🔐 Swift: Check camera permission callback invoked")
				let wrapper = KMPCallbackWrapper(completion)
				IOSCameraCallbacks.checkCameraPermission { granted in
					print("   Permission result: \(granted)")
					_ = wrapper.callback(KotlinBoolean(value: granted))
				}
			}
		)

		// Create Permission Manager callbacks that bridge to Swift implementation
		let permissionManagerCallbacks = PermissionManagerCallbacks(
			isCameraPermissionGranted: {
				let granted = IOSPermissionManagerBridge.isCameraPermissionGranted()
				print("🔐 Swift: Camera permission granted = \(granted)")
				return KotlinBoolean(value: granted)
			},
			requestCameraPermission: { onGranted, onDenied in
				print("🔐 Swift: Request camera permission callback invoked")
				IOSPermissionManagerBridge.requestCameraPermission(
					onGranted: {
						print("✅ Swift: Camera permission granted via request")
						_ = onGranted()
					},
					onDenied: {
						print("❌ Swift: Camera permission denied via request")
						_ = onDenied()
					}
				)
			},
			wasCameraPermissionDenied: {
				let wasDenied = IOSPermissionManagerBridge.wasCameraPermissionDenied()
				print("🔐 Swift: Camera permission previously denied = \(wasDenied)")
				return KotlinBoolean(value: wasDenied)
			},
			isPhotoLibraryPermissionGranted: {
				let granted = IOSPermissionManagerBridge.isPhotoLibraryPermissionGranted()
				print("🖼️ Swift: Photo library permission granted = \(granted)")
				return KotlinBoolean(value: granted)
			},
			requestPhotoLibraryPermission: { onGranted, onDenied in
				print("🖼️ Swift: Request photo library permission callback invoked")
				IOSPermissionManagerBridge.requestPhotoLibraryPermission(
					onGranted: {
						print("✅ Swift: Photo library permission granted via request")
						_ = onGranted()
					},
					onDenied: {
						print("❌ Swift: Photo library permission denied via request")
						_ = onDenied()
					}
				)
			},
			wasPhotoLibraryPermissionDenied: {
				let wasDenied = IOSPermissionManagerBridge.wasPhotoLibraryPermissionDenied()
				print("🖼️ Swift: Photo library permission previously denied = \(wasDenied)")
				return KotlinBoolean(value: wasDenied)
			},
			openAppSettings: {
				print("⚙️ Swift: Open app settings callback invoked")
				IOSPermissionManagerBridge.openAppSettings()
			}
		)

		// Create Gallery Picker callbacks that bridge to Swift PHPicker implementation
		let galleryPickerCallbacks = GalleryPickerCallbacks(
			pickImage: { onImageSelected, onCancelled in
				print("📸 Swift: Pick image callback invoked")
				IOSGalleryPickerBridge.pickImage(
					onImageSelected: { imagePath in
						print("✅ Swift: Image selected from gallery - \(imagePath)")
						_ = onImageSelected(imagePath)
					},
					onCancelled: {
						print("ℹ️ Swift: Gallery selection cancelled")
						onCancelled()
					}
				)
			},
			pickMultipleImages: { maxImages, onImagesSelected, onCancelled in
				print("📸 Swift: Pick multiple images callback invoked (max: \(maxImages))")
				IOSGalleryPickerBridge.pickMultipleImages(
					maxImages: Int(maxImages),
					onImagesSelected: { imagePaths in
						print("✅ Swift: \(imagePaths.count) images selected from gallery")
						_ = onImagesSelected(imagePaths)
					},
					onCancelled: {
						print("ℹ️ Swift: Gallery selection cancelled")
						onCancelled()
					}
				)
			},
			isAvailable: {
				let available = IOSGalleryPickerBridge.isAvailable()
				print("📸 Swift: Gallery available = \(available)")
				return KotlinBoolean(value: available)
			}
		)

		// Create EXIF Reader callbacks that bridge to Swift implementation
		let exifReaderCallbacks = ExifReaderCallbacks(
			getOrientation: { filePath in
				let orientation = IOSExifReaderBridge.getOrientation(filePath: filePath)
				print("📐 Swift: Image orientation for \(filePath) = \(orientation)")
				return KotlinInt(value: Int32(orientation))
			}
		)

		// Create Edge Detector callbacks that bridge to Swift Vision framework implementation
		let edgeDetectorCallbacks = EdgeDetectorCallbacks(
			detectEdges: { imagePath, onSuccess, onFailure in
				print("🔵 Swift: Detect edges callback invoked for: \(imagePath)")
				IOSEdgeDetectorCallbacks.detectEdges(
					imagePath: imagePath,
					onSuccess: { result in
						print("✅ Swift: Edge detection successful")
						// Pass individual values to Kotlin callback (wrapped in KotlinFloat)
						_ = onSuccess(
							KotlinFloat(value: result.topLeftX),
							KotlinFloat(value: result.topLeftY),
							KotlinFloat(value: result.topRightX),
							KotlinFloat(value: result.topRightY),
							KotlinFloat(value: result.bottomRightX),
							KotlinFloat(value: result.bottomRightY),
							KotlinFloat(value: result.bottomLeftX),
							KotlinFloat(value: result.bottomLeftY),
							result.confidence
						)
					},
					onFailure: {
						print("⚠️ Swift: Edge detection failed, triggering manual mode")
						onFailure()
					}
				)
			},
			applyPerspectiveTransform: { imagePath, tlX, tlY, trX, trY, brX, brY, blX, blY, rotation, onSuccess, onError in
				print("🔵 Swift: Apply perspective transform callback invoked")
				IOSEdgeDetectorCallbacks.applyPerspectiveTransform(
					imagePath: imagePath,
					topLeftX: CGFloat(tlX.doubleValue),
					topLeftY: CGFloat(tlY.doubleValue),
					topRightX: CGFloat(trX.doubleValue),
					topRightY: CGFloat(trY.doubleValue),
					bottomRightX: CGFloat(brX.doubleValue),
					bottomRightY: CGFloat(brY.doubleValue),
					bottomLeftX: CGFloat(blX.doubleValue),
					bottomLeftY: CGFloat(blY.doubleValue),
					rotation: Int(rotation),
					onSuccess: { outputPath in
						print("✅ Swift: Transform successful: \(outputPath)")
						_ = onSuccess(outputPath)
					},
					onError: { error in
						print("❌ Swift: Transform failed: \(error)")
						_ = onError(error)
					}
				)
			},
			getDefaultCorners: { width, height, onResult in
				print("🔵 Swift: Get default corners callback invoked for \(width)x\(height)")
				let result = IOSEdgeDetectorCallbacks.getDefaultCorners(imageWidth: Int(width), imageHeight: Int(height))
				_ = onResult(
					KotlinFloat(value: result.topLeftX),
					KotlinFloat(value: result.topLeftY),
					KotlinFloat(value: result.topRightX),
					KotlinFloat(value: result.topRightY),
					KotlinFloat(value: result.bottomRightX),
					KotlinFloat(value: result.bottomRightY),
					KotlinFloat(value: result.bottomLeftX),
					KotlinFloat(value: result.bottomLeftY)
				)
			},
			getImageSize: { imagePath, onResult in
				print("🔵 Swift: Get image size callback invoked for: \(imagePath)")
				let dimensions = IOSEdgeDetectorCallbacks.getImageSize(imagePath: imagePath)
				_ = onResult(KotlinInt(value: Int32(dimensions.width)), KotlinInt(value: Int32(dimensions.height)))
			}
		)

		// Create Calendar callbacks that bridge to Swift implementation
		let calendarManager = IOSCalendarManager()
		let calendarCallbacks = CalendarCallbacks(
			requestPermission: { completion in
				print("📅 Swift: Request calendar permission callback invoked")
				let wrapper = KMPCallbackWrapper(completion)
				calendarManager.requestCalendarAccess { granted in
					print("   Permission result: \(granted)")
					_ = wrapper.callback(KotlinBoolean(value: granted))
				}
			},
			hasPermission: { completion in
				print("📅 Swift: Has calendar permission callback invoked")
				let wrapper = KMPCallbackWrapper(completion)
				calendarManager.checkCalendarAccess { hasAccess in
					print("   Has access: \(hasAccess)")
					_ = wrapper.callback(KotlinBoolean(value: hasAccess))
				}
			},
			getDeviceCalendars: { type, completion in
				print("📅 Swift: Get device calendars callback invoked (type: \(type))")
				calendarManager.getDeviceCalendars(type) { calendars in
					print("   Found \(calendars?.count ?? 0) calendars")
					_ = completion(calendars)
				}
			},
			getEvents: { calendarIds, startDate, endDate, filterStudyRelated, completion in
				print("📅 Swift: Get events callback invoked")
				print("   Calendar IDs: \(calendarIds)")
				print("   Date range: \(startDate) to \(endDate)")
				print("   Filter study-related: \(filterStudyRelated)")
				calendarManager.getEvents(
					calendarIds: calendarIds,
					startDate: startDate,
					endDate: endDate,
					filterStudyRelated: filterStudyRelated.boolValue
				) { events in
					print("   Found \(events?.count ?? 0) events")
					_ = completion(events)
				}
			}
		)

		// Create Notification callbacks using adapter class
		let notificationCallbacks = IOSNotificationCallbacksAdapter()

		// Pass all handlers and callbacks to MainViewController
		// Course materials: document picker + offline course cache (CoreData)
		IOSDocumentPicker.register()
		CourseCache.shared.store = IOSCourseCacheStore(container: WizlyDatabase.shared.persistentContainer)

		return MainViewControllerKt.MainViewController(
			googleSignInHandler: googleSignInHandler,
			appleSignInHandler: appleSignInHandler,
			bootstrapSyncCallbacks: bootstrapSyncCallbacks,
			documentStorageCallbacks: documentStorageCallbacks,
			documentQueryCallbacks: documentQueryCallbacks,
			networkDetectorCallbacks: networkDetectorCallbacks,
			imageQualityProcessorCallbacks: imageQualityProcessorCallbacks,
			edgeDetectorCallbacks: edgeDetectorCallbacks,
			cameraCallbacks: cameraCallbacks,
			permissionManagerCallbacks: permissionManagerCallbacks,
			galleryPickerCallbacks: galleryPickerCallbacks,
			exifReaderCallbacks: exifReaderCallbacks,
			calendarCallbacks: calendarCallbacks,
			notificationCallbacks: notificationCallbacks,
			signOutHandler: {
				// Sign out of Google/Apple so the next sign-in shows the account picker
				IOSGoogleSignInBridge.shared.signOut()
				IOSAppleSignInBridge.shared.signOut()
			}
		)
	}

	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
