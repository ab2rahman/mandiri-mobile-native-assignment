# Mandiri Mobile Native Assignment

This repository contains two native mobile projects:

- `AndroidMovieNews`: Android app written in Kotlin with Jetpack Compose.
- `iOSMovieNews`: iOS app written in Swift using VIPER modules.

Both projects implement Option 1 and Option 2:

- Movies from The Movie Database API.
- News from NewsAPI.

## API Keys

Do not commit real API keys.

Android:

1. Create `AndroidMovieNews/local.properties`.
2. Add:

```properties
TMDB_API_KEY=your_tmdb_key
NEWS_API_KEY=your_newsapi_key
```

iOS:

1. Open `iOSMovieNews/MovieNewsIOS/Resources/Secrets.plist`.
2. Replace the placeholder values with your keys.

## Android

```bash
cd AndroidMovieNews
./gradlew test
./gradlew assembleDebug
```

The Android app uses Compose, Navigation, Retrofit, OkHttp, Kotlin Serialization, Coil, and Coroutines.

## iOS

Open `iOSMovieNews/Package.swift` in Xcode and run the `MovieNewsIOS` executable target on an iOS simulator.

The iOS app uses SwiftUI for views while keeping feature boundaries in VIPER:

- View: SwiftUI screens.
- Interactor: API and business flow.
- Presenter: state mapping and user actions.
- Entity: Codable models.
- Router: module creation and navigation targets.

## Implemented User Stories

Movies:

- Official movie genres.
- Discover movies by genre with endless scrolling.
- Movie primary information.
- Movie reviews with endless scrolling.
- YouTube trailer link.
- Positive and negative API/UI states.

News:

- News categories.
- Sources by category with search and endless scrolling behavior.
- Articles by source with search and endless scrolling.
- Article detail in WebView.
- Positive and negative API/UI states.
