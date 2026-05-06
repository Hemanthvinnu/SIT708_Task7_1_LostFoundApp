# SIT708 Task 7.1P - Lost and Found App

This is a beginner-friendly Android app for reporting lost and found items.

## Features

- Add a lost or found advert.
- Store adverts in a local SQLite database.
- Require an image for every advert.
- Automatically add a date/time stamp when the advert is saved.
- Filter the advert list by category, such as Electronics, Pets, and Wallets.
- View advert details.
- Remove an advert after the item is returned to its owner.

## How to run

1. Open this folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Start an emulator or connect an Android phone.
4. Press Run.

## Main files

- `MainActivity.kt` contains the user interface, image picker, list, form, filtering, and delete logic.
- `LostFoundDatabase` inside `MainActivity.kt` creates and manages the SQLite database.
- `AndroidManifest.xml` registers the app and main activity.

## Beginner explanation

The app saves each advert as one row in the SQLite table named `adverts`.
When the user taps `Post`, the app opens a form. The app checks that all fields are filled and that an image has been selected. Then it saves the advert with the current date/time.

The category spinner at the top controls the filter. If `All` is selected, the app loads every advert. If a category is selected, the app only loads adverts where the saved category matches that selected category.

The `Remove` button deletes the advert from SQLite. This matches the task requirement that users should remove the advert after the item is returned to its owner.
