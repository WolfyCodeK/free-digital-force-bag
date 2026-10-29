# Free Digital Force Bag

An open-source Android app for a "force" trick. It installs as **Keep Notes**, a Google Keep look-alike.

## How it works

1. A small accessibility service watches your real home screen. It never changes what you see.
2. You swipe between home-screen pages. **Where each swipe starts** picks a digit, using an invisible keypad:

   ```
   1 2 3
   4 5 6
   7 8 9
   0 0 0   (whole bottom row is 0)
   ```
   Use two swipes per number. Single-digit numbers use a leading zero, so 7 is `0` then `7`, and 100 is `0` then `0`.
   Each accepted swipe gives one tiny vibration. A swipe only counts if the launcher actually changes page.
3. Open Keep Notes (put it on page 3). Open the **Celebrities** note. The forced name (default **Tom Hanks**, line 1 of `celebrities.txt`) is on the spectator's number.
4. If no number was entered in the last 30 minutes, the forced name lands on a random line.

## Setup on the phone

1. Install the APK from the **Latest APK** release (repo, then Releases).
2. Settings, then Accessibility, then Keep Notes, and turn it on. On Android 13+, first open App info for Keep Notes, tap the menu and choose "Allow restricted settings".
3. It needs Android 11 or newer.

## Changing the names

Edit `app/src/main/assets/celebrities.txt`: line 1 is the forced name and the other 99 lines are fillers. Pushing rebuilds the APK.

Tune haptics and timings with the constants at the top of `ForceService.java`.
