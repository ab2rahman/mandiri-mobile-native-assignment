package com.mandiri.movienews

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test

class RepositoryTest {
    @Test
    fun movieRepositoryReturnsOfficialGenres() = runTest {
        val repository = MovieRepository(FakeTmdbApi())

        val genres = repository.genres()

        assertEquals(listOf(Genre(28, "Action"), Genre(18, "Drama")), genres)
    }

    @Test
    fun movieRepositoryReturnsYoutubeTrailer() = runTest {
        val repository = MovieRepository(FakeTmdbApi())

        val trailer = repository.trailer(1)

        assertEquals("abc123", trailer?.key)
    }

    @Test
    fun newsRepositoryFiltersSources() = runTest {
        val repository = NewsRepository(FakeNewsApi())

        val sources = repository.sources("technology", "android")

        assertEquals(1, sources.size)
        assertEquals("Android Weekly", sources.first().name)
    }

    @Test
    fun newsRepositoryThrowsWhenApiReturnsError() = runTest {
        val repository = NewsRepository(FakeNewsApi(status = "error"))

        assertFailsWith<IllegalStateException> {
            repository.articles("bad-source", "", 1)
        }
    }
}

private class FakeTmdbApi : TmdbApi {
    override suspend fun genres() = GenreResponse(listOf(Genre(28, "Action"), Genre(18, "Drama")))

    override suspend fun discover(genreId: Int, page: Int) = MoviePage(
        page = page,
        totalPages = 2,
        results = listOf(Movie(id = 1, title = "Sample Movie", overview = "Overview"))
    )

    override suspend fun details(id: Int) = Movie(id = id, title = "Sample Movie", overview = "Overview")

    override suspend fun reviews(id: Int, page: Int) = ReviewPage(
        page = page,
        totalPages = 1,
        results = listOf(Review(id = "r1", author = "Reviewer", content = "Good movie"))
    )

    override suspend fun videos(id: Int) = VideoResponse(
        listOf(
            Video(key = "not-trailer", site = "YouTube", type = "Teaser"),
            Video(key = "abc123", site = "YouTube", type = "Trailer", official = true)
        )
    )
}

private class FakeNewsApi(private val status: String = "ok") : NewsApi {
    override suspend fun sources(category: String) = SourceResponse(
        status = status,
        sources = listOf(
            NewsSource(id = "android-weekly", name = "Android Weekly", description = "Android development news", category = category),
            NewsSource(id = "daily-world", name = "Daily World", description = "Global headlines", category = category)
        ),
        message = if (status == "ok") null else "Invalid API key"
    )

    override suspend fun articles(source: String, query: String?, page: Int, pageSize: Int) = ArticleResponse(
        status = status,
        articles = listOf(Article(title = "Kotlin News", url = "https://example.com", source = ArticleSource("Android Weekly"))),
        message = if (status == "ok") null else "Invalid source"
    )
}
