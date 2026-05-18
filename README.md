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

## Beginner test steps

1. Run the app.
2. Tap `Post`.
3. Enter the item details.
4. Type a location, for example `Deakin Burwood Library`, `Flinders Street`, or `Melbourne Central`.
5. Tap `SEARCH TYPED LOCATION`.
6. Choose an image.
7. Save the advert.
8. Add one more advert using `GET CURRENT LOCATION`.
9. Tap `Show on Map`.
10. Enter a radius such as `5`, tap `GET CURRENT LOCATION`, then tap `Show on Map` again.

## Main files

- `MainActivity.kt` contains the user interface, image picker, list, form, filtering, delete logic, map logic, current location, autocomplete, and radius search.
- `LostFoundDatabase` inside `MainActivity.kt` creates and manages the SQLite database.
- `AndroidManifest.xml` registers the app, permissions, and Google Maps API key setting.

## explanation

The app saves each advert as one row in the SQLite table named `adverts`.
When the user taps `Post`, the app opens a form. The app checks that all fields are filled, an image has been selected, and a location has been chosen. Then it saves the advert with the current date/time and location coordinates.

The category spinner at the top controls the filter. If `All` is selected, the app loads every advert. If a category is selected, the app only loads adverts where the saved category matches that selected category.

The `Remove` button deletes the advert from SQLite. This matches the task requirement that users should remove the advert after the item is returned to its owner.

The `Show on Map` button reads the saved adverts and adds them as markers on Google Maps. If a radius value is entered, the app compares each advert location with the user's current location and only shows adverts inside that distance.

## Video demo plan

In the video, show the app running and explain these parts of the code:

- `showPostDialog()` creates the post form.
- `openPlaceSearch()` opens Google Places autocomplete.
- `searchTypedLocation()` searches the typed location and gets coordinates.
- `useSimpleLocationFallback()` uses simple saved coordinates if the emulator cannot search the typed location.
- `getCurrentLocation()` gets the phone/emulator location.
- `validateAndSave()` checks the form and saves the advert.
- `showAdvertsOnMap()` adds map markers and applies the radius filter.
