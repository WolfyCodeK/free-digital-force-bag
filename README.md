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
   counted swipe buzzes (a double buzz when the second digit completes the number). Taps that
   open an app, long presses and pulling down the notification shade don't count.
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
   swipe didn't count (see "When a swipe counts" below). To check you hit the cells you meant,
   make two swipes, open Keep Notes, type `*#*#` in the search bar and look at **Number**.

**Updating**: install the new APK over the old one. Afterwards check that Settings >
Accessibility > Keep Notes is still On. If you installed an early build, the service has since
been renamed and has to be turned on again once (step 3).

## Performing, step by step

1. **Before the show**: open Keep Notes, tap the search bar and type `*#*#` to open the hidden
   settings (see below). Check that **Swipe service** says *On*, tap **Reset now**, then press
   Home. Make sure you are on a home-screen page that has another page next to it.
2. The spectator thinks of (or names) a number from 1 to 100.
3. On the home screen, make **exactly two page swipes**. Start the first swipe on the keypad cell
   for the **tens** digit and the second on the cell for the **units** digit (7 is `0` then `7`,
   100 is `0` then `0`). Where your finger **starts** picks the digit; after that it just has to
   be an ordinary left or right swipe that turns the page (left or right doesn't matter).
   You feel **one short buzz** after the first swipe and a **double buzz** after the second: the
   number is in.
4. Tap the Keep Notes icon and open **Celebrities**. The forced name is on their line. Scroll
   down slowly while you talk.
5. You can hand the phone over. For 3 minutes after the note is opened, swipes on the home screen
   don't change anything, and as soon as anyone leaves the app the note goes back to its clean
   state.

### When a swipe counts, and why sometimes it doesn't

A swipe counts as a digit (and buzzes) only if **all** of these are true:

- The Keep Notes accessibility service is on. Android switches it off if the app is force-stopped,
  which is why the Samsung "Never sleeping apps" step matters.
- The **home screen** is what you see: not the app drawer, Recents, the notification shade, the
  lock screen or another app. The service only listens while the home screen is showing, and
  switches on a fraction of a second after it appears, so pause for a moment after pressing Home
  before the first swipe. Everywhere else it is completely off, so other apps behave normally
  (no screen-reader effects such as focus boxes in Recents).
- The home screen **really moves to another page**. Swiping left on the first page or right on
  the last page bounces back and does **not** count. This is the most common reason a swipe
  "doesn't work": keep a page on both sides of the one you start on, or always swipe towards a
  page that exists.
- The swipe doesn't start right at the edge of the screen. With gesture navigation, a swipe from
  the left or right edge is **Back** and one from the bottom edge is **Home** or switches apps,
  so the page doesn't turn and nothing counts. Start about a thumb's width in from the edges.
  This matters most for 1, 4 and 7 (left edge), 3, 6 and 9 (right edge) and 0 (bottom).
- The swipe doesn't start on a widget that scrolls by itself (a calendar or photo widget, for
  example). The widget moves instead of the page, so it doesn't count.
- Your finger lifts within 3 seconds and nothing opens: tapping an icon or long-pressing (which
  opens a menu) never counts.
- It is not within the lock time after the Celebrities note was opened (3 minutes by default).

How the digits pair up:

- The first counted swipe is the tens digit. The next counted swipe within **45 seconds**
  completes the number (double buzz).
- If no second swipe comes within 45 seconds, the first digit is forgotten and the next swipe
  starts again as a tens digit.
- Once a number is complete, the next swipe starts a **new** number. The old number stays in
  place until the new one is complete, so one stray swipe can't spoil it, but two stray swipes
  will replace it.
- If you lose track, wait 45 seconds (or use **Reset now**) and enter the number again.

### How it resets

- **Leaving the app** (Home, Recents or another app) puts the Celebrities note back to its clean
  state, closes any open note and returns the app to the plain notes list.
- The forced name **stays on the line of the last number** until you enter a new number or use
  **Reset now**. It never moves on its own, so the note looks like any normal note whenever it
  is opened. Closing the app or restarting the phone doesn't change it either.
- After the Celebrities note is opened, new numbers are ignored for **3 minutes**, so the reveal
  can't be disturbed. To perform again sooner, use **Reset now**.
- A lone first digit is forgotten after **45 seconds**.
- **Reset now** in the hidden settings forgets the number, a first digit and the lock, and moves
  the forced name to a new random line.

Both times can be changed in the hidden settings.

### Why the name never moves when you open the app

