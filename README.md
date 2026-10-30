# Free Digital Force Bag

An open-source Android app for a "force" trick. It installs as **Keep Notes** and looks and
behaves like Google Keep. While you casually swipe between pages on your phone's real home
screen, it reads a secret number. The "Celebrities" note in the app then has your forced name on
exactly that line.

## How it works

1. A small accessibility service ("Keep Notes") notices where each touch starts on the home
   screen. It never changes what you see: every touch goes straight to your launcher, untouched.
2. The app you start each sideways swipe **on** picks a digit. The keypad is the 3x3 block of
   app positions in the bottom-right of the home screen, just above the dock, under your thumb,
   with 0 the app to the left of the 7. On a four-column home screen:

   ```
   .  1  2  3
   .  4  5  6
   0  7  8  9
   [   dock   ]
   ```

   It is the same positions on every page, whichever apps are there (an empty spot works too).
   Swipes that start anywhere else never count, so you can move between pages freely by
   starting a swipe outside the keypad. **Set up the keypad** in the hidden settings lets you
   choose the apps for 1, 9 and 0 by tapping them, if your home screen is laid out differently.

   Two swipes make a number from 1 to 100. Single-digit numbers use a leading zero (7 is `0`
   then `7`) and 100 is `0` then `0`. A keypad swipe counts if the home screen turns to another page,
   or if it is a clear sideways swipe even though the page can't turn (on the last page). Every
   counted swipe buzzes (a double buzz when the second digit completes the number). Taps,
   long presses, swipes up into the app drawer and pulling down the notification shade don't
   count.
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
5. Put Keep Notes on the **third page** of your home screen. **Prime** (see below) takes you to
   the first page, so the two swipes of the trick take you from page 1 to page 2 to page 3,
   where the app is waiting. Pressing Home while you are already on the home screen must take
   you to the first page. On Samsung this is the "main page": pinch the home screen and check
   the house icon is on the first page.
6. **Check the keypad**: open the hidden settings (type `*#*#` in the Keep Notes search bar),
   switch on **Show the keypad on the home screen** and press Home. The ten keys are outlined
   over your apps. If they don't line up with your bottom-right 3x3, tap **Set up the keypad**:
   it goes to your home screen and asks you to tap the app for 1 (top-left of the 3x3), then
   9 (bottom-right), then 0. Nothing opens while you tap, and **Cancel** stops it. **Use the
   default keypad** goes back to the standard layout.
7. **Practise** with the overlay on: every swipe that counts lights up its key and buzzes, with
   the two-digit number shown above the keypad. Practice swipes never enter a number. Switch
   the overlay off again in the hidden settings; **Prime** also switches it off.

**Updating**: install the new APK over the old one. Afterwards check that Settings >
Accessibility > Keep Notes is still On. If you installed an early build, the service has since
been renamed and has to be turned on again once (step 3).

## Performing, step by step

1. **Prime the trick before the show**: open Keep Notes, tap the search bar and type `*#*#` to
   open the hidden settings. Check that **Swipe service** says *On* and tap **Prime**. The
   phone goes straight to the first page of your home screen, and the trick is armed.
   Nothing counts until you have primed it.
2. The spectator thinks of (or names) a number from 1 to 100.
3. On the home screen, make **exactly two page swipes** towards Keep Notes (page 1 to 2, then 2
   to 3). Start the first swipe on the keypad cell for the **tens** digit and the second on the
   cell for the **units** digit (7 is `0` then `7`, 100 is `0` then `0`). Where your finger
   **starts** picks the digit; after that it just has to be an ordinary swipe that turns the
   page. You feel **one short buzz** after the first swipe and a **double buzz** after the
   second: the number is in.
4. Tap the Keep Notes icon and open **Celebrities**. The forced name is on their line. Scroll
   down slowly while you talk.
5. **Opening the note switches the trick off**, and so does leaving Keep Notes once a number has
   been entered. From then on the service doesn't even listen on the home screen: nothing can
   buzz, and no swipe or tap can change the note, until you tap **Prime** again. You can hand
   the phone over. As soon as anyone leaves the app, the note goes back to its clean state.

### When a swipe counts, and why sometimes it doesn't

A swipe counts as a digit (and buzzes) only if **all** of these are true:

- The trick is **primed** (the hidden settings show *Primed*). After a reveal it is off until
  you tap **Prime** again.
- The Keep Notes accessibility service is on. Android switches it off if the app is force-stopped,
  which is why the Samsung "Never sleeping apps" step matters.
- The **home screen** is what you see: not the app drawer, Recents, the notification shade, the
  lock screen or another app. The service only listens while the home screen is showing, and
  switches on a fraction of a second after it appears, so pause for a moment after pressing Home
  before the first swipe. Everywhere else it is completely off, so other apps behave normally
  (no screen-reader effects such as focus boxes in Recents).
