# UPSC 400 (offline Android app)

A fully offline study planner for the 400-day UPSC plan (Day 1 = Saturday 5 December 2026, Day 400 = Saturday 8 January 2028).
Kotlin, Jetpack Compose, local SQLite. No account, no cloud, **no internet permission**.

## Build the APK on GitHub (no Android Studio needed)

1. On github.com create a new empty repository (private is fine), for example `upsc400`.
2. Upload everything in this folder to it: on the repository page choose *Add file, Upload files*, drag the **contents** of this folder
   (including the hidden `.github` folder) and commit. If the web upload skips `.github`, create the file `.github/workflows/android.yml`
   by hand using *Add file, Create new file* and paste the workflow from this project.
3. Open the *Actions* tab. The workflow *Android CI* starts by itself. If not, choose it and press *Run workflow*.
4. Wait about 5 to 10 minutes. When it finishes, open the run and download the artifact **upsc400-debug-apk**. Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone, open it and allow *Install unknown apps* for your file manager or browser when asked.
6. If the run fails, open the failing step, copy the red error lines (or download the log) and send them to me. I will fix the code.

The workflow runs the scheduling-engine unit tests first, then builds the debug APK, then checks that the merged manifest has no INTERNET permission.

## What is inside

- `app/src/main/assets/plan.json`: the validated 400-day plan (compact form of the Phase 2 dataset).
- `app/src/main/java/.../engine/`: the scheduling logic in plain Java (day rollover, recovery queue, skim rule, backlog warnings, projection, pace recalibration).
- `app/src/test/.../EngineTest.java`: JUnit tests for that logic.
- `app/src/main/java/.../*.kt`: database, timer, notifications and the Compose screens.
- `tools/run-engine-tests.sh`: runs the engine tests locally with a Java compiler and no Gradle.

## Using the app

- **Today**: your slot cards. Pick lecture, reading or problems, press Start, Pause or Resume, then Done. Timers store timestamps, so they survive screen lock, closing the app and process death.
- **Lecture link**: each lecture has an optional link field that opens in YouTube or a browser.
- **Plan**: all 400 days; tap a day. **Progress**: per book, per course, per syllabus heading. **Recovery**: overdue slots in the order the rules require.
- **Stats**: time logs, planned versus actual, and your own pace (pages, problems, lecture minutes per hour).
- **Settings**: start date, day rollover (default 04:00), theme (dark by default), optional daily reminder, backup export and import (JSON file).
