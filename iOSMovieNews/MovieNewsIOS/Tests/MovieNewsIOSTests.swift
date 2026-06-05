import XCTest
@testable import MovieNewsIOS

final class MovieNewsIOSTests: XCTestCase {
    func testMoviePresenterLoadsGenres() async {
        let presenter = await GenresPresenter(interactor: MoviesInteractor(repository: FakeMovieRepository()))

        await presenter.loadGenres()

        let genres = await presenter.state.value
        XCTAssertEqual(genres, [Genre(id: 28, name: "Action"), Genre(id: 18, name: "Drama")])
    }

    func testMoviePresenterStoresError() async {
        let presenter = await GenresPresenter(interactor: MoviesInteractor(repository: FakeMovieRepository(shouldFail: true)))

        await presenter.loadGenres()

        let error = await presenter.state.error
        XCTAssertEqual(error, "Failure")
    }

    func testMoviePresenterSearchesMovies() async {
        let presenter = await GenresPresenter(interactor: MoviesInteractor(repository: FakeMovieRepository()))

        await MainActor.run { presenter.movieQuery = "matrix" }
        await presenter.searchMovies()

        let movies = await presenter.movies
        XCTAssertEqual(movies.map(\.title), ["Search Result"])
    }

    func testNewsPresenterFiltersSources() async {
        let presenter = await NewsPresenter(interactor: NewsInteractor(repository: FakeNewsRepository()))

        await presenter.selectCategory("technology")
        await MainActor.run { presenter.sourceQuery = "ios" }
        await presenter.loadSources()

        let sources = await presenter.sources.value
        XCTAssertEqual(sources?.map(\.name), ["iOS Dev Weekly"])
    }

    func testNewsPresenterStoresArticleError() async {
        let presenter = await NewsPresenter(interactor: NewsInteractor(repository: FakeNewsRepository(shouldFail: true)))
        await MainActor.run {
            presenter.selectedSource = NewsSource(id: "bad", name: "Bad", description: "", category: "general")
        }

        await presenter.loadMoreArticles()

        let error = await presenter.articleError
        XCTAssertEqual(error, "Failure")
    }
}

private struct FakeMovieRepository: MovieRepository {
    let shouldFail: Bool

    init(shouldFail: Bool = false) {
        self.shouldFail = shouldFail
    }

    func genres() async throws -> [Genre] {
        if shouldFail { throw APIError.message("Failure") }
        return [Genre(id: 28, name: "Action"), Genre(id: 18, name: "Drama")]
    }

    func discover(genreID: Int, page: Int) async throws -> MoviePage {
        MoviePage(page: page, results: [Movie(id: 1, title: "Movie", overview: "Overview", posterPath: nil, releaseDate: "2026-01-01", voteAverage: 8.1)], totalPages: 1)
    }

    func search(query: String, page: Int) async throws -> MoviePage {
        MoviePage(page: page, results: [Movie(id: 2, title: "Search Result", overview: query, posterPath: nil, releaseDate: "2026-01-02", voteAverage: 8.2)], totalPages: 1)
    }

    func detail(movieID: Int) async throws -> Movie {
        Movie(id: movieID, title: "Movie", overview: "Overview", posterPath: nil, releaseDate: "2026-01-01", voteAverage: 8.1)
    }

    func reviews(movieID: Int, page: Int) async throws -> ReviewPage {
        ReviewPage(page: page, results: [Review(id: "1", author: "Tester", content: "Great")], totalPages: 1)
    }

    func trailer(movieID: Int) async throws -> Video? {
        Video(key: "abc123", site: "YouTube", type: "Trailer", official: true)
    }
}

private struct FakeNewsRepository: NewsRepository {
    let shouldFail: Bool

    init(shouldFail: Bool = false) {
        self.shouldFail = shouldFail
    }

    func sources(category: String, query: String) async throws -> [NewsSource] {
        if shouldFail { throw APIError.message("Failure") }
        let sources = [
            NewsSource(id: "ios", name: "iOS Dev Weekly", description: "Swift and iOS news", category: category),
            NewsSource(id: "world", name: "World Daily", description: "General headlines", category: category)
        ]
        return query.isEmpty ? sources : sources.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.description.localizedCaseInsensitiveContains(query) }
    }

    func articles(sourceID: String, query: String, page: Int) async throws -> [Article] {
        if shouldFail { throw APIError.message("Failure") }
        return [
            Article(
                title: "Swift News",
                description: "Article",
                url: URL(string: "https://example.com")!,
                urlToImage: nil,
                publishedAt: nil,
                source: ArticleSource(name: "iOS Dev Weekly")
            )
        ]
    }
}
