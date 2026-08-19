# Still

A deliberately small Android Pomodoro timer: quiet when idle, precise when it matters.

Still is built for phones and tablets with the Android framework directly. There is no Compose runtime, Material dependency, analytics SDK, network client, account system, database, or background polling loop.

## What it does

- Pomodoro focus, short break, and long break phases
- Configurable durations and cycle length
- Optional automatic next phase
- Exact phase-end scheduling that survives Doze
- System countdown notification with pause, resume, skip, and stop actions
- Distinct focus-complete and break-complete sounds
- Optional completion vibration
- Reboot recovery for an active timer
- **Float**: a draggable edge-snapping overlay with countdown and compact controls
- **Low power**: immersive black clock mode, very low screen brightness, no idle animation, and subtle OLED pixel drift
- Responsive layout capped to a comfortable tablet width

## Efficiency model

The core timer is timestamp-based. Starting a session stores its end time and schedules one Android alarm for that boundary. Still does **not** keep a service, thread, worker, or one-second background loop alive just to count down.

The visible app updates once per second while on-screen. The system notification renders its own countdown. The overlay is the only feature that intentionally keeps a foreground service alive, and only while the user has explicitly enabled the overlay.

## Android

- Minimum: Android 8.0 / API 26
- Target: Android 16 / API 36
- Java 17
- Android Gradle Plugin 9.3
- Gradle 9.5

## Builds

Everything can be built in GitHub Actions; a local Android development environment is not required.

- `Build` runs lint and produces a debug APK on pushes and pull requests.
- `Release APK` produces a signed, R8-minified release APK.
- Pushing a `v*` tag also publishes the signed APK as a GitHub Release.

Release signing material is stored only in GitHub Actions secrets. It is intentionally absent from the public repository.

## Permissions

Still asks only for capabilities tied to visible features:

- notifications — active countdown and completion alert
- exact alarms — precise Pomodoro completion
- vibration — optional completion haptic
- boot completed — restore an active timer after reboot
- draw over other apps — only for Float
- foreground service — only while Float is visible

There is no internet permission.
