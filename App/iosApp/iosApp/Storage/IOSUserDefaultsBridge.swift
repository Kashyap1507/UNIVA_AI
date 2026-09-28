import Foundation
import shared

/**
 * UX-017: iOS UserDefaults Bridge
 *
 * Provides access to iOS NSUserDefaults from Kotlin code
 * for persistent storage of offline queue operations.
 */

/// Get string value from UserDefaults
@_cdecl("getUserDefaultsString")
func getUserDefaultsString(key: UnsafePointer<CChar>) -> UnsafePointer<CChar>? {
    let keyString = String(cString: key)

    guard let value = UserDefaults.standard.string(forKey: keyString) else {
        return nil
    }

    // Convert to C string (caller must free)
    let cString = strdup(value)
    return UnsafePointer(cString)
}

/// Set string value in UserDefaults
@_cdecl("setUserDefaultsString")
func setUserDefaultsString(key: UnsafePointer<CChar>, value: UnsafePointer<CChar>) {
    let keyString = String(cString: key)
    let valueString = String(cString: value)

    UserDefaults.standard.set(valueString, forKey: keyString)
    UserDefaults.standard.synchronize()
}

/// Remove value from UserDefaults
@_cdecl("removeUserDefaultsValue")
func removeUserDefaultsValue(key: UnsafePointer<CChar>) {
    let keyString = String(cString: key)

    UserDefaults.standard.removeObject(forKey: keyString)
    UserDefaults.standard.synchronize()
}
