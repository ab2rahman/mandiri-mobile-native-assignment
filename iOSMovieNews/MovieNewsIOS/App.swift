import SwiftUI

@main
struct MovieNewsApp: App {
    private let container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView(
                moviesRouter: MoviesRouter(repository: container.movieRepository),
                newsRouter: NewsRouter(repository: container.newsRepository)
            )
        }
    }
}

@MainActor struct AppContainer {
    let movieRepository: MovieRepository
    let newsRepository: NewsRepository

    init() {
        let secrets = Secrets.load()
        movieRepository = RemoteMovieRepository(api: TMDBClient(apiKey: secrets.tmdbApiKey))
        newsRepository = RemoteNewsRepository(api: NewsAPIClient(apiKey: secrets.newsApiKey))
    }
}

struct Secrets {
    let tmdbApiKey: String
    let newsApiKey: String

    static func load() -> Secrets {
        guard
            let url = Bundle.module.url(forResource: "Secrets", withExtension: "plist"),
            let data = try? Data(contentsOf: url),
            let plist = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: String]
        else {
            return Secrets(tmdbApiKey: "", newsApiKey: "")
        }
        return Secrets(tmdbApiKey: plist["TMDB_API_KEY"] ?? "", newsApiKey: plist["NEWS_API_KEY"] ?? "")
    }
}

struct RootView: View {
    let moviesRouter: MoviesRouter
    let newsRouter: NewsRouter

    var body: some View {
        TabView {
            moviesRouter.makeGenresView()
                .tabItem { Label("Movies", systemImage: "film") }
            newsRouter.makeCategoriesView()
                .tabItem { Label("News", systemImage: "newspaper") }
        }
    }
}
