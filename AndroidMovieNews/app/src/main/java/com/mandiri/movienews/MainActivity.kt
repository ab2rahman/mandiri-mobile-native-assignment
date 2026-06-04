package com.mandiri.movienews

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import okhttp3.MediaType.Companion.toMediaType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer()
        setContent {
            MaterialTheme {
                AppRoot(container)
            }
        }
    }
}

private class AppContainer {
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()
    private val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

    val movieRepository: MovieRepository = MovieRepository(
        retrofit("https://api.themoviedb.org/3/", bearer = BuildConfig.TMDB_API_KEY).create(TmdbApi::class.java)
    )
    val newsRepository: NewsRepository = NewsRepository(
        retrofit("https://newsapi.org/v2/", queryKey = BuildConfig.NEWS_API_KEY).create(NewsApi::class.java)
    )

    private fun retrofit(baseUrl: String, bearer: String = "", queryKey: String = ""): Retrofit {
        val auth = Interceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (bearer.isNotBlank()) builder.addHeader("Authorization", "Bearer $bearer")
            val url = if (queryKey.isNotBlank()) {
                request.url.newBuilder().addQueryParameter("apiKey", queryKey).build()
            } else request.url
            chain.proceed(builder.url(url).build())
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().addInterceptor(auth).addInterceptor(logging).build())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Failure(val message: String) : LoadState<Nothing>
}

private fun Throwable.userMessage(): String = when (this) {
    is HttpException -> "Request failed (${code()}). Please check your API key or try again."
    else -> localizedMessage ?: "Something went wrong. Please try again."
}

@Serializable data class GenreResponse(val genres: List<Genre>)
@Serializable data class Genre(val id: Int, val name: String)
@Serializable data class MoviePage(val page: Int, val results: List<Movie>, @SerialName("total_pages") val totalPages: Int)
@Serializable data class Movie(
    val id: Int,
    val title: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0
)
@Serializable data class ReviewPage(val page: Int, val results: List<Review>, @SerialName("total_pages") val totalPages: Int)
@Serializable data class Review(val id: String, val author: String, val content: String)
@Serializable data class VideoResponse(val results: List<Video>)
@Serializable data class Video(val key: String, val site: String, val type: String, val official: Boolean = false)

interface TmdbApi {
    @GET("genre/movie/list")
    suspend fun genres(): GenreResponse

    @GET("discover/movie")
    suspend fun discover(@Query("with_genres") genreId: Int, @Query("page") page: Int): MoviePage

    @GET("movie/{id}")
    suspend fun details(@retrofit2.http.Path("id") id: Int): Movie

    @GET("movie/{id}/reviews")
    suspend fun reviews(@retrofit2.http.Path("id") id: Int, @Query("page") page: Int): ReviewPage

    @GET("movie/{id}/videos")
    suspend fun videos(@retrofit2.http.Path("id") id: Int): VideoResponse
}

class MovieRepository(private val api: TmdbApi) {
    suspend fun genres() = api.genres().genres
    suspend fun discover(genreId: Int, page: Int) = api.discover(genreId, page)
    suspend fun details(id: Int) = api.details(id)
    suspend fun reviews(id: Int, page: Int) = api.reviews(id, page)
    suspend fun trailer(id: Int) = api.videos(id).results.firstOrNull {
        it.site.equals("YouTube", true) && it.type.equals("Trailer", true)
    }
}

@Serializable data class SourceResponse(val status: String, val sources: List<NewsSource> = emptyList(), val message: String? = null)
@Serializable data class NewsSource(val id: String? = null, val name: String, val description: String = "", val category: String = "")
@Serializable data class ArticleResponse(val status: String, val articles: List<Article> = emptyList(), val message: String? = null)
@Serializable data class Article(
    val title: String,
    val description: String? = null,
    val url: String,
    @SerialName("urlToImage") val imageUrl: String? = null,
    @SerialName("publishedAt") val publishedAt: String? = null,
    val source: ArticleSource = ArticleSource("")
)
@Serializable data class ArticleSource(val name: String)

