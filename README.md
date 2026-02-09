# Call Browser

A Kotlin Android app for browsing call logs with advanced filtering options.

## Features

- **Filter by Call Type:**
  - All Calls
  - Incoming
  - Outgoing
  - Missed

- **Filter by Contact Status:**
  - All Contacts
  - Saved Contacts only
  - Unsaved Numbers only

- **Call Details Display:**
  - Contact name (if saved) or phone number
  - Call type with color coding
  - Date and time
  - Call duration
  - Contact status indicator

## Permissions Required

- `READ_CALL_LOG` - To access call history
- `READ_CONTACTS` - To determine if a number is saved in contacts

## Setup

1. Open the project in Android Studio
2. Sync Gradle files
3. Run on an Android device or emulator
4. Grant the requested permissions when prompted

## Project Structure

```
CallBrowser/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/callbrowser/
│   │   │   ├── MainActivity.kt        # Main activity with filters
│   │   │   ├── CallLogAdapter.kt      # RecyclerView adapter
│   │   │   └── CallLogEntry.kt        # Data model
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml  # Main layout
│   │   │   │   └── item_call_log.xml  # Call item layout
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   ├── arrays.xml         # Spinner options
│   │   │   │   └── themes.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## Color Coding

- **Green** - Incoming calls
- **Blue** - Outgoing calls
- **Red** - Missed calls

## Requirements

- Android SDK 24+
- Kotlin 1.9+
- Android Studio Hedgehog (2023.1.1) or newer