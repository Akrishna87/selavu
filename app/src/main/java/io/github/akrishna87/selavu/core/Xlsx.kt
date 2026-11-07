package io.github.akrishna87.selavu.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The smallest Excel workbook (.xlsx) writer and reader that does the job, so the app needs no
 * spreadsheet library. Writing makes one sheet per table, with a bold first row; numbers go in
 * as numbers so Excel can add them up. Reading returns every sheet as rows of text, whether the
 * file came from here, Excel or Google Sheets.
 */
object Xlsx {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** A sheet's name and its rows; the first row is the header. A cell is text, a number, or null. */
    data class Sheet(val name: String, val rows: List<List<Any?>>)

    fun write(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("[Content_Types].xml", buildString {
                append(XML_HEAD)
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach {
                    append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                append("</Types>")
            })
            put("_rels/.rels", XML_HEAD + """<Relationships xmlns="$RELS"><Relationship Id="rId1" Type="$DOC_RELS/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append(XML_HEAD)
                append("""<workbook xmlns="$MAIN" xmlns:r="$DOC_RELS"><sheets>""")
                sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append(XML_HEAD)
                append("""<Relationships xmlns="$RELS">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="$DOC_RELS/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="$DOC_RELS/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            })
            put("xl/styles.xml", XML_HEAD + STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return out.toByteArray()
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append(XML_HEAD)
        append("""<worksheet xmlns="$MAIN">""")
        // Columns wide enough to read without resizing.
        val cols = s.rows.maxOfOrNull { it.size } ?: 0
        if (cols > 0) {
            append("<cols>")
            for (c in 0 until cols) {
                val longest = s.rows.maxOf { (it.getOrNull(c)?.toString()?.length ?: 0) }
                append("""<col min="${c + 1}" max="${c + 1}" width="${(longest + 2).coerceIn(8, 40)}" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, v ->
                val ref = column(c) + (r + 1)
                val style = if (r == 0) """ s="1"""" else ""
                when (v) {
                    null -> {}
                    is Number -> append("""<c r="$ref"$style><v>$v</v></c>""")
                    else -> append("""<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** Every sheet of a workbook, by name, as rows of text (missing cells are empty). */
    fun read(bytes: ByteArray): List<Pair<String, List<List<String>>>> {
        val files = unzip(bytes)
        val shared = files["xl/sharedStrings.xml"]?.let { xml ->
            elements(parse(xml), "si").map { si -> elements(si, "t").joinToString("") { it.textContent } }
        } ?: emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { xml ->
            elements(parse(xml), "Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
        } ?: emptyMap()
        val workbook = parse(files["xl/workbook.xml"] ?: error("Not an Excel workbook"))
        return elements(workbook, "sheet").mapNotNull { sheet ->
            val rid = (0 until sheet.attributes.length).map { sheet.attributes.item(it) }
                .firstOrNull { it.localName == "id" || it.nodeName.endsWith(":id") }?.nodeValue
            val target = rels[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val xml = files[path] ?: return@mapNotNull null
            sheet.getAttribute("name") to rows(parse(xml), shared)
        }
    }

    private fun rows(doc: Element, shared: List<String>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        for (row in elements(doc, "row")) {
            val cells = sortedMapOf<Int, String>()
            var next = 0
            for (c in elements(row, "c")) {
                val ref = c.getAttribute("r")
                val col = if (ref.isNotEmpty()) columnIndex(ref) else next
                next = col + 1
                val v = elements(c, "v").firstOrNull()?.textContent ?: ""
                cells[col] = when (c.getAttribute("t")) {
                    "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> elements(c, "t").joinToString("") { it.textContent }
                    "str", "b", "e" -> v
                    else -> number(v)
                }
            }
            // Row numbers can skip; keep the rows in order and leave out blank ones.
            if (cells.isNotEmpty()) out += (0..cells.lastKey()).map { cells[it] ?: "" }
        }
        return out
    }

    /** A number as plain text: "1200", "250.5"; rounded to cents, as amounts are. */
    private fun number(v: String): String = runCatching {
        val d = BigDecimal(v)
        (if (d.scale() > 2) d.setScale(2, java.math.RoundingMode.HALF_UP) else d).stripTrailingZeros().toPlainString()
    }.getOrDefault(v)

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val data = zip.readBytes()
                total += data.size
                // A spreadsheet of a household's money is tiny; refuse anything that unpacks huge.
                require(total < 50_000_000) { "The file is too large" }
                files[e.name.removePrefix("/")] = data
            }
        }
        return files
    }

    private fun parse(xml: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        // No DTDs or outside entities: a workbook never needs them.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }

    private fun elements(parent: Element, local: String): List<Element> {
        val list = parent.getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    /** 0 → "A", 25 → "Z", 26 → "AA". */
    fun column(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.append('A' + r)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** "C7" → 2. */
    private fun columnIndex(ref: String): Int =
        ref.takeWhile { it.isLetter() }.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    private fun esc(s: String) = buildString {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (ch < ' ' && ch != '\n' && ch != '\t') append(' ') else append(ch)
            }
        }
    }

    private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val DOC_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val STYLES = """<styleSheet xmlns="$MAIN"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