interface NewsApi {
    @GET("top-headlines/sources")
    suspend fun sources(@Query("category") category: String): SourceResponse

    @GET("everything")
    suspend fun articles(@Query("sources") source: String, @Query("q") query: String?, @Query("page") page: Int, @Query("pageSize") pageSize: Int = 20): ArticleResponse
}

class NewsRepository(private val api: NewsApi) {
    suspend fun sources(category: String, query: String): List<NewsSource> {
        val response = api.sources(category)
        if (response.status != "ok") error(response.message ?: "Unable to load news sources.")
        return response.sources.filter { it.name.contains(query, true) || it.description.contains(query, true) }
    }

    suspend fun articles(source: String, query: String, page: Int): List<Article> {
        val response = api.articles(source, query.ifBlank { null }, page)
        if (response.status != "ok") error(response.message ?: "Unable to load news articles.")
        return response.articles
    }
}

class MovieViewModel(private val repo: MovieRepository) : ViewModel() {
    var genres by mutableStateOf<LoadState<List<Genre>>>(LoadState.Idle)
    var selectedGenre by mutableStateOf<Genre?>(null)
    var movies = mutableStateListOf<Movie>()
    var movieError by mutableStateOf<String?>(null)
    var movieLoading by mutableStateOf(false)
    private var moviePage = 1
    private var movieTotalPages = 1

    fun loadGenres() = viewModelScope.launch {
        genres = LoadState.Loading
        genres = runCatching { repo.genres() }.fold({ LoadState.Success(it) }, { LoadState.Failure(it.userMessage()) })
    }

    fun selectGenre(genre: Genre) {
        selectedGenre = genre
        movies.clear()
        moviePage = 1
        movieTotalPages = 1
        loadMoreMovies()
    }

    fun loadMoreMovies() = viewModelScope.launch {
        val genre = selectedGenre ?: return@launch
        if (movieLoading || moviePage > movieTotalPages) return@launch
        movieLoading = true
        movieError = null
        runCatching { repo.discover(genre.id, moviePage) }
            .onSuccess {
                movies.addAll(it.results)
                movieTotalPages = it.totalPages
                moviePage += 1
            }
            .onFailure { movieError = it.userMessage() }
        movieLoading = false
    }
}

class MovieDetailViewModel(private val repo: MovieRepository, private val movieId: Int) : ViewModel() {
    var detail by mutableStateOf<LoadState<Movie>>(LoadState.Idle)
    var trailer by mutableStateOf<Video?>(null)
    var reviews = mutableStateListOf<Review>()
    var reviewError by mutableStateOf<String?>(null)
    var reviewLoading by mutableStateOf(false)
    private var page = 1
    private var totalPages = 1

    fun load() {
        viewModelScope.launch {
            detail = LoadState.Loading
            detail = runCatching { repo.details(movieId) }.fold({ LoadState.Success(it) }, { LoadState.Failure(it.userMessage()) })
            trailer = runCatching { repo.trailer(movieId) }.getOrNull()
            loadMoreReviews()
        }
    }

    fun loadMoreReviews() = viewModelScope.launch {
        if (reviewLoading || page > totalPages) return@launch
        reviewLoading = true
        reviewError = null
        runCatching { repo.reviews(movieId, page) }
            .onSuccess {
                reviews.addAll(it.results)
                totalPages = it.totalPages
                page += 1
            }
            .onFailure { reviewError = it.userMessage() }
        reviewLoading = false
    }
}

class NewsViewModel(private val repo: NewsRepository) : ViewModel() {
    val categories = listOf("business", "entertainment", "general", "health", "science", "sports", "technology")
    var selectedCategory by mutableStateOf<String?>(null)
    var sources by mutableStateOf<LoadState<List<NewsSource>>>(LoadState.Idle)
    var sourceQuery by mutableStateOf("")
    var selectedSource by mutableStateOf<NewsSource?>(null)
    var articleQuery by mutableStateOf("")
    var articles = mutableStateListOf<Article>()
    var articleError by mutableStateOf<String?>(null)
    var articleLoading by mutableStateOf(false)
    private var articlePage = 1

