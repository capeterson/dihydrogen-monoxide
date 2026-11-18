# Health Connect: manual checks on a real phone

The fake client covers the logic (`sync/healthconnect/src/test`). These checks need real source apps, so run them before
sending a build to a tester. Record device, Android version, build and result.

Setup: Health Connect installed (built in on Android 14+), a source app that writes water (Fitbit, Samsung Health, or Google
Fit's successor), a second phone or the same account for the new-device case.

1. **Permissions are per switch.** Settings → Health Connect. Turn on "Count water logged in other apps": only the water
   *read* permission is requested. Deny it: the switch returns to off and the message appears. Allow it: it stays on.
2. **Import.** Log 250 ml in the source app. Open this app (or "Sync now"): the drink appears in Today and History with the
   source app named, the total changes, and the goal's reminders follow. The source app appears in "Apps that add water".
3. **Exclude a source.** Switch that app off: its drinks disappear from the day total. Switch it on: they come back after the
   next sync. Turn "Count water logged in other apps" off: all imported drinks hide; turn it on again and they return.
4. **Edit and delete in the source app.** Change the volume there: this app follows. Delete it there: it disappears here.
5. **Adopt.** In History, make an imported drink editable, change it, then change it in the source app: the local edit wins.
6. **Write.** Turn on "Save my water to Health Connect" (the first time every existing drink is queued). Log, edit and
   delete a drink here and confirm each shows up, changes and disappears in the Health Connect app. Log one with no
   connection, reconnect, and confirm it arrives once.
7. **Weight and exercise.** Turn on both. A new weight reading updates the goal's weight (unless "pin weight" is set in the
   profile). A 30-minute run recorded elsewhere adds water to that day.
8. **Revoked permission.** Remove a permission in system settings: the screen says a permission was removed, nothing
   crashes, and the other switches keep working.
9. **Background.** With "Sync in the background" on and the app closed, log water in the source app, wait 15–30 minutes
   with the phone idle and charging, and open the app: the drink is already there.
10. **Restore onto another phone.** Restore a backup, then connect Health Connect: previously imported drinks are not
    duplicated, and the switches start off (permissions belong to each phone).
