package com.marrakechguide.feature.explore

import com.marrakechguide.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreScreenTest {

    @Test
    fun `category filters use canonical content keys`() {
        assertTrue(EXPLORE_CATEGORY_FILTERS.contains("landmark"))
        assertTrue(EXPLORE_CATEGORY_FILTERS.contains("historic_site"))
        assertFalse(EXPLORE_CATEGORY_FILTERS.contains("Landmarks"))
        assertFalse(EXPLORE_CATEGORY_FILTERS.contains("Riads"))
    }

    @Test
    fun `filterExplorePlaces applies category key exactly as stored in content`() {
        val places = listOf(
            place(id = "koutoubia", category = "landmark"),
            place(id = "majorelle", category = "garden"),
            place(id = "bahia", category = "historic_site")
        )

        val filtered = filterExplorePlaces(
            places = places,
            selectedCategory = "landmark",
            rawQuery = ""
        )

        assertEquals(listOf("koutoubia"), filtered.map { it.id })
    }

    @Test
    fun `filterExplorePlaces treats all category case-insensitively`() {
        val places = listOf(
            place(id = "koutoubia", category = "landmark"),
            place(id = "majorelle", category = "garden")
        )

        val filtered = filterExplorePlaces(
            places = places,
            selectedCategory = "all",
            rawQuery = ""
        )

        assertEquals(listOf("koutoubia", "majorelle"), filtered.map { it.id })
    }

    @Test
    fun `filterExplorePlaces treats all category with surrounding whitespace as all`() {
        val places = listOf(
            place(id = "koutoubia", category = "landmark"),
            place(id = "majorelle", category = "garden")
        )

        val filtered = filterExplorePlaces(
            places = places,
            selectedCategory = "  ALL  ",
            rawQuery = ""
        )

        assertEquals(listOf("koutoubia", "majorelle"), filtered.map { it.id })
    }

    @Test
    fun `filterExplorePlaces trims query before matching`() {
        val places = listOf(
            place(id = "koutoubia", category = "landmark", name = "Koutoubia Mosque"),
            place(id = "majorelle", category = "garden", name = "Majorelle Garden")
        )

        val filtered = filterExplorePlaces(
            places = places,
            selectedCategory = null,
            rawQuery = "   koutoubia   "
        )

        assertEquals(listOf("koutoubia"), filtered.map { it.id })
    }

    @Test
    fun `displayCategoryLabel converts stored key to readable label`() {
        assertEquals("Historic site", displayCategoryLabel("historic_site"))
        assertEquals("Restaurant", displayCategoryLabel("restaurant"))
    }

    @Test
    fun `isExploreCategorySelected normalizes all and category casing`() {
        assertTrue(isExploreCategorySelected(category = "All", selectedCategory = null))
        assertTrue(isExploreCategorySelected(category = "All", selectedCategory = "  all  "))
        assertTrue(isExploreCategorySelected(category = "landmark", selectedCategory = "LANDMARK"))
        assertFalse(isExploreCategorySelected(category = "landmark", selectedCategory = "garden"))
    }

    private fun place(id: String, category: String, name: String = id): Place {
        return Place(
            id = id,
            name = name,
            category = category
        )
    }
}
