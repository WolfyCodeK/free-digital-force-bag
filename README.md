# Free Digital Force Bag

An open-source Android app for a "force" trick. It installs as **Keep Notes** and looks and
behaves like Google Keep. While you casually swipe between pages on your phone's real home
screen, it reads a secret number. The "Celebrities" note in the app then has your forced name on
exactly that line.

## How it works

1. A small accessibility service ("Keep Notes") notices where each touch starts on the home
   screen. It never changes what you see: every touch goes straight to your launcher, untouched.
2. Where each page swipe **starts** picks a digit from an invisible phone keypad covering the
   whole screen:

   ```
   1 2 3
   4 5 6
   7 8 9
   0 0 0   (the whole bottom quarter is 0)
   ```

   Two swipes make a number from 1 to 100. Single-digit numbers use a leading zero (7 is `0`
   then `7`) and 100 is `0` then `0`. A swipe only counts if the home screen really moves to
   another page (the service compares what the home screen shows before and after), and every
   counted swipe gives one short vibration. Taps that open an app, long presses and pulling
   down the notification shade don't count.
3. Open Keep Notes and open the **Celebrities** note. It is a numbered list of 100 names, and
   the forced name (line 1 of `celebrities.txt`, "Tom Hanks" by default) is on the line with the
   spectator's number.

## Setup on the phone

Needs Android 13 or newer.

1. **Install** the APK from the repository's **Releases** page ("Latest APK"). Every build is
   signed with the same key, so new versions install over old ones and keep your notes.
2. **Allow restricted settings** (Android 13+). Android blocks accessibility for apps installed
   from outside an app store until you allow it:
   Settings > Apps > Keep Notes > tap the three-dot menu (top right) > **Allow restricted
   settings**. If you don't see that option, first try step 3 once: Android only offers it after
   it has blocked the setting.
3. **Turn on the service**: Settings > Accessibility > Installed apps (Samsung: Installed apps or
   Downloaded apps) > **Keep Notes** > On, and confirm. Android warns that the app can view and
   control the screen; that is the standard wording for every accessibility service. Keep Notes
   only looks at where a touch starts and whether the home screen changed page, and never
   sends anything anywhere.
4. **Samsung only, recommended**: Settings > Battery > Background usage limits > Never sleeping
   apps > add Keep Notes. If Android force-stops the app, it also switches the accessibility
   service off and you would have to turn it on again.
5. Put Keep Notes on your home screen, ideally on its own page, and make sure the home screen
   has at least two pages to swipe between.
6. **Practise**: swipe between pages and feel for the buzz after each swipe. No buzz means the
   swipe didn't count (see "If the home-screen swipes don't work" below).

Updating from a version before November 2026: the service was renamed, so turn it on again
once (step 3).

## Performing

1. Have the spectator name a number from 1 to 100 (or force one).
2. Go to the home screen and swipe between pages twice, starting each swipe on the right keypad
   cell. Feel two buzzes. Swipe left or right, whichever way your pages go.
3. Open Keep Notes from the home screen and open **Celebrities**. The forced name is on their
   line. Scroll down slowly while you talk.

Rules worth knowing:

- A number is used if you open the note within **30 minutes** of entering it.
- After the Celebrities note has been opened, new numbers are ignored for **3 minutes**, so a
  spectator swiping around the home screen afterwards can't move the name. To perform again
  sooner, wait out the 3 minutes.
- A lone first digit is forgotten after 45 seconds, so a stray swipe can't spoil the next number.
- After entering the number, open Keep Notes directly. Don't open the app drawer and scroll
  through it, because scrolling inside the launcher can count as more swipes.
- With no number entered, the forced name sits on a random line, and stays there like any
  normal note.

### How the Celebrities note behaves if someone edits it

It behaves like a normal note while the app is open: a spectator can edit, colour, label, pin,
archive or delete it, and those changes stay while they look around. As soon as the app is left
(Home, Recents or another app), the note is quietly put back exactly as it was: same title, the
clean list for the current number, back in Notes with the default colour, no labels or reminder.
So nothing a spectator does can break the next performance. A copy made with "Make a copy" is an
ordinary note and is kept; delete it yourself if you don't want it.

Leaving the app also closes any open note and returns to the main notes list, so the app always
opens in a normal state.

### If the home-screen swipes don't work on your phone

The service doesn't depend on any particular launcher: it notes where the finger went down and
then checks whether the home screen now shows a different page. This has been tested on the
Pixel launcher of the Android 14 and Android 16 emulators (see Development). It has **not** been
tested on a real phone, on Samsung One UI Home or on other launchers. If you never feel the buzz:

- Turn the Keep Notes accessibility service **off**. The app then switches to an in-app fallback:
  open Keep Notes and make **two vertical swipes on the notes list** (as if scrolling), each
  starting on the keypad cell for the digit. The same keypad covers the whole screen. Each
  counted swipe gives a light tick. Then open Celebrities.

## Your profile picture and account

- Profile picture: put a square-ish photo at `app/src/main/res/drawable-nodpi/profile.jpg` (or
  `.png`/`.webp`). The app crops it to a circle inside Google's blue ring. Without one, the
  avatar shows the first letter of your name.
- Name and email shown when the picture is tapped: edit `app/src/main/assets/account.txt`
  (line 1 name, line 2 email).

## Changing the names

Edit `app/src/main/assets/celebrities.txt`: line 1 is the forced name and the other 99 lines are
the fillers. This is the only file to edit. Pushing rebuilds the APK.

Haptics and timings are constants at the top of `GestureService.java` and `Force.java`.

## What works in the app

Notes in a grid or list, sorting (custom, date created, date modified), search with type, label
and colour filters, the navigation drawer (Notes, Reminders, labels, Create new label, Archive,
Deleted, Settings, Help & feedback), long-press multi-select (pin, reminder, colour, labels,
archive, delete, make a copy, send), drag to reorder, swipe to archive with undo, note colours,
labels, reminders with notifications, archive, Deleted with restore (emptied after 7 days),
checklists, text formatting, images (camera or gallery), drawings, voice recordings, undo/redo,
and the new-note menu. Everything is stored on the phone and the app never uses the network.
Things that need the internet behave like Keep does offline: the Live button, sync
(pull to refresh) and Help & feedback.

## Development

There is no need for a local Android SDK: every push is built by GitHub Actions
(`.github/workflows/build.yml`), which publishes the APK to the "latest" release and then runs the
app on Android 14 and Android 16 emulators (`ci/drive.py`). That job takes screenshots of every
screen and runs an end-to-end test: it enables the accessibility service, swipes from the "6"
and "1" keypad cells on a two-page Pixel home screen, opens the app by tapping its icon and checks
that line 61 of Celebrities is the forced name. Screenshots are uploaded as the
`screenshots-apiNN` artifact and also kept under the git ref `refs/ci/<branch>/apiNN`
(`git fetch origin refs/ci/<branch>/api34`).

Fonts are Google Sans (SIL Open Font License) and icons are Material Symbols (Apache 2.0); see
`third_party/`.
