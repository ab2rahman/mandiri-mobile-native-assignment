import Foundation

@MainActor protocol HTTPSession {
    func data(from request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: HTTPSession {
    func data(from request: URLRequest) async throws -> (Data, URLResponse) {
        try await data(for: request)
    }
}

@MainActor struct APIClient {
    let baseURL: URL
    let session: HTTPSession
    let headers: [String: String]
    let defaultQuery: [URLQueryItem]
    private let decoder = JSONDecoder()

    init(baseURL: URL, session: HTTPSession = URLSession.shared, headers: [String: String] = [:], defaultQuery: [URLQueryItem] = []) {
        self.baseURL = baseURL
        self.session = session
        self.headers = headers
        self.defaultQuery = defaultQuery
    }

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        guard var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false) else {
            throw APIError.invalidURL
        }
        components.queryItems = defaultQuery + query
        guard let url = components.url else { throw APIError.invalidURL }
        var request = URLRequest(url: url)
        headers.forEach { request.addValue($0.value, forHTTPHeaderField: $0.key) }
        let (data, response) = try await session.data(from: request)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw APIError.message("Request failed. Please check your API key or try again.")
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.invalidResponse
        }
    }
}

@MainActor protocol MovieRepository {
    func genres() async throws -> [Genre]
    func discover(genreID: Int, page: Int) async throws -> MoviePage
    func search(query: String, page: Int) async throws -> MoviePage
    func detail(movieID: Int) async throws -> Movie
    func reviews(movieID: Int, page: Int) async throws -> ReviewPage
    func trailer(movieID: Int) async throws -> Video?
}

@MainActor struct TMDBClient {
    private let client: APIClient

    init(apiKey: String, session: HTTPSession = URLSession.shared) {
        client = APIClient(
            baseURL: URL(string: "https://api.themoviedb.org/3/")!,
            session: session,
            headers: apiKey.isEmpty ? [:] : ["Authorization": "Bearer \(apiKey)"]
        )
    }

    func genres() async throws -> GenreResponse { try await client.get("genre/movie/list") }
    func discover(genreID: Int, page: Int) async throws -> MoviePage {
        try await client.get("discover/movie", query: [.init(name: "with_genres", value: "\(genreID)"), .init(name: "page", value: "\(page)")])
    }
    func search(query: String, page: Int) async throws -> MoviePage {
        try await client.get("search/movie", query: [.init(name: "query", value: query), .init(name: "page", value: "\(page)")])
    }
    func detail(movieID: Int) async throws -> Movie { try await client.get("movie/\(movieID)") }
    func reviews(movieID: Int, page: Int) async throws -> ReviewPage {
        try await client.get("movie/\(movieID)/reviews", query: [.init(name: "page", value: "\(page)")])
    }
    func videos(movieID: Int) async throws -> VideoResponse { try await client.get("movie/\(movieID)/videos") }
}

struct RemoteMovieRepository: MovieRepository {
    let api: TMDBClient
    func genres() async throws -> [Genre] { try await api.genres().genres }
    func discover(genreID: Int, page: Int) async throws -> MoviePage { try await api.discover(genreID: genreID, page: page) }
    func search(query: String, page: Int) async throws -> MoviePage { try await api.search(query: query, page: page) }
    func detail(movieID: Int) async throws -> Movie { try await api.detail(movieID: movieID) }
    func reviews(movieID: Int, page: Int) async throws -> ReviewPage { try await api.reviews(movieID: movieID, page: page) }
    func trailer(movieID: Int) async throws -> Video? {
        try await api.videos(movieID: movieID).results.first { $0.site.lowercased() == "youtube" && $0.type.lowercased() == "trailer" }
    }
}

@MainActor protocol NewsRepository {
    func sources(category: String, query: String) async throws -> [NewsSource]
    func articles(sourceID: String, query: String, page: Int) async throws -> [Article]
}

@MainActor struct NewsAPIClient {
    private let client: APIClient

    init(apiKey: String, session: HTTPSession = URLSession.shared) {
        client = APIClient(
            baseURL: URL(string: "https://newsapi.org/v2/")!,
            session: session,
            defaultQuery: apiKey.isEmpty ? [] : [.init(name: "apiKey", value: apiKey)]
        )
    }

    func sources(category: String) async throws -> SourceResponse {
        try await client.get("top-headlines/sources", query: [.init(name: "category", value: category)])
    }

    func articles(sourceID: String, query: String, page: Int) async throws -> ArticleResponse {
        var items = [URLQueryItem(name: "sources", value: sourceID), .init(name: "page", value: "\(page)"), .init(name: "pageSize", value: "20")]
        if !query.isEmpty { items.append(.init(name: "q", value: query)) }
        return try await client.get("everything", query: items)
    }
}

struct RemoteNewsRepository: NewsRepository {
    let api: NewsAPIClient

    func sources(category: String, query: String) async throws -> [NewsSource] {
        let response = try await api.sources(category: category)
        guard response.status == "ok" else { throw APIError.message(response.message ?? "Unable to load sources.") }
        let sources = response.sources ?? []
        guard !query.isEmpty else { return sources }
        return sources.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.description.localizedCaseInsensitiveContains(query) }
    }

    func articles(sourceID: String, query: String, page: Int) async throws -> [Article] {
        let response = try await api.articles(sourceID: sourceID, query: query, page: page)
        guard response.status == "ok" else { throw APIError.message(response.message ?? "Unable to load articles.") }
        return response.articles ?? []
    }
}
