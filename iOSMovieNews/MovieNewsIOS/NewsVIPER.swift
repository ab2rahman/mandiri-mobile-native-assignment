import SwiftUI
import WebKit

@MainActor
final class NewsPresenter: ObservableObject {
    @Published var selectedCategory: String?
    @Published var sourceQuery = ""
    @Published var sources = Loadable<[NewsSource]>()
    @Published var selectedSource: NewsSource?
    @Published var articleQuery = ""
    @Published var articles: [Article] = []
    @Published var articleError: String?
    @Published var isLoadingArticles = false

    let categories = ["business", "entertainment", "general", "health", "science", "sports", "technology"]
    private let interactor: NewsInteractor
    private var articlePage = 1

    init(interactor: NewsInteractor) {
        self.interactor = interactor
    }

    func selectCategory(_ category: String) async {
        selectedCategory = category
        await loadSources()
    }

    func loadSources() async {
        guard let selectedCategory else { return }
        sources.isLoading = true
        sources.error = nil
        do {
            sources.value = try await interactor.sources(category: selectedCategory, query: sourceQuery)
        } catch {
            sources.error = error.userMessage
        }
        sources.isLoading = false
    }

    func selectSource(_ source: NewsSource) async {
        selectedSource = source
        articles = []
        articlePage = 1
        await loadMoreArticles()
    }

    func searchArticles() async {
        articles = []
        articlePage = 1
        await loadMoreArticles()
    }

    func loadMoreArticles() async {
        guard let sourceID = selectedSource?.id, !isLoadingArticles else { return }
        isLoadingArticles = true
        articleError = nil
        do {
            let next = try await interactor.articles(sourceID: sourceID, query: articleQuery, page: articlePage)
            articles.append(contentsOf: next)
            if !next.isEmpty { articlePage += 1 }
        } catch {
            articleError = error.userMessage
        }
        isLoadingArticles = false
    }
}

@MainActor struct NewsInteractor {
    let repository: NewsRepository
    func sources(category: String, query: String) async throws -> [NewsSource] { try await repository.sources(category: category, query: query) }
    func articles(sourceID: String, query: String, page: Int) async throws -> [Article] { try await repository.articles(sourceID: sourceID, query: query, page: page) }
}

struct NewsRouter {
    let repository: NewsRepository

    @MainActor func makeCategoriesView() -> some View {
        let interactor = NewsInteractor(repository: repository)
        return NavigationStack {
            NewsCategoriesView(presenter: NewsPresenter(interactor: interactor))
        }
    }
}

struct NewsCategoriesView: View {
    @StateObject var presenter: NewsPresenter
    @State private var showsCategorySheet = false
    @State private var showsSourceSheet = false

    var body: some View {
        List {
            Section {
                PickerRow(label: "Category", value: presenter.selectedCategory?.capitalized ?? "Select a category") {
                    showsCategorySheet = true
                }

                if presenter.sources.isLoading {
                    ProgressView()
                } else if let error = presenter.sources.error {
                    RetryView(message: error) { Task { await presenter.loadSources() } }
                } else {
                    PickerRow(
                        label: "Source",
                        value: presenter.selectedSource?.name ?? (presenter.selectedCategory == nil ? "Choose a category first" : "Select a source"),
                        isEnabled: presenter.selectedCategory != nil
                    ) {
                        showsSourceSheet = true
                    }
                }
            }

            Section(presenter.selectedSource?.name ?? "Articles") {
                TextField("Search articles", text: $presenter.articleQuery)
                    .onSubmit { Task { await presenter.searchArticles() } }

                if presenter.articles.isEmpty, !presenter.isLoadingArticles, presenter.selectedSource == nil {
                    Text("Choose a source to view articles.")
                }
                ForEach(presenter.articles) { article in
                    NavigationLink {
                        ArticleDestinationView(article: article)
                    } label: {
                        ArticleRow(article: article)
                            .task {
                                if article == presenter.articles.last { await presenter.loadMoreArticles() }
                            }
                    }
                }
                if presenter.isLoadingArticles { ProgressView() }
                if let error = presenter.articleError {
                    RetryView(message: error) { Task { await presenter.loadMoreArticles() } }
                }
            }
        }
        .navigationTitle("News")
        .sheet(isPresented: $showsCategorySheet) {
            NavigationStack {
                List {
                    ForEach(presenter.categories, id: \.self) { category in
                        Button {
                            showsCategorySheet = false
                            Task { await presenter.selectCategory(category) }
                        } label: {
                            HStack {
                                Text(category.capitalized)
                                Spacer()
                                if presenter.selectedCategory == category {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }
                .navigationTitle("Choose Category")
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showsSourceSheet) {
            NavigationStack {
                List {
                    Section {
                        TextField("Search sources", text: $presenter.sourceQuery)
                            .onSubmit { Task { await presenter.loadSources() } }
                    }

                    Section {
                        if presenter.sources.isLoading {
                            ProgressView()
                        } else if let error = presenter.sources.error {
                            RetryView(message: error) { Task { await presenter.loadSources() } }
                        } else {
                            ForEach(presenter.sources.value ?? [], id: \.stableID) { source in
                                Button {
                                    showsSourceSheet = false
                                    Task { await presenter.selectSource(source) }
                                } label: {
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading) {
                                            Text(source.name).font(.headline)
                                            Text(source.description).font(.subheadline).lineLimit(3)
                                        }
                                        Spacer()
                                        if presenter.selectedSource == source {
                                            Image(systemName: "checkmark")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .navigationTitle("Choose Source")
            }
            .presentationDetents([.medium, .large])
        }
    }
}

struct ArticleRow: View {
    let article: Article

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            AsyncImage(url: article.urlToImage) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.gray.opacity(0.2)
            }
            .frame(height: 120)
            .clipped()
            Text(article.title).font(.headline)
            Text(article.description ?? "").font(.subheadline).lineLimit(3)
        }
    }
}

struct ArticleDestinationView: View {
    let article: Article

    var body: some View {
        let view = ArticleWebView(url: article.url)
            .navigationTitle(article.source.name)
#if os(iOS)
        view.navigationBarTitleDisplayMode(.inline)
#else
        view
#endif
    }
}

#if os(iOS)
typealias PlatformWebViewRepresentable = UIViewRepresentable
#else
typealias PlatformWebViewRepresentable = NSViewRepresentable
#endif

struct ArticleWebView: PlatformWebViewRepresentable {
    let url: URL

#if os(iOS)
    func makeUIView(context: Context) -> WKWebView {
        WKWebView()
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        webView.load(URLRequest(url: url))
    }
#else
    func makeNSView(context: Context) -> WKWebView {
        WKWebView()
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        webView.load(URLRequest(url: url))
    }
#endif
}

struct RetryView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message).foregroundStyle(.red)
            Button("Retry", action: retry)
        }
    }
}

struct PickerRow: View {
    let label: String
    let value: String
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(label).font(.caption).foregroundStyle(.secondary)
                    Text(value).font(.headline).foregroundStyle(isEnabled ? .primary : .secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.secondary)
            }
        }
        .disabled(!isEnabled)
    }
}
