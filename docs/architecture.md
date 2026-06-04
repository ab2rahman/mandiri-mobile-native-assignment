# Architecture

## Android

The Android app uses a pragmatic MVVM structure in Kotlin:

- `MainActivity` hosts Compose UI.
- ViewModels own state, pagination, user actions, and error handling.
- Repositories isolate TMDB and NewsAPI behavior.
- Retrofit APIs map remote endpoints into Kotlin Serialization entities.

Positive cases are represented by success states and populated lists. Negative cases are represented by API-key failures, decoding failures, empty states, retry controls, and unit tests around repository errors.

## iOS

The iOS app uses VIPER per feature:

- View: `GenresView`, `MovieDetailView`, `NewsCategoriesView`, `ArticleWebView`.
- Interactor: `MoviesInteractor`, `NewsInteractor`.
- Presenter: `GenresPresenter`, `MovieDetailPresenter`, `NewsPresenter`.
- Entity: `Entities.swift`.
- Router: `MoviesRouter`, `NewsRouter`.

SwiftUI is used for modern native UI, while presenters remain testable and framework-light.

## API Notes

TMDB supports paged movie discovery and reviews, so both lists request additional pages as users scroll.

NewsAPI supports paging for articles. The public sources endpoint returns a full source list by category, so source search/filtering is handled locally after fetching the official category source list.

