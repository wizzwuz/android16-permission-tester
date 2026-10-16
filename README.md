# Android 16 Special Permissions Test App

A dedicated Android testing utility and diagnostic tool designed to test, verify, and grant all special permissions on Android 13, 14, 15, and 16 (Baklava).

## Permissions Tested

1. **Notification Read, Reply & Control**
   - NotificationListenerService (`BIND_NOTIFICATION_LISTENER_SERVICE`)
   - Notification reading (`getActiveNotifications()`)
   - Direct inline reply via `RemoteInput` actions
   - Notification dismissal and control (`cancelNotification`, `cancelAllNotifications`)
2. **Install Unknown Apps**
   - `android.permission.REQUEST_INSTALL_PACKAGES`
   - Real-time `canRequestPackageInstalls()` checking
   - Direct link to `ACTION_MANAGE_UNKNOWN_APP_SOURCES`
3. **App Usage Data (UsageStats)**
   - `android.permission.PACKAGE_USAGE_STATS`
   - Real-time AppOps status verification
   - Querying foreground app statistics over the past 24 hours
4. **Accessibility Settings**
   - Custom `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`)
   - Verification of enabled accessibility services in `Settings.Secure`
   - Triggering global actions: Back, Home, Recents, Notifications shade
5. **Write Secure Settings**
   - `android.permission.WRITE_SECURE_SETTINGS`
   - Testing read/write operations against `Settings.Secure`
6. **Restricted Settings (Android 13+)**
   - Guidance and bypass verification for sideloaded app restrictions
   - App details shortcut to access the 3-dot "Allow restricted settings" dialog
7. **1-Click Root Superuser (SU) Granter**
   - In-app 1-tap grant for rooted devices

---

## One-Line ADB Permission Grant Script

If your phone is connected via ADB, run these commands to grant all special permissions:

```bash
PKG="com.antigravity.permissiontest"

# 1. Write Secure Settings
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

# 2. App Usage Data
adb shell appops set $PKG GET_USAGE_STATS allow

# 3. Install Unknown Apps
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow

# 4. Bypass Restricted Settings (Android 13/14/15/16)
adb shell appops set $PKG ACCESS_RESTRICTED_SETTINGS allow

# 5. Enable Notification Listener Service
adb shell cmd notification set_listeners $PKG/$PKG.services.TestNotificationListenerService

# 6. Enable Accessibility Service
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.services.TestAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

## Direct Root (SU) Commands

```bash
su -c "pm grant com.antigravity.permissiontest android.permission.WRITE_SECURE_SETTINGS"
su -c "appops set com.antigravity.permissiontest GET_USAGE_STATS allow"
su -c "appops set com.antigravity.permissiontest REQUEST_INSTALL_PACKAGES allow"
su -c "appops set com.antigravity.permissiontest ACCESS_RESTRICTED_SETTINGS allow"
su -c "cmd notification set_listeners com.antigravity.permissiontest/com.antigravity.permissiontest.services.TestNotificationListenerService"
su -c "settings put secure enabled_accessibility_services com.antigravity.permissiontest/com.antigravity.permissiontest.services.TestAccessibilityService"
su -c "settings put secure accessibility_enabled 1"
```
