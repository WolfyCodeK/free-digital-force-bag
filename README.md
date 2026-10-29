# Free Digital Force Bag

A tiny open-source Android app for a "force" trick. It's installed as **Notes** and opens to a blank page.

## Performing

1. Write the forced celebrity (default: **Tom Hanks**) on a card.
2. Have the spectator think of a number from 1 to 100.
3. In the app, swipe twice, once per digit. The screen doesn't change and the phone gives one tiny buzz per swipe.
   Where a swipe *starts* picks the digit, using an invisible phone keypad:

   ```
   1 2 3
   4 5 6
   7 8 9
   0 0 0   (whole bottom row is 0)
   ```
   The columns are thirds of the screen width and the rows are quarters of the height.
   Single-digit numbers use a leading zero: 7 is `0` then `7`. 100 is `0` then `0`. 40 is `4` then `0`.
4. The screen then fades to a plain numbered list of 100 celebrities. The forced name sits on the spectator's number.
5. Back, or leaving the app, resets everything.

## Changing the names

Edit `app/src/main/assets/celebrities.txt`. Line 1 is the forced name and the other 99 lines are fillers. Pushing rebuilds the APK.

## Getting the APK

Every push runs the **Build APK** GitHub Action. Download `app-debug.apk` from the **Latest APK** release (repo → Releases) on your phone and install it. You'll need to allow installs from your browser.

Tune the swipe length, haptics and reveal delay with the constants at the top of `MainActivity.java`.