The list is rebuilt the moment the second swipe counts, while the app is still closed. When you
open Keep Notes, the very first picture it draws already has the name on the right line, so
there is nothing left to change on screen. When an app is still in memory, Android normally
shows a saved picture of its last screen while it opens (from the home screen and from Recents),
and that picture is what made the name visibly jump in early builds. Keep Notes no longer lets
Android keep that picture, so an old version of the list can never appear. (The side effect: the
Keep Notes card in Recents shows a plain background instead of a preview.)

### Hidden settings

Open Keep Notes, tap the search bar and type `*#*#`. Nothing in the app points to this screen, and
it closes as soon as the app is left. It shows:

- **Status** (live): whether the swipe service is on, the last number and how long ago it was
  entered, a first digit waiting for its partner, which line the forced name is on now, and
  whether new numbers are being accepted or ignored (and for how much longer).
- **Feedback**: vibration strength (Off, Light, Medium, Strong), a test button, and whether the
  second digit gives a double buzz.
- **Timing**: how long new numbers are ignored after the note is opened (Off, 1, 3 or 10
  minutes) and how long you have between the two swipes (20, 45 or 90 seconds).
- **Actions**: **Set the number by hand** (an emergency backup if the swipes failed) and **Reset
  now**.

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
  counted swipe buzzes, as on the home screen. Then open Celebrities.
- Or, as a last resort, use **Set the number by hand** in the hidden settings.

### Troubleshooting

- **A green box around apps in Recents, or the keyboard won't type**: you are on an old build.
  Early builds kept a screen-reader mode switched on everywhere. The current build only listens
  while the home screen is showing and is completely off in every other app. Install the latest
  APK from Releases, then check the service is still on.
- **The swipes stopped buzzing**: check **Swipe service** in the hidden settings. If it says
  *Off*, Android switched the service off (usually because the app was force-stopped or
  "put to sleep"); turn it on again and do the Samsung "Never sleeping apps" step.
- **"Allow restricted settings" is missing**: try to turn on the service once first (step 3 of
  Setup). Android only offers the option after it has blocked the setting.
- **The name is on the wrong line**: one of the swipes probably didn't count (no buzz), so a
  stray swipe paired up with the next one. Use **Reset now** and enter the number again, or set
  it by hand.

## Your profile picture and account

- Profile picture: `app/src/main/res/drawable-nodpi/profile.jpg`. It is cropped from the original
  photo to the same square Google uses for the account picture, and the app draws it in a circle
  inside Google's blue ring. Replace the file to change it; without one, the avatar shows Google's
  plain person outline.
- Name and email shown when the picture is tapped: edit `app/src/main/assets/account.txt`
  (line 1 name, line 2 email). Until then the account card shows just the picture.
- The app is called "Keep notes" on phones set to UK English and "Keep Notes" elsewhere, exactly
  like Google Keep.

## Changing the names

Edit `app/src/main/assets/celebrities.txt`: line 1 is the forced name and the other 99 lines are
the fillers. This is the only file to edit. Pushing rebuilds the APK.

Vibration strength and the timings are changed in the hidden settings (type `*#*#` in search).

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
app on Android 14 and Android 16 emulators (Pixel 6 profile, Pixel launcher) with `ci/drive.py`.
That job:

- takes screenshots of every screen in dark and light mode (a tour of the notes screen, search,
  the new-note menu, selection, the editor and its sheets, Reminders, Archive, Deleted, labels,
  Settings, Help, the account card and drawing);
- runs an end-to-end test of the trick with real touchscreen events (written to the emulator's
  touch device, because `adb shell input` bypasses accessibility services): it enables the
  service, swipes from the "6" and then the "1" keypad cell on a two-page home screen, opens
  the app by tapping its icon and checks that line 61 of Celebrities is the forced name;
- checks that touches still reach the launcher and gesture navigation still works with the
  service on, that the icon tap is not counted as a digit, that touch exploration (the
  screen-reader mode) is on only on the home screen and off in Keep Notes and in other apps,
  and that the in-app fallback (service off, two swipes on the notes list) puts the name on
  line 28.

All of those checks pass on both emulators. Screenshots are uploaded as the `screenshots-apiNN`
artifact and also kept under the git ref `refs/ci/<branch>/apiNN`
(`git fetch origin refs/ci/<branch>/api34`). The service logs what it sees only when its debug
tag is switched on (`adb shell setprop log.tag.KeepGestures DEBUG`), which only CI does.

**Not tested on a real phone.** Nothing here has run on real hardware, on Samsung One UI, or with
a launcher other than Pixel's. Before performing, practise on your own phone and check you feel
one buzz per swipe; if not, use the in-app fallback described above.

Fonts are Google Sans (SIL Open Font License) and icons are Material Symbols (Apache 2.0); see
`third_party/`.
