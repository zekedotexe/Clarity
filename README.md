# Clarity

---

## Project Overview

Made for the natHacks 2026 event for the Standard Division under the topic of Healthcare and Accessibility. Our project tackles the problem of dyslexic individuals struggling with the cognitive load of reading, where they often become mentally tired or frustrated while reading. We also wanted to provide a solution for neurological therapists by providing data of specific struggles/patterns their patient may have. Hence, allowing the therapist to have highly-targeted practice with their patients. Here is a [presentation](https://docs.google.com/presentation/d/1pboTwElwYRLjrwfVI6u4OSQYpQCSas1VTP28yePDdrw/edit?slide=id.h69fbb7543530192b_13_396#slide=id.h69fbb7543530192b_13_396) made for this. Made by Zeke Elegino, William Bautista, Gerard Ventura, Denis Zhang. 

## Research and Approach

After researching statistics and proper techniques, we discovered that sound-based learning does help individuals with dyslexia; however, it is much more effective when combined with a visual technique. For our final product, the two approaches we chose were sounding out the word and reducing visual crowding when reading.

## Audience and Value

Despite having no user data, many other companies have had similar techniques and approaches to ours, showing that our project has an audience and value. For example, the C-Pen, a pen that reads out loud any printed text.

## Tech Stack

- Tested using a Samsung S24.
- Hand-tracking done with MediaPipe
- Word Recognition with Google ML kit
- CameraX for converting the camera input to bitmaps for the word recognition and hand-tracking.

## To Build and Run

### Prerequisites
- Android Studio 2026.2.1 or newer
- Java 11+ JDK
- Android SDK (Compile SDK 37, Min SDK 34)
- ClarityApp directory.

### Quick Start (Command Line)

```bash
# Build debug APK
./gradlew assembleDebug

# Build and install on connected device/emulator
./gradlew installDebug

# Clean project
./gradlew clean build
