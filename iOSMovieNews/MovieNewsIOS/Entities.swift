import Foundation

struct Loadable<Value: Sendable>: Sendable {
    var isLoading = false
    var value: Value?
    var error: String?
}

extension Error {
    var userMessage: String {
        if let apiError = self as? APIError { return apiError.message }
        return localizedDescription
    }
}

enum APIError: Error, Sendable {
    case invalidURL
    case invalidResponse
    case message(String)

    var message: String {
        switch self {
        case .invalidURL: "Invalid URL."
        case .invalidResponse: "Unable to read the server response."
        case .message(let text): text
        }
    }
}

struct GenreResponse: Decodable, Sendable { let genres: [Genre] }
struct Genre: Decodable, Identifiable, Equatable, Sendable { let id: Int; let name: String }

struct MoviePage: Decodable, Sendable {
    let page: Int
    let results: [Movie]
    let totalPages: Int

    enum CodingKeys: String, CodingKey {
        case page, results
        case totalPages = "total_pages"
    }
}

struct Movie: Decodable, Identifiable, Equatable, Sendable {
    let id: Int
    let title: String
    let overview: String
    let posterPath: String?
    let releaseDate: String?
    let voteAverage: Double

    enum CodingKeys: String, CodingKey {
        case id, title, overview
        case posterPath = "poster_path"
        case releaseDate = "release_date"
        case voteAverage = "vote_average"
    }
}

struct ReviewPage: Decodable, Sendable {
    let page: Int
    let results: [Review]
    let totalPages: Int

    enum CodingKeys: String, CodingKey {
        case page, results
        case totalPages = "total_pages"
    }
}

struct Review: Decodable, Identifiable, Equatable, Sendable {
    let id: String
    let author: String
    let content: String
}

struct VideoResponse: Decodable, Sendable { let results: [Video] }
struct Video: Decodable, Equatable, Sendable {
    let key: String
    let site: String
    let type: String
    let official: Bool?
}

struct SourceResponse: Decodable, Sendable {
    let status: String
    let sources: [NewsSource]?
    let message: String?
}

struct NewsSource: Decodable, Identifiable, Equatable, Sendable {
    let id: String?
    let name: String
    let description: String
    let category: String

    var stableID: String { id ?? name }
}

struct ArticleResponse: Decodable, Sendable {
    let status: String
    let articles: [Article]?
    let message: String?
}

struct Article: Decodable, Identifiable, Equatable, Sendable {
    let title: String
    let description: String?
    let url: URL
    let urlToImage: URL?
    let publishedAt: String?
    let source: ArticleSource

    var id: URL { url }
}

struct ArticleSource: Decodable, Equatable, Sendable { let name: String }
