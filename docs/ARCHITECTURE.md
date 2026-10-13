# Architecture

CarTube shows a YouTube page on the Android Auto head unit. The phone is only the remote: it accepts a URL or a search, and the car screen is the player. The app is sideloaded. It is not a Play Store navigation app, and it does not guide a route.

Playback is for a parked car or a passenger. The phone UI, the car toast, and the README all say not to watch or operate it while driving.

## Why this shape

Android Auto does not let an ordinary activity draw pixels on the head unit. Projected apps go through the Car App Library (`androidx.car.app:app:1.7.0`). Most templates are fixed lists and messages. The templates that expose a `Surface` are the map templates, and `NavigationTemplate` is the one that has been stable since early hosts.

Google's own [Draw maps](https://developer.android.com/training/cars/apps/library/draw-maps) sample is the path this app follows:

1. Declare a `CarAppService` with category `androidx.car.app.category.NAVIGATION`.
2. Return a `NavigationTemplate`.
3. Register a `SurfaceCallback` on `AppManager`.
4. When `onSurfaceAvailable` provides a `Surface`, create a `VirtualDisplay` whose output is that surface.
5. Show a `Presentation` on the virtual display. The presentation's content is a `WebView`.

The older Desktop Head Unit SDK (`com.google.android.apps.auto.sdk`) is not used. Current Android Auto hosts do not run it.

`MapWithContentTemplate` also has a surface, but it needs Car API 7 and spends part of the screen on a list. `NavigationTemplate` needs only the surface plus small action strips, and tap handling (`SurfaceCallback.onClick`) has existed since Car API 5. The manifest sets `androidx.car.app.minCarApiLevel` to 5. A Xiaomi 14 Ultra in 2026 runs a much newer Android Auto host than that.

Media browse templates are the wrong tool. They expose play/pause rows, not a video surface.

```mermaid
sequenceDiagram
    participant Phone as Phone UI
    participant Store as PlaybackStore
    participant Host as Android Auto host
    participant Screen as CarTubeScreen
    participant Web as WebView on virtual display

    Phone->>Store: open(url)
    Host->>Screen: bind CarAppService, show NavigationTemplate
    Screen->>Host: SurfaceCallback
    Host->>Screen: onSurfaceAvailable(surface)
    Screen->>Web: VirtualDisplay + Presentation
    Store-->>Web: load embed or m.youtube.com
    Host->>Screen: onClick / onScroll / onScale
    Screen->>Web: MotionEvent
```

## What is declared, and what is deliberately not started

`AndroidManifest.xml` requests `androidx.car.app.NAVIGATION_TEMPLATES` and `androidx.car.app.ACCESS_SURFACE`, and `res/xml/automotive_app_desc.xml` says the app uses the template host. That is what makes the head unit list the app after the user enables unknown sources.

`NavigationManager.navigationStarted()` is never called. That call tells the car a route is active. It can take the navigation slot and push the surface toward the instrument cluster. CarTube is not a navigator, so it only occupies the main template surface while the user has the app open. There is no trip, no guidance, and no cluster content.

`HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` is intentional. A Play app would pin the Android Auto host signature. This build is sideloaded and also has to accept the Desktop Head Unit. Unknown sources is already an explicit switch in Android Auto's developer settings. The service is documented as exported because the projection host has to bind it.

## Car surface

`CarTubeScreen` installs the surface callback before the first template is returned. The template has:

- An action strip with Home and Reload (both have an icon and a short title, which `ACTIONS_CONSTRAINTS_NAVIGATION` allows).
- A map action strip containing `Action.PAN` plus zoom in and zoom out. The host only forwards `SurfaceCallback` gestures when the PAN action is present. Zoom buttons cover head units that never emit pinch events. Map actions are icon-only.
- A pan-mode listener, so the host can enter the mode that turns a rotary or touchpad into scroll and scale.

`CarSurfaceRenderer.onSurfaceAvailable` reads width, height, and dpi from the `SurfaceContainer`. It creates the virtual display with flags `0`, the same value as the platform sample. The display is private to this process. The renderer skips the work when the same surface and size are delivered twice, and it drops the presentation before releasing the display so the WebView is not left attached to a dead window.

The `Presentation` uses the application context and a black fullscreen theme. Its content is a `FrameLayout` with one `WebView`, created from the presentation context so the view is display-aware. `onSurfaceDestroyed` dismisses the presentation, destroys the WebView, then releases the virtual display. That release is what returns the host surface.

## YouTube in the WebView

A video id (watch, youtu.be, Shorts, live, music.youtube.com, or a bare 11-character id) becomes:

`https://www.youtube.com/embed/<id>?autoplay=1&playsinline=1&...`

The load sends `Referer: https://www.youtube.com/`. Without that header, the embed endpoint often returns player error 153. The WebView user agent is the system one with `; wv` and `Version/4.0` removed, because YouTube treats those tokens as an embedded WebView and refuses the player. The Chrome version from the device is kept.