- It is a **sideways** swipe: the page turns, or, where there's no page to turn to (on the
  last page, where Keep Notes is), your finger moves clearly sideways (at least about a
  centimetre, and more sideways than up or down). So if a swipe on the last page didn't buzz,
  just swipe again there: the page bounces back but the digit still counts. Swipes up (app
  drawer) or down (notification shade) never count.
- The swipe doesn't start right at the edge of the screen. With gesture navigation, a swipe from
  the left or right edge is **Back** and one from the bottom edge is **Home** or switches apps,
  so the service ignores swipes that start within a few millimetres of the side edges or the
  bottom edge (unless the page turns). Start on the app icon itself, not at the very edge.
  This matters most for 3, 6 and 9, the right-hand column.
- It starts **on a keypad key** (one of the ten app positions). Anywhere else is ignored on
  purpose, so you can turn pages freely without entering a digit. Check where the keys are with
  the practice overlay.
- Your finger lifts within 3 seconds and nothing opens: tapping an icon or long-pressing (which
  opens a menu) never counts.

**Quick swipes are fine.** You can make the two swipes straight after each other, as fast as you
would normally flick between pages. Each swipe is checked as soon as the next one starts, so a
quick second swipe never cancels the first.

How the digits pair up:

- The first counted swipe is the tens digit. The next counted swipe within **45 seconds**
  completes the number (double buzz).
- If no second swipe comes within 45 seconds, the first digit is forgotten and the next swipe
  starts again as a tens digit.
- Once a number is complete, the next swipe starts a **new** number. The old number stays in
  place until the new one is complete, so one stray swipe can't spoil it, but two stray swipes
  will replace it.
- If you lose track, wait 45 seconds and enter the number again, or open the hidden settings
  and tap **Prime** to start over.

### Priming, switching off and resetting

- **Prime** (hidden settings) forgets the last number and any first digit, moves the forced
  name to a new random line, switches the keypad overlay off, arms the trick and goes to the
  first page of the home screen.
- The trick **switches itself off** when the Celebrities note is opened after a number was
  entered, or when Keep Notes is left after a number was entered (even if the note wasn't
  opened). Off means completely off: the service stops listening on the home screen, nothing
  buzzes and nothing changes until the next **Prime**. Opening the app or the note *before*
  entering a number doesn't switch it off.
- **Primed stays primed** until a number has been entered and revealed: turning the screen off,
  hours of waiting or restarting the phone doesn't change it. Two things to watch: unlock
  straight back onto the home screen page you left (most launchers keep the page, but check
  yours), and any page swipes you make in the meantime count as digits. If you've used the
  phone since priming, tap Prime again before performing (or wait 45 seconds after your last
  swipe: a lone digit is forgotten by then and a stray number is replaced by the real one).
- The forced name **stays on the line of the last number** until the next Prime. It never moves
  on its own, so the note looks like any normal note whenever it is opened. Closing the app or
  restarting the phone doesn't change it either.
- **Leaving the app** (Home, Recents or another app) also puts the Celebrities note back to its
  clean state (see below), closes any open note and returns the app to the plain notes list.
- A lone first digit is forgotten after **45 seconds** (changeable in the hidden settings).
- A fresh install, or an update from a build without Prime, starts switched off: prime it
  before the first practice run.

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

- **Prime**: the button to tap before every performance (see above).
- **Celebrity**: the forced name. Tap it, type any name and Save; the Celebrities note is rebuilt
  at once with that name on the same line. **Default** goes back to line 1 of
  `celebrities.txt`. If the name you type is one of the other 99, the default name takes its
  place in the list, so no name is ever there twice.
- **Status** (live): whether the trick is primed or off, whether the swipe service is on, the
  last number and how long ago it was entered, a first digit waiting for its partner, and which
  line the forced name is on now.
- **Practice**: **Show the keypad on the home screen**, the practice overlay. It can't be
  switched on while the trick is primed, and Prime switches it off. **Set up the keypad** picks
  the apps for 1, 9 and 0 on your home screen (the 3x3 is worked out from the 1 and the 9);
  **Use the default keypad** goes back to the bottom-right 3x3 with 0 left of the 7. Neither
  works while the trick is primed.
- **Feedback**: vibration strength (Off, Light, Medium, Strong), a test button, and whether the
  second digit gives a double buzz.
- **Timing**: how long you have between the two swipes (20, 45 or 90 seconds).
- **Actions**: **Set the number by hand**, an emergency backup if the swipes failed. It works
  even when the trick is off.

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
  tap **Prime** in the hidden settings (with the service off it stays in the app), go back to
  the notes list and make **two vertical swipes on the list** (as if scrolling), each starting
  on the keypad key for the digit (the same screen positions as on the home screen). Each counted swipe
  buzzes, as on the home screen. Then open Celebrities, which switches the trick off as usual.
- Or, as a last resort, use **Set the number by hand** in the hidden settings.

### Troubleshooting

