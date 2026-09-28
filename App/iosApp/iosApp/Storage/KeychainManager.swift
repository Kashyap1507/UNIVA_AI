//
//  KeychainManager.swift
//  Wizly
//
//  Created by Security Review on 2025-11-06.
//  SECURITY: Secure Keychain storage for sensitive data (auth tokens, credentials)
//

import Foundation
import Security

/**
 * Secure Keychain storage manager for iOS
 *
 * SECURITY:
 * - Uses iOS Keychain for hardware-backed encryption
 * - Data is encrypted at rest and protected by device passcode/biometrics
 * - kSecAttrAccessibleWhenUnlockedThisDeviceOnly ensures data is only accessible when device is unlocked
 * - Data is NOT backed up to iCloud or iTunes (ThisDeviceOnly)
 */
@objc class KeychainManager: NSObject {

    @objc static let shared = KeychainManager()

    private let service = "com.kashyap.wizlyai"

    private override init() {
        super.init()
    }

    /**
     * Save a value to Keychain
     *
     * - Parameters:
     *   - value: String value to save
     *   - key: Unique identifier for this value
     * - Returns: true if successful, false otherwise
     */
    @objc func save(_ value: String, forKey key: String) -> Bool {
        guard let data = value.data(using: .utf8) else {
            return false
        }

        // Delete existing value first (update by delete + add)
        delete(key)

        // Security: Configure Keychain attributes
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            // Security: Only accessible when device is unlocked, not backed up to cloud
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        let status = SecItemAdd(query as CFDictionary, nil)

        if status == errSecSuccess {
            return true
        } else {
            print("[Keychain] Failed to save '\(key)': \(status)")
            return false
        }
    }

    /**
     * Retrieve a value from Keychain
     *
     * - Parameter key: Unique identifier for the value
     * - Returns: The string value if found, nil otherwise
     */
    @objc func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            if status != errSecItemNotFound {
                print("[Keychain] Failed to retrieve '\(key)': \(status)")
            }
            return nil
        }

        return string
    }

    /**
     * Delete a value from Keychain
     *
     * - Parameter key: Unique identifier for the value to delete
     * - Returns: true if deleted or didn't exist, false on error
     */
    @objc func delete(_ key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        let status = SecItemDelete(query as CFDictionary)

        // Success if deleted or didn't exist
        if status == errSecSuccess || status == errSecItemNotFound {
            return true
        } else {
            print("[Keychain] Failed to delete '\(key)': \(status)")
            return false
        }
    }

    /**
     * Clear all Keychain values for this app
     *
     * - Returns: true if successful, false otherwise
     */
    @objc func clear() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service
        ]

        let status = SecItemDelete(query as CFDictionary)

        // Success if deleted or didn't exist
        if status == errSecSuccess || status == errSecItemNotFound {
            return true
        } else {
            print("[Keychain] Failed to clear all: \(status)")
            return false
        }
    }

    /**
     * Check if a key exists in Keychain
     *
     * - Parameter key: Unique identifier to check
     * - Returns: true if exists, false otherwise
     */
    @objc func exists(_ key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: false
        ]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
}
