package com.mandiri.movienews

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

@Composable
private fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🎬") },
                    label = { Text("Movies") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📰") },
                    label = { Text("News") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(ListBackground)) {
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
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5 && vm.movies.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMoreMovies() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            LargeTitle("Movies")
            SectionHeader("Genres")
            when (val state = vm.genres) {
                LoadState.Idle -> DropdownPicker("Genre", "No genres found", emptyList(), enabled = false)
                LoadState.Loading -> DropdownPicker("Genre", "Loading genres", emptyList(), enabled = false)
                is LoadState.Failure -> ErrorListRow(state.message, vm::loadGenres)
                is LoadState.Success -> DropdownPicker(
                    label = "Genre",
                    value = vm.selectedGenre?.name ?: "Select a genre",
                    options = state.data.map { genre -> genre.name to { vm.selectGenre(genre) } },
                    enabled = state.data.isNotEmpty()
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item { SectionHeader(vm.selectedGenre?.name ?: "Movies") }
            if (vm.movies.isEmpty() && !vm.movieLoading && vm.movieError == null) {
                item { EmptyListRow("Select a genre to discover movies.") }
            }
            items(vm.movies) { movie ->
                MovieListRow(movie = movie, onClick = { onMovie(movie) })
            }
            if (vm.movieLoading) item { LoadingListRow() }
            vm.movieError?.let { item { ErrorListRow(it, vm::loadMoreMovies) } }
        }
    }
}

@Composable
private fun MovieListRow(movie: Movie, onClick: () -> Unit) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterImage(movie.posterPath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(movie.title, fontWeight = FontWeight.SemiBold)
                Text("${movie.releaseDate.ifBlank { "-" }} - Rating ${"%.1f".format(movie.voteAverage)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Text(movie.overview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = SecondaryText, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun MovieDetailScreen(repo: MovieRepository, movieId: Int, onBack: () -> Unit) {
    val vm: MovieDetailViewModel = viewModel(key = "movie-$movieId", factory = simpleFactory { MovieDetailViewModel(repo, movieId) })
    val context = LocalContext.current
    LaunchedEffect(movieId) { vm.load() }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4 && vm.reviews.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMoreReviews() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item { BackRow("Movie Detail", onBack) }
        when (val state = vm.detail) {
            LoadState.Idle -> item { EmptyListRow("Movie not found.") }
            LoadState.Loading -> item { LoadingListRow() }
            is LoadState.Failure -> item { ErrorListRow(state.message, vm::load) }
            is LoadState.Success -> item {
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.data.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${state.data.releaseDate.ifBlank { "-" }} - Rating ${"%.1f".format(state.data.voteAverage)}", color = SecondaryText)
                        Text(state.data.overview)
                        vm.trailer?.let { video ->
                            Button(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.key}")))
                            }) { Text("Open YouTube Trailer") }
                        }
                    }
                }
            }
        }

        item { SectionHeader("Reviews") }
        if (vm.reviews.isEmpty() && !vm.reviewLoading && vm.reviewError == null) item { EmptyListRow("No reviews yet.") }
        items(vm.reviews) { review ->
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(review.author, fontWeight = FontWeight.SemiBold)
                    Text(review.content, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = DividerColor)
        }
        if (vm.reviewLoading) item { LoadingListRow() }
        vm.reviewError?.let { item { ErrorListRow(it, vm::loadMoreReviews) } }
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
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5 && vm.articles.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMoreArticles() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            LargeTitle("News")
            SectionHeader("Categories")
            DropdownPicker(
                label = "Category",
                value = vm.selectedCategory?.replaceFirstChar { it.uppercase() } ?: "Select a category",
                options = vm.categories.map { category ->
                    category.replaceFirstChar { it.uppercase() } to { vm.selectCategory(category) }
                }
            )

            SectionHeader("Sources")
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.sourceQuery,
                    onValueChange = {
                        vm.sourceQuery = it
                        vm.loadSources()
                    },
                    label = { Text("Search sources") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }

            when (val state = vm.sources) {
                LoadState.Idle -> DropdownPicker("Source", "Choose a category first", emptyList(), enabled = false)
                LoadState.Loading -> DropdownPicker("Source", "Loading sources", emptyList(), enabled = false)
                is LoadState.Failure -> ErrorListRow(state.message, vm::loadSources)
                is LoadState.Success -> DropdownPicker(
                    label = "Source",
                    value = vm.selectedSource?.name ?: "Select a source",
                    options = state.data.map { source -> source.name to { vm.selectSource(source) } },
                    enabled = state.data.isNotEmpty()
                )
            }

            SectionHeader("Articles")
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.articleQuery,
                    onValueChange = vm::searchArticles,
                    label = { Text("Search articles") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item { SectionHeader(vm.selectedSource?.name ?: "Articles") }
            if (vm.articles.isEmpty() && !vm.articleLoading && vm.articleError == null) {
                item { EmptyListRow("Choose a source to view articles.") }
            }
            items(vm.articles) { article ->
                ArticleListRow(article = article, onClick = { onArticle(article) })
            }
            if (vm.articleLoading) item { LoadingListRow() }
            vm.articleError?.let { item { ErrorListRow(it, vm::loadMoreArticles) } }
        }
    }
}

@Composable
private fun ArticleListRow(article: Article, onClick: () -> Unit) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArticleImage(article.imageUrl)
            Text(article.title, fontWeight = FontWeight.SemiBold)
            Text(article.description.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis, color = SecondaryText)
        }
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun ArticleWebScreen(url: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ListBackground).padding(16.dp)) {
        BackRow("Article Detail", onBack)
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            factory = { context -> WebView(context).apply { settings.javaScriptEnabled = true } },
            update = { it.loadUrl(url) }
        )
    }
}

private val ListBackground = Color(0xFFF2F2F7)
private val SecondaryText = Color(0xFF6D6D72)
private val DividerColor = Color(0xFFE5E5EA)

private fun tmdbPosterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w342$it" }

@Composable private fun PosterImage(path: String?) {
    Box(
        modifier = Modifier
            .size(68.dp, 102.dp)
            .background(ListBackground),
        contentAlignment = Alignment.Center
    ) {
        if (path.isNullOrBlank()) {
            Text("No image", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        } else {
            AsyncImage(
                model = tmdbPosterUrl(path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable private fun ArticleImage(url: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(ListBackground),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text("No image", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable private fun LargeTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SecondaryText, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 2.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DropdownPicker(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        action()
                    }
                )
            }
        }
    }
}

@Composable private fun BackRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹ Back", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(56.dp))
    }
}

@Composable private fun EmptyListRow(text: String) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp), color = SecondaryText)
    }
    HorizontalDivider(color = DividerColor)
}

@Composable private fun LoadingListRow() {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    HorizontalDivider(color = DividerColor)
}

@Composable private fun ErrorListRow(message: String, retry: () -> Unit) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = SecondaryText)
            Button(onClick = retry) { Text("Retry") }
        }
    }
    HorizontalDivider(color = DividerColor)
}

private inline fun <reified T : ViewModel> simpleFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