    fun selectCategory(category: String) {
        selectedCategory = category
        loadSources()
    }

    fun loadSources() = viewModelScope.launch {
        val category = selectedCategory ?: return@launch
        sources = LoadState.Loading
        sources = runCatching { repo.sources(category, sourceQuery) }
            .fold({ LoadState.Success(it) }, { LoadState.Failure(it.userMessage()) })
    }

    fun selectSource(source: NewsSource) {
        selectedSource = source
        articles.clear()
        articlePage = 1
        loadMoreArticles()
    }

    fun searchArticles(query: String) {
        articleQuery = query
        articles.clear()
        articlePage = 1
        loadMoreArticles()
    }

    fun loadMoreArticles() = viewModelScope.launch {
        val sourceId = selectedSource?.id ?: return@launch
        if (articleLoading) return@launch
        articleLoading = true
        articleError = null
        runCatching { repo.articles(sourceId, articleQuery, articlePage) }
            .onSuccess {
                articles.addAll(it)
                if (it.isNotEmpty()) articlePage += 1
            }
            .onFailure { articleError = it.userMessage() }
        articleLoading = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Movie News") }) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Movies") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("News") })
            }
            if (tab == 0) MoviesFlow(container.movieRepository) else NewsFlow(container.newsRepository)
        }
    }
}

@Composable
private fun MoviesFlow(repo: MovieRepository) {
    val vm: MovieViewModel = viewModel(factory = simpleFactory { MovieViewModel(repo) })
    var detailMovieId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.loadGenres() }

    detailMovieId?.let { id ->
        MovieDetailScreen(repo, id, onBack = { detailMovieId = null })
    } ?: MovieListScreen(vm, onMovie = { detailMovieId = it.id })
}

@Composable
private fun MovieListScreen(vm: MovieViewModel, onMovie: (Movie) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).padding(12.dp)) {
            Text("Genres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            StateBlock(vm.genres, empty = "No genres found.", retry = vm::loadGenres) { genres ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { genre ->
                        Card(Modifier.fillMaxWidth().clickable { vm.selectGenre(genre) }) {
                            Text(genre.name, Modifier.padding(12.dp), fontWeight = if (vm.selectedGenre == genre) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(0.58f).padding(12.dp)) {
            Text(vm.selectedGenre?.name ?: "Movies", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            MovieLazyList(vm.movies, vm.movieLoading, vm.movieError, vm::loadMoreMovies, onMovie)
        }
    }
}

@Composable
private fun MovieLazyList(movies: List<Movie>, loading: Boolean, error: String?, loadMore: () -> Unit, onMovie: (Movie) -> Unit) {
    val state = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= movies.lastIndex - 4 && movies.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) loadMore() }
    LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (movies.isEmpty() && !loading && error == null) item { EmptyText("Select a genre to discover movies.") }
        items(movies) { movie ->
            Card(Modifier.fillMaxWidth().clickable { onMovie(movie) }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage("https://image.tmdb.org/t/p/w185${movie.posterPath}", null, Modifier.size(78.dp, 116.dp))
                    Column {
                        Text(movie.title, fontWeight = FontWeight.Bold)
                        Text("${movie.releaseDate} - Rating ${"%.1f".format(movie.voteAverage)}")
                        Text(movie.overview, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (loading) item { LoadingRow() }
        error?.let { item { ErrorBlock(it, loadMore) } }
    }
}

@Composable
private fun MovieDetailScreen(repo: MovieRepository, movieId: Int, onBack: () -> Unit) {
    val vm: MovieDetailViewModel = viewModel(key = "movie-$movieId", factory = simpleFactory { MovieDetailViewModel(repo, movieId) })
    val context = LocalContext.current
    LaunchedEffect(movieId) { vm.load() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        StateBlock(vm.detail, empty = "Movie not found.", retry = vm::load) { movie ->
            Text(movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${movie.releaseDate} - Rating ${"%.1f".format(movie.voteAverage)}")
            Text(movie.overview, Modifier.padding(vertical = 8.dp))
            vm.trailer?.let { video ->
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.key}")))
                }) { Text("Open YouTube Trailer") }
            }
        }
        Text("Reviews", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        val state = rememberLazyListState()
        val shouldLoadMore by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= vm.reviews.lastIndex - 2 && vm.reviews.isNotEmpty() } }
        LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMoreReviews() }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.reviews.isEmpty() && !vm.reviewLoading && vm.reviewError == null) item { EmptyText("No reviews yet.") }
            items(vm.reviews) { review ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(review.author, fontWeight = FontWeight.Bold)
                        Text(review.content, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (vm.reviewLoading) item { LoadingRow() }
            vm.reviewError?.let { item { ErrorBlock(it, vm::loadMoreReviews) } }
        }
    }
}

