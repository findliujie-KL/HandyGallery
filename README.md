# Handy Gallery

**贴心相册** · An Android photo gallery built around one job: sending photos at full resolution.

Messaging and social apps usually compress the photos you send. Handy Gallery shares the original file instead, so it arrives exactly as it was taken. Its **Front Row** button puts chosen photos at the front of other apps' photo pickers, ready to send.

## Features

**Sending**
- **Share at full resolution:** sends the original photo as a file, not a compressed copy.
- **Front Row:** moves a photo to the front of the photo picker in the apps you send from. Photos you put there are collected in their own album, and can be taken out again.

**Browsing**
- **Photo wall:** every photo by month, in five sizes (pinch to change). It can be split by folder within each month, and you choose which folders appear on it.
- **Shortcut tiles:** up to three tiles above the wall for Favourites, Front Row, Screenshots or any folder. Hold and drag to reorder them.
- **Folders:** sort by recent, name or size, or arrange them yourself by dragging. Folders can be hidden.
- **Viewer:** zoom, a film strip, details, and one tap to switch between the photo's own folder and the photo wall.

**Tidying up**
- **Sort photos:** go through photos one at a time. Swipe up to reject, left to keep, or tap a folder to move the photo there.
- **Similar photos:** finds bursts and duplicate copies on your phone, without uploading anything, and suggests which to keep.
- **Compare photos:** view two or more photos stacked or side by side, with zoom kept in step, and choose which to keep.
- **Move, copy, favourite and trash** single photos or whole selections. Trashed photos show how many days they have left.

**Editing**
- Crop and rotate, draw, add text, and mosaic.
- Save over the original and revert later, or save a copy. Edits can also be handed to another editing app.

**Also**
- Built-in help with animated examples, one-time tips, and a short welcome.
- English and Simplified Chinese, light and dark themes.
- Home-screen shortcuts and links straight to Screenshots and Front Row.

## Requirements

- Android 11 (API 30) or later
- Access to photos. Optionally, "Media management" (*Skip system prompts* in Settings) lets Front Row, saving edits and trashing work without Android asking each time.

## Building

The app is written in Kotlin with Jetpack Compose and uses no third-party libraries.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. You need JDK 17 or later; the one bundled with Android Studio works.

## License

Copyright 2026 Liu Jie

Handy Gallery is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text of the GNU General Public License v3.
