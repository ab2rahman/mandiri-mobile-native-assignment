import SwiftUI

@MainActor
final class GenresPresenter: ObservableObject {
    @Published var state = Loadable<[Genre]>()
    @Published var selectedGenre: Genre?
    @Published var movieQuery = ""
    @Published var movies: [Movie] = []
    @Published var movieError: String?
    @Published var isLoadingMovies = false

    private let interactor: MoviesInteractor
    private var page = 1
    private var totalPages = 1

    init(interactor: MoviesInteractor) {
        self.interactor = interactor
    }

    func loadGenres() async {
        state.isLoading = true
        state.error = nil
        do {
            state.value = try await interactor.loadGenres()
        } catch {
            state.error = error.userMessage
        }
        state.isLoading = false
    }

    func select(_ genre: Genre) async {
        selectedGenre = genre
        movies = []
        page = 1
        totalPages = 1
        await loadMoreMovies()
    }

    func searchMovies() async {
        movies = []
        page = 1
        totalPages = 1
        await loadMoreMovies()
    }

    func loadMoreMovies() async {
        guard !isLoadingMovies, page <= totalPages else { return }
        guard !movieQuery.isEmpty || selectedGenre != nil else { return }
        isLoadingMovies = true
        movieError = nil
        do {
            let result = if movieQuery.isEmpty {
                try await interactor.discover(genreID: selectedGenre!.id, page: page)
            } else {
                try await interactor.search(query: movieQuery.trimmingCharacters(in: .whitespacesAndNewlines), page: page)
            }
            movies.append(contentsOf: result.results)
            totalPages = result.totalPages
            page += 1
        } catch {
            movieError = error.userMessage
        }
        isLoadingMovies = false
    }
}

@MainActor
final class MovieDetailPresenter: ObservableObject {
    @Published var detail = Loadable<Movie>()
    @Published var trailer: Video?
    @Published var reviews: [Review] = []
    @Published var reviewError: String?
    @Published var isLoadingReviews = false

    private let interactor: MoviesInteractor
    private let movieID: Int
    private var page = 1
    private var totalPages = 1

    init(interactor: MoviesInteractor, movieID: Int) {
        self.interactor = interactor
        self.movieID = movieID
    }

    func load() async {
        detail.isLoading = true
        do {
            detail.value = try await interactor.detail(movieID: movieID)
            trailer = try? await interactor.trailer(movieID: movieID)
            await loadMoreReviews()
        } catch {
            detail.error = error.userMessage
        }
        detail.isLoading = false
    }

    func loadMoreReviews() async {
        guard !isLoadingReviews, page <= totalPages else { return }
        isLoadingReviews = true
        reviewError = nil
        do {
            let result = try await interactor.reviews(movieID: movieID, page: page)
            reviews.append(contentsOf: result.results)
            totalPages = result.totalPages
            page += 1
        } catch {
            reviewError = error.userMessage
        }
        isLoadingReviews = false
    }
}

@MainActor struct MoviesInteractor {
    let repository: MovieRepository
    func loadGenres() async throws -> [Genre] { try await repository.genres() }
    func discover(genreID: Int, page: Int) async throws -> MoviePage { try await repository.discover(genreID: genreID, page: page) }
    func search(query: String, page: Int) async throws -> MoviePage { try await repository.search(query: query, page: page) }
    func detail(movieID: Int) async throws -> Movie { try await repository.detail(movieID: movieID) }
    func reviews(movieID: Int, page: Int) async throws -> ReviewPage { try await repository.reviews(movieID: movieID, page: page) }
    func trailer(movieID: Int) async throws -> Video? { try await repository.trailer(movieID: movieID) }
}

struct MoviesRouter {
    let repository: MovieRepository

    @MainActor func makeGenresView() -> some View {
        let interactor = MoviesInteractor(repository: repository)
        return NavigationStack {
            GenresView(presenter: GenresPresenter(interactor: interactor), router: self)
        }
    }

    @MainActor func makeMovieDetailView(movieID: Int) -> some View {
        let interactor = MoviesInteractor(repository: repository)
        return MovieDetailView(presenter: MovieDetailPresenter(interactor: interactor, movieID: movieID))
    }
}

