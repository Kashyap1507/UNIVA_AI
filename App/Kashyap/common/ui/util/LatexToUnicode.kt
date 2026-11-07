package com.kashyap.wizlyai.ui.util

/**
 * Lightweight LaTeX → Unicode conversion for tutor answers.
 *
 * Covers what tutoring answers mostly use (Greek letters, operators, relations,
 * sets, arrows, fractions, roots, sub/superscripts, blackboard bold, accents).
 * Anything unknown is kept readable rather than dropped. See the Phase 3
 * decision in migration/PROGRESS.md for why this isn't KaTeX.
 */
object LatexToUnicode {
    private val symbols =
        mapOf(
            // Greek
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
            "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
            "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ",
            "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
            "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
            "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
            // Big operators and calculus
            "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
            "partial" to "∂", "nabla" to "∇", "infty" to "∞", "lim" to "lim", "max" to "max", "min" to "min",
            "sup" to "sup", "inf" to "inf", "arg" to "arg", "det" to "det", "exp" to "exp", "log" to "log",
            "ln" to "ln", "lg" to "lg", "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot",
            "sec" to "sec", "csc" to "csc", "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
            "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh", "Pr" to "Pr", "gcd" to "gcd", "deg" to "deg",
            "dim" to "dim", "ker" to "ker", "mod" to "mod", "bmod" to "mod",
            // Binary operators
            "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "ast" to "∗", "star" to "⋆",
            "circ" to "∘", "bullet" to "•", "oplus" to "⊕", "otimes" to "⊗", "odot" to "⊙", "wedge" to "∧",
            "vee" to "∨", "land" to "∧", "lor" to "∨", "neg" to "¬", "lnot" to "¬", "setminus" to "∖",
            // Relations
            "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
            "equiv" to "≡", "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝", "ll" to "≪", "gg" to "≫",
            "prec" to "≺", "succ" to "≻", "perp" to "⊥", "parallel" to "∥", "mid" to "∣", "models" to "⊨", "vdash" to "⊢",
            // Sets and logic
            "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃",
            "supseteq" to "⊇", "cup" to "∪", "cap" to "∩", "bigcup" to "⋃", "bigcap" to "⋂", "emptyset" to "∅",
            "varnothing" to "∅", "forall" to "∀", "exists" to "∃", "nexists" to "∄", "top" to "⊤", "bot" to "⊥",
            "therefore" to "∴", "because" to "∵",
            // Arrows
            "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "gets" to "←", "leftrightarrow" to "↔",
            "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "implies" to "⇒", "iff" to "⇔",
            "mapsto" to "↦", "uparrow" to "↑", "downarrow" to "↓", "longrightarrow" to "⟶", "longmapsto" to "⟼",
            // Delimiters and dots
            "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
            "lvert" to "|", "rvert" to "|", "lVert" to "‖", "rVert" to "‖", "vert" to "|", "Vert" to "‖",
            "ldots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱", "dots" to "…",
            // Misc
            "prime" to "′", "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "angle" to "∠",
            "degree" to "°", "checkmark" to "✓", "square" to "□", "blacksquare" to "■", "triangle" to "△",
            "quad" to "  ", "qquad" to "    ", "," to " ", ";" to " ", ":" to " ", "!" to "", " " to " ",
            "{" to "{", "}" to "}", "%" to "%", "$" to "$", "&" to "&", "#" to "#", "_" to "_", "|" to "‖",
        )