Other YouTube URLs (a channel, a playlist, the home page) are loaded as themselves, upgraded to https. A phrase that is not a URL becomes `https://m.youtube.com/results?search_query=...`, which is a touch-oriented page. `m.youtube.com/` is the idle page.

`shouldOverrideUrlLoading` allows main-frame navigations only to YouTube hosts and `https://accounts.google.com` (sign-in). `javascript:`, `intent:`, and other sites are dropped. Video bytes from `googlevideo.com` are subresource requests, not navigations, so they are not blocked here.

WebView settings that matter for playback:

- JavaScript and DOM storage on
- Third-party cookies on (the embed player needs them)
- `mediaPlaybackRequiresUserGesture = false` so autoplay can start
- File and content URL access off
- JS dialogs cancelled, because a `Presentation` has no activity window to host them
- HTML5 fullscreen is reparented into the same frame instead of asking an activity for a new window

If the renderer process dies, the callback returns true (so the app is not killed) and tries to rebuild the page twice. After that it asks the user to reopen the app.

## Touch

The host does not deliver a `MotionEvent` stream. `SurfaceCallback` reports:

| Callback | Car API | What CarTube does |
| --- | --- | --- |
| `onClick(x, y)` | 5 | Down and up at that point |
| `onScroll(dx, dy)` | 2 | A drag. Positive X is to the right, positive Y is down |
| `onFling` | 2 | Lifts the drag if one is active, otherwise `WebView.flingScroll` |
| `onScale` | 2 | `WebView.zoomBy` |

Coordinates are surface pixels. `SurfacePoints` scales them when the WebView's measured size differs from the surface, and clamps them inside the view. Events are built with `TOOL_TYPE_FINGER` and `SOURCE_TOUCHSCREEN`; a bare `MotionEvent` is often ignored by WebView.

On some head units, scroll and scale arrive only after the driver presses PAN. Taps via `onClick` are separate, and the platform notes that some cars never call `onClick`. The zoom buttons exist for that case. The car back button walks WebView history before it leaves the app.

## Phone UI and process lifetime

`MainActivity` is not on the car display. It edits the URL, shows whether `CarConnection` reports a projection, and warns on first launch. `PlaybackStore` is a `StateFlow` plus `SharedPreferences`. The car screen collects it and calls `WebView.loadUrl`. The same URL can be sent again because each open bumps a generation counter.

Android Auto binds `CarTubeService` in the app process (no `android:process`). The phone activity and the car session therefore share the store. A foreground service of type `mediaPlayback` is started when the car session starts or when the user sends a URL. It holds a partial wake lock (capped at four hours), requests media audio focus, and publishes a `MediaSession` in the playing state. targetSdk is 35, and a media playback foreground service on that target is expected to have a real session. The service is stopped when the car session is destroyed. Swiping the phone task away does not remove the car surface by itself; HyperOS battery limits still can, which is why the README and the phone screen point at autostart and unrestricted battery.

Starting a foreground service from the car session can throw `ForegroundServiceStartNotAllowedException` if the phone process is considered background. That failure is logged and swallowed. The host's binding still keeps the process while the template is visible. The phone button is the reliable place to start the service, because the activity is in the foreground.

## Signing and CI

No keystore is in the tree. `.gitignore` rejects `*.p12`, `*.jks`, and `*.keystore`.

`.github/workflows/android.yml`:

- On pull requests: JDK 17, Android SDK platform 35 and build-tools 35.0.0, `testDebugUnitTest assembleDebug`.
- On tags `v*`: the same test task, then `assembleRelease` with `REQUIRE_SIGNING=true`. `KEYSTORE_BASE64` is decoded to a temporary PKCS12 file. `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` come from repository secrets. The APK is uploaded to GitHub Releases as `car-tube-<version>.apk`, where `<version>` is the tag without the leading `v`.

Debug builds are signed with the automatic debug key. A local `assembleRelease` without the environment variables produces an unsigned APK and does not fail the signing check.

## Trying it on the Desktop Head Unit

1. Install the debug APK and the Android Auto app.
2. Enable Android Auto developer mode (tap the version ten times) and unknown sources.
3. Start the Desktop Head Unit (`desktop-head-unit` from the SDK extras, or Android Studio's DHU) and connect the phone.
4. Open CarTube on the head unit, then send a URL from the phone.

CI does not boot an emulator or a head unit. It compiles, runs the JVM tests (URL policy, touch mapping, user-agent cleanup), and builds the APK.

## Limits

- Widevine video that requires a secure decoder is black on a normal virtual display. Standard YouTube embeds often still play; some titles will be audio-only or black. The app does not extract stream URLs or bypass that.
- The host draws its own action strips over the edges of the surface. That chrome is not part of the WebView.
- Gesture coverage depends on the head unit. PAN is required for drags on many systems.
- YouTube can change the embed page and break WebView playback without an app update.
- The navigation category is a platform constraint, not a claim that this app navigates. Do not use it while driving.