- **A green box around apps in Recents, or the keyboard won't type**: you are on an old build.
  Early builds kept a screen-reader mode switched on everywhere. The current build only listens
  while the home screen is showing and is completely off in every other app. Install the latest
  APK from Releases, then check the service is still on.
- **Nothing buzzes**: open the hidden settings. If **Trick** isn't *Primed*, tap **Prime** (the
  trick switches itself off after every reveal). If **Swipe service** says *Off*, Android
  switched the service off (usually because the app was force-stopped or "put to sleep"); turn
  it on again and do the Samsung "Never sleeping apps" step.
- **Prime doesn't land on the first page**: Prime presses Home twice, and the second press takes
  the launcher to its main page. On Samsung, pinch the home screen and tap the house icon at the
  top of the first page to make it the main page. If your launcher can't do that, swipe back to
  the first page after priming and wait 45 seconds before the real swipes: a leftover first
  digit is forgotten by then, and a throwaway number is replaced by the real one.
- **"Allow restricted settings" is missing**: try to turn on the service once first (step 3 of
  Setup). Android only offers the option after it has blocked the setting.
- **The name is on the wrong line**: either a swipe started on the neighbouring keypad key, or
  a stray swipe made just before (within 45 seconds) was still waiting as a first digit, so
  your first swipe completed a number and your second started a new one (you feel the double
  buzz after your first swipe instead of the second). Priming right before the show prevents
  the second case. To fix it on the spot, enter the number again, or set it by hand.

## Your profile picture and account

- Profile picture: `app/src/main/res/drawable-nodpi/profile.jpg`. It is cropped from the original
  photo to the same square Google uses for the account picture, and the app draws it in a circle
  inside Google's blue ring. Replace the file to change it; without one, the avatar shows Google's
  plain person outline.
- Name and email shown when the picture is tapped: edit `app/src/main/assets/account.txt`
  (line 1 name, line 2 email). Until then the account card shows just the picture.
- The app is called "Keep notes" on phones set to UK English and "Keep Notes" elsewhere, exactly
  like Google Keep.

## The other notes

The app starts with about twenty everyday notes, all in the default colour, so it looks like a
phone that has been used for months: a shopping list with Japanese cooking bits, work to-dos,
a sprint retro, git commands, a bug write-up, recipes (oyakodon, katsu curry, tamagoyaki), a
Japan trip plan, a gym split and so on, plus two old notes in the Archive. The Celebrities
note is third, just below the shopping and work lists. They are ordinary notes: edit or delete
them and add your own. Updating from an earlier build swaps the old starter notes (Shopping
with milk and eggs, Pasta bake, Car service and the rest) for the new ones once; notes you wrote
yourself are kept. The text lives in `addStarterNotes` in
`app/src/main/java/app/notes/Store.java`.

## Changing the names

On the phone: **Celebrity** in the hidden settings changes the forced name (see Hidden settings).

In the code: edit `app/src/main/assets/celebrities.txt`. Line 1 is the default forced name and
the other 99 lines are the rest of the list. This is the only file to edit. Pushing rebuilds
the APK.

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
  service, checks nothing listens before priming, taps **Prime** from page 2 and checks it
  lands on page 1, swipes from the "6" and then the "1" keypad key on a two-page home screen,
  opens the app by tapping its icon and checks that line 61 of Celebrities is the forced name;
- checks the trick is then off: nothing listens on the home screen, two more swipes don't
  count and the note keeps line 61; that priming again switches listening back on; and that
  leaving the app after entering a number (without opening the note) switches it off too;
- checks the practice overlay shows on the home screen, lights up a swipe without entering a
  number, and goes off when the trick is primed; sets the keypad up by tapping three spots on
  the home screen and checks a swipe from the new "2" counts as 2;
- makes two quick swipes back to back, the second on the last page where the page can't turn,
  and checks both count (line 63);
- checks that touches still reach the launcher and gesture navigation still works with the
  service on, that the icon tap is not counted as a digit, that touch exploration (the
  screen-reader mode) is on only on the home screen and off in Keep Notes and in other apps,
  and that the in-app fallback (service off, Prime, two swipes on the notes list) puts the name
  on line 58 and is off after the reveal;
- changes the celebrity in the hidden settings to a name that is also on the list and checks
  it moves to the spectator's line without appearing twice.

Each run writes a `RESULTS` line with every check to its `log.txt`. Screenshots are uploaded as the `screenshots-apiNN`
artifact and also kept under the git ref `refs/ci/<branch>/apiNN`
(`git fetch origin refs/ci/<branch>/api34`). The service logs what it sees only when its debug
tag is switched on (`adb shell setprop log.tag.KeepGestures DEBUG`), which only CI does.

**Not tested on a real phone.** Nothing here has run on real hardware, on Samsung One UI, or with
a launcher other than Pixel's. Before performing, practise on your own phone and check you feel
one buzz per swipe; if not, use the in-app fallback described above.

Fonts are Google Sans (SIL Open Font License) and icons are Material Symbols (Apache 2.0); see
`third_party/`.