    private val blackboard = mapOf('R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ", 'C' to "ℂ", 'P' to "ℙ", 'E' to "𝔼", 'F' to "𝔽")
    private val superscripts =
        mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ',
            'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'j' to 'ʲ',
            'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
            'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ', '*' to '*', '′' to '′',
        )
    private val subscripts =
        mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '−' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ',
            'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ',
            's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
        )

    private val combining = mapOf("hat" to '̂', "bar" to '̄', "overline" to '̅', "vec" to '⃗', "tilde" to '̃', "dot" to '̇', "ddot" to '̈')
    private val passThrough = setOf("text", "textrm", "textbf", "textit", "mathrm", "mathbf", "mathit", "mathsf", "mathtt", "operatorname", "boldsymbol", "mathcal", "emph", "mbox")
    private val dropped = setOf("left", "right", "big", "Big", "bigg", "Bigg", "displaystyle", "textstyle", "limits", "nolimits")

    /** Relations and arrows get spaces around them so they stay readable. */
    private val spaced =
        setOf(
            "leq", "le", "geq", "ge", "neq", "ne", "approx", "equiv", "sim", "simeq", "cong", "propto", "ll", "gg",
            "in", "notin", "subset", "subseteq", "supset", "supseteq", "to", "rightarrow", "leftarrow", "gets",
            "leftrightarrow", "Rightarrow", "Leftarrow", "Leftrightarrow", "implies", "iff", "mapsto", "longrightarrow",
            "mid", "perp", "models", "vdash",
        )

    private val matrixDelimiters =
        mapOf(
            "matrix" to ("(" to ")"), "pmatrix" to ("(" to ")"), "bmatrix" to ("[" to "]"), "Bmatrix" to ("{" to "}"),
            "vmatrix" to ("|" to "|"), "Vmatrix" to ("‖" to "‖"), "smallmatrix" to ("(" to ")"), "cases" to ("{ " to " }"),
        )

    fun convert(tex: String): String = Parser(tex).parseAll().replace(Regex(" {2,}"), " ").trim()

    private class Parser(val s: String) {
        var i = 0

        fun parseAll(): String {
            val out = StringBuilder()
            while (i < s.length) out.append(parseAtom())
            return out.toString()
        }

        /** One unit: a group, a command, a script, or a character. */
        fun parseAtom(): String {
            val c = s[i]
            return when (c) {
                '{' -> parseGroup()
                '}' -> {
                    i++
                    ""
                }
                '\\' -> parseCommand()
                '^' -> {
                    i++
                    script(parseArgument(), superscripts, "^")
                }
                '_' -> {
                    i++
                    script(parseArgument(), subscripts, "_")
                }
                '&' -> {
                    i++
                    "  "
                }
                '~' -> {
                    i++
                    " "
                }
                else -> {
                    i++
                    if (c == '-' ) "−" else c.toString()
                }
            }
        }

        fun parseGroup(): String {
            i++ // {
            val out = StringBuilder()
            while (i < s.length && s[i] != '}') out.append(parseAtom())
            if (i < s.length) i++ // }
            return out.toString()
        }

        /** Argument of a command or script: a group, a command, or one character. */
        fun parseArgument(): String {
            while (i < s.length && s[i] == ' ') i++
            if (i >= s.length) return ""
            return if (s[i] == '{') parseGroup() else parseAtom()
        }

        fun parseCommand(): String {
            i++ // backslash
            if (i >= s.length) return ""
            if (s[i] == '\\') {
                i++
                return "\n"
            }
            val start = i
            if (s[i].isLetter()) {
                while (i < s.length && s[i].isLetter()) i++
            } else {
                i++
            }
            val name = s.substring(start, i)
            // As in TeX, a space after a command word only ends the word ("\\nabla f" → "∇f");
            // keep it before operators so "\\theta - \\alpha" stays "θ − α"
            if (name.first().isLetter()) {
                var j = i
                while (j < s.length && s[j] == ' ') j++
                if (j < s.length && (s[j].isLetterOrDigit() || s[j] in "\\({[")) i = j
            }
            return when {
                name == "frac" || name == "dfrac" || name == "tfrac" -> {
                    val num = parseArgument()
                    val den = parseArgument()
                    "${wrap(num)}/${wrap(den)}"
                }
                name == "sqrt" -> {
                    var index = ""
                    if (i < s.length && s[i] == '[') {
                        val end = s.indexOf(']', i)
                        if (end > i) {
                            index = s.substring(i + 1, end)
                            i = end + 1
                        }
                    }
                    val body = parseArgument()
                    val root = when (index) {
                        "" -> "√"
                        "3" -> "∛"
                        "4" -> "∜"
                        else -> script(index, superscripts, "^") + "√"
                    }
                    root + wrap(body)
                }
                name == "mathbb" -> parseArgument().map { blackboard[it] ?: it.toString() }.joinToString("")
                name in combining -> {
                    val body = parseArgument()
                    if (body.length == 1) body + combining.getValue(name) else "$name($body)"
                }
                name == "binom" -> {
                    val n = parseArgument()
                    val k = parseArgument()
                    "C(${n}, ${k})"
                }
                name == "begin" -> {
                    val env = parseArgument()
                    matrixDelimiters[env]?.let { (open, close) -> matrix(env, open, close) } ?: "\n"
                }
                name == "end" -> {
                    parseArgument() // environment name
                    "\n"
                }
                name in passThrough -> parseArgument()
                name in dropped -> ""
                name in spaced -> " ${symbols.getValue(name)} "
                // Operator words need a gap before their argument: "\\sin x" → "sin x", not "sinx"
                symbols[name] == name && i < s.length && (s[i].isLetterOrDigit() || s[i] == '\\') -> "$name "
                else -> symbols[name] ?: name
            }
        }

        /** "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}" → "(1 2; 3 4)"; cases → "{ a, x > 0; b, otherwise }". */
        fun matrix(
            env: String,
            open: String,
            close: String,
        ): String {
            val endTag = "\\end{$env}"
            val end = s.indexOf(endTag, i).let { if (it < 0) s.length else it }
            val body = s.substring(i, end)
            i = (end + endTag.length).coerceAtMost(s.length)
            val rows =
                body.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }.map { row ->
                    row.split('&').joinToString(if (env == "cases") ", " else " ") { convert(it) }
                }
            return open + rows.joinToString("; ") + close
        }

        fun wrap(value: String): String {
            val v = value.trim()
            return if (v.length <= 1 || v.all { it.isLetterOrDigit() }) v else "($v)"
        }

        fun script(value: String, table: Map<Char, Char>, marker: String): String {
            val v = value.trim()
            if (v.isEmpty()) return ""
            return if (v.all { it in table }) {
                v.map { table.getValue(it) }.joinToString("")
            } else {
                if (v.length == 1) "$marker$v" else "$marker($v)"
            }
        }
    }
}