struct GenresView: View {
    @StateObject var presenter: GenresPresenter
    let router: MoviesRouter
    @State private var showsGenreSheet = false

    var body: some View {
        List {
            Section {
                if let error = presenter.state.error {
                    RetryView(message: error) { Task { await presenter.loadGenres() } }
                } else if presenter.state.isLoading {
                    ProgressView()
                } else {
                    PickerRow(label: "Genre", value: presenter.selectedGenre?.name ?? "Select a genre") {
                        showsGenreSheet = true
                    }
                }
                TextField("Search movies", text: $presenter.movieQuery)
                    .onSubmit { Task { await presenter.searchMovies() } }
            }

            Section(presenter.movieQuery.isEmpty ? presenter.selectedGenre?.name ?? "Movies" : "Search Results") {
                if presenter.movies.isEmpty, !presenter.isLoadingMovies, presenter.movieError == nil {
                    Text("Search movies or select a genre.")
                }
                ForEach(presenter.movies) { movie in
                    NavigationLink(value: movie.id) {
                        MovieRow(movie: movie)
                            .task {
                                if movie == presenter.movies.last { await presenter.loadMoreMovies() }
                            }
                    }
                }
                if presenter.isLoadingMovies { ProgressView() }
                if let error = presenter.movieError {
                    RetryView(message: error) { Task { await presenter.loadMoreMovies() } }
                }
            }
        }
        .navigationTitle("Movies")
        .navigationDestination(for: Int.self) { movieID in
            router.makeMovieDetailView(movieID: movieID)
        }
        .sheet(isPresented: $showsGenreSheet) {
            NavigationStack {
                List {
                    ForEach(presenter.state.value ?? []) { genre in
                        Button {
                            showsGenreSheet = false
                            Task { await presenter.select(genre) }
                        } label: {
                            HStack {
                                Text(genre.name)
                                Spacer()
                                if presenter.selectedGenre == genre {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }
                .navigationTitle("Choose Genre")
            }
            .presentationDetents([.medium, .large])
        }
        .task { await presenter.loadGenres() }
    }
}

struct MovieRow: View {
    let movie: Movie

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AsyncImage(url: movie.posterPath.flatMap { URL(string: "https://image.tmdb.org/t/p/w185\($0)") }) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.gray.opacity(0.2)
            }
            .frame(width: 68, height: 102)
            .clipped()

            VStack(alignment: .leading, spacing: 4) {
                Text(movie.title).font(.headline)
                Text("\(movie.releaseDate ?? "-") - Rating \(movie.voteAverage, specifier: "%.1f")").font(.caption)
                Text(movie.overview).font(.subheadline).lineLimit(3)
            }
        }
    }
}

struct MovieDetailView: View {
    @StateObject var presenter: MovieDetailPresenter
    @Environment(\.openURL) private var openURL

    var body: some View {
        List {
            Section {
                if presenter.detail.isLoading {
                    ProgressView()
                } else if let error = presenter.detail.error {
                    RetryView(message: error) { Task { await presenter.load() } }
                } else if let movie = presenter.detail.value {
                    Text(movie.title).font(.title2.bold())
                    Text("\(movie.releaseDate ?? "-") - Rating \(movie.voteAverage, specifier: "%.1f")")
                    Text(movie.overview)
                    if let trailer = presenter.trailer, let url = URL(string: "https://www.youtube.com/watch?v=\(trailer.key)") {
                        Button("Open YouTube Trailer") { openURL(url) }
                    }
                }
            }

            Section("Reviews") {
                if presenter.reviews.isEmpty, !presenter.isLoadingReviews {
                    Text("No reviews yet.")
                }
                ForEach(presenter.reviews) { review in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(review.author).font(.headline)
                        Text(review.content).lineLimit(8)
                    }
                    .task {
                        if review == presenter.reviews.last { await presenter.loadMoreReviews() }
                    }
                }
                if presenter.isLoadingReviews { ProgressView() }
                if let error = presenter.reviewError {
                    RetryView(message: error) { Task { await presenter.loadMoreReviews() } }
                }
            }
        }
        .navigationTitle("Movie Detail")
        .task { await presenter.load() }
    }
}
