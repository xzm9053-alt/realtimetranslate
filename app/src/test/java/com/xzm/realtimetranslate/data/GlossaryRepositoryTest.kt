package com.xzm.realtimetranslate.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GlossaryRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `add update and toggle glossary terms`() = runBlocking {
        val file = File(temp.root, "glossary.json")
        val repo = GlossaryRepository(file)

        repo.add("LLM", "大语言模型", "技术名词")
        assertEquals(1, repo.entries.value.size)
        val term = repo.entries.value.first()
        assertEquals("LLM", term.source)
        assertEquals("大语言模型", term.target)
        assertTrue(term.enabled)

        // Update
        repo.update(term.copy(target = "大模型"))
        assertEquals("大模型", repo.entries.value.first().target)

        // Toggle
        repo.toggle(term.id, false)
        assertFalse(repo.entries.value.first().enabled)

        // Prompt should exclude disabled terms
        assertTrue(repo.getEnabledTermsPrompt().isEmpty())

        // Toggle back
        repo.toggle(term.id, true)
        val prompt = repo.getEnabledTermsPrompt()
        assertTrue(prompt.contains("LLM"))
        assertTrue(prompt.contains("大模型"))

        // Delete
        repo.delete(term.id)
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun `importText parses multiple formats and merges`() = runBlocking {
        val file = File(temp.root, "glossary.json")
        val repo = GlossaryRepository(file)

        val text = """
            # 注释行应该被忽略
            HP	生命值	游戏术语
            MP -> 魔法值
            LiveTranslate => 实时翻译

            XP, 经验值, 游戏
        """.trimIndent()

        val result = repo.importText(text)
        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull())

        val entries = repo.entries.value
        assertEquals(4, entries.size)

        val hp = entries.find { it.source == "HP" }
        assertEquals("生命值", hp?.target)
        assertEquals("游戏术语", hp?.note)

        val mp = entries.find { it.source == "MP" }
        assertEquals("魔法值", mp?.target)

        val lt = entries.find { it.source == "LiveTranslate" }
        assertEquals("实时翻译", lt?.target)
    }

    @Test
    fun `exportTsv outputs valid TSV`() = runBlocking {
        val file = File(temp.root, "glossary.json")
        val repo = GlossaryRepository(file)

        repo.add("Apple", "苹果", "水果")
        repo.add("Banana", "香蕉")

        val tsv = repo.exportTsv()
        assertTrue(tsv.contains("Apple\t苹果\t水果"))
        assertTrue(tsv.contains("Banana\t香蕉"))
    }
}