/**
 * Prepares tutor Markdown for display: converts $$…$$ / \[…\] display maths to
 * fenced "math" blocks and $…$ / \(…\) inline maths to Unicode text. Code
 * blocks and inline code are left untouched; "$5 and $10" is not treated as maths.
 */
object MathMarkdown {
    private val codeFence = Regex("(?s)```.*?(```|$)")
    private val inlineCode = Regex("`[^`\n]*`")
    private val displayDollar = Regex("(?s)\\$\\$(.+?)\\$\\$")
    private val displayBracket = Regex("(?s)\\\\\\[(.+?)\\\\\\]")
    private val inlineParen = Regex("\\\\\\((.+?)\\\\\\)")
    private val inlineDollar = Regex("(?<![\\\\$\\w])\\$(?!\\s)([^$\\n]+?)(?<!\\s)\\$(?![\\w$])")

    fun prepare(markdown: String): String {
        // Protect code first
        val protected = mutableListOf<String>()
        fun protect(text: String, regex: Regex): String =
            regex.replace(text) {
                protected.add(it.value)
                "\u0000${protected.size - 1}\u0000"
            }

        var text = protect(markdown, codeFence)
        text = protect(text, inlineCode)

        val display: (MatchResult) -> String = { m -> "\n\n```math\n${LatexToUnicode.convert(m.groupValues[1])}\n```\n\n" }
        text = displayDollar.replace(text, display)
        text = displayBracket.replace(text, display)
        val inline: (MatchResult) -> String = { m -> escapeMarkdown(LatexToUnicode.convert(m.groupValues[1])) }
        text = inlineParen.replace(text, inline)
        text = inlineDollar.replace(text) { m ->
            val body = m.groupValues[1]
            // Currency guard: "$5 and $10" has a digit right after the opening $
            if (isCurrency(body)) m.value else inline(m)
        }

        return Regex("\u0000(\\d+)\u0000").replace(text) { protected[it.groupValues[1].toInt()] }
    }

    /** One-line preview text: maths as Unicode, Markdown markers and line breaks removed. */
    fun plain(markdown: String): String {
        var text = displayDollar.replace(markdown) { LatexToUnicode.convert(it.groupValues[1]) }
        text = displayBracket.replace(text) { LatexToUnicode.convert(it.groupValues[1]) }
        text = inlineParen.replace(text) { LatexToUnicode.convert(it.groupValues[1]) }
        text = inlineDollar.replace(text) { m ->
            val body = m.groupValues[1]
            if (isCurrency(body)) m.value else LatexToUnicode.convert(body)
        }
        return text.replace(Regex("[*`#>]+|(?m)^\\s*[-+]\\s+"), "").replace(Regex("\\s+"), " ").trim()
    }

    /** "$5 or $10" style text: starts with a digit, has words or spaces, and no maths syntax. A lone number like $2$ is maths. */
    private fun isCurrency(body: String): Boolean =
        body.first().isDigit() && !body.all { it.isDigit() || it == '.' || it == ',' } &&
            !body.any { it == '\\' || it == '^' || it == '_' || it == '=' }

    private fun escapeMarkdown(text: String): String = text.replace("*", "\\*").replace("_", "\\_")
}
