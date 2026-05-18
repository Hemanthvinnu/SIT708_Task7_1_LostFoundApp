# SIT708 Task 9.1P - Lost and Found Map App

This is an Android app for reporting lost and found items. It started as my Task 7.1P app and was extended for Task 9.1P with map and location features.

## Features

- Add a lost or found advert.
- Store adverts in a local SQLite database.
- Require an image for every advert.
- Automatically add a date/time stamp when the advert is saved.
- Filter the advert list by category, such as Electronics, Pets, and Wallets.
- View advert details.
- Remove an advert after the item is returned to its owner.
- Pick a location by typing an address and searching it.
- Pick a location using Google Places autocomplete.
- Use the phone/emulator current location.
- Save latitude and longitude for each advert.
- Show saved adverts as markers on Google Maps.
- Search by radius in kilometres from the user's current location.

## How to run

1. Open this folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Open `local.properties`.
4. Add your Google Maps API key like this: `MAPS_API_KEY=your_key_here`.
5. In Google Cloud, enable Maps SDK for Android and Places API.
6. Start an emulator or connect an Android phone.
7. Press Run.

## Main files

- `MainActivity.kt` contains the user interface, image picker, list, form, filtering, delete logic, map logic, current location, autocomplete, and radius search.
- `LostFoundDatabase` inside `MainActivity.kt` creates and manages the SQLite database.
- `AndroidManifest.xml` registers the app, permissions, and Google Maps API key setting.