@Composable
private fun NewsFlow(repo: NewsRepository) {
    val vm: NewsViewModel = viewModel(factory = simpleFactory { NewsViewModel(repo) })
    var webUrl by remember { mutableStateOf<String?>(null) }
    webUrl?.let { ArticleWebScreen(it) { webUrl = null } } ?: NewsScreen(vm, onArticle = { webUrl = it.url })
}

@Composable
private fun NewsScreen(vm: NewsViewModel, onArticle: (Article) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.32f).padding(12.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            vm.categories.forEach { category ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.selectCategory(category) }) {
                    Text(category.replaceFirstChar { it.uppercase() }, Modifier.padding(12.dp), fontWeight = if (vm.selectedCategory == category) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Column(Modifier.weight(0.34f).padding(12.dp)) {
            Text("Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(vm.sourceQuery, {
                vm.sourceQuery = it
                vm.loadSources()
            }, label = { Text("Search sources") }, modifier = Modifier.fillMaxWidth())
            StateBlock(vm.sources, empty = "No sources found.", retry = vm::loadSources) { sources ->
                LazyColumn {
                    items(sources) { source ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.selectSource(source) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(source.name, fontWeight = FontWeight.Bold)
                                Text(source.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(0.34f).padding(12.dp)) {
            Text(vm.selectedSource?.name ?: "Articles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(vm.articleQuery, vm::searchArticles, label = { Text("Search articles") }, modifier = Modifier.fillMaxWidth())
            ArticleList(vm, onArticle)
        }
    }
}

@Composable
private fun ArticleList(vm: NewsViewModel, onArticle: (Article) -> Unit) {
    val state = rememberLazyListState()
    val shouldLoadMore by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= vm.articles.lastIndex - 4 && vm.articles.isNotEmpty() } }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMoreArticles() }
    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.articles.isEmpty() && !vm.articleLoading && vm.articleError == null) item { EmptyText("Choose a source to view articles.") }
        items(vm.articles) { article ->
            Card(Modifier.fillMaxWidth().clickable { onArticle(article) }) {
                Column(Modifier.padding(12.dp)) {
                    AsyncImage(article.imageUrl, null, Modifier.fillMaxWidth().height(120.dp))
                    Text(article.title, fontWeight = FontWeight.Bold)
                    Text(article.description.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (vm.articleLoading) item { LoadingRow() }
        vm.articleError?.let { item { ErrorBlock(it, vm::loadMoreArticles) } }
    }
}

@Composable
private fun ArticleWebScreen(url: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Text("Article detail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            factory = { context -> WebView(context).apply { settings.javaScriptEnabled = true } },
            update = { it.loadUrl(url) }
        )
    }
}

@Composable
private fun <T> StateBlock(state: LoadState<T>, empty: String, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Idle -> EmptyText(empty)
        LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failure -> ErrorBlock(state.message, retry)
        is LoadState.Success -> {
            val value = state.data
            if (value is Collection<*> && value.isEmpty()) EmptyText(empty) else content(value)
        }
    }
}

@Composable private fun EmptyText(text: String) = Text(text, Modifier.padding(16.dp))
@Composable private fun LoadingRow() = Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
@Composable private fun ErrorBlock(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message)
        Button(onClick = retry) { Text("Retry") }
    }
}

private inline fun <reified T : ViewModel> simpleFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
