package gift.nan.terminal.util

import java.io.Flushable
import java.util.*

class Curses {
    private val stack = Stack<Any>()
    private val sv = Array<Any>(26) {}
    private val dv = Array<Any>(26) {}

    private enum class IfThenStatus {
        None, If, Then, Else;

        class Handler(val result: IfThenStatus, vararg val requirements: IfThenStatus)

        companion object {
            private val ifThanHandlers = mapOf(
                '?' to Handler(If, None),
                't' to Handler(Then, If, Else),
                'e' to Handler(Else, Then),
                ';' to Handler(None, Then, Else)
            )

            fun handle(flag: Char, old: IfThenStatus): IfThenStatus? {
                val handler = ifThanHandlers[flag] ?: return null
                check(handler.requirements.any { it == old })
                return handler.result
            }
        }

    }

    companion object {
        fun tputs(cap: String, vararg params: Any): String = instance.tputs(cap, *params)
        fun tputs(out: Appendable, cap: String, vararg params: Any) = instance.tputs(out, cap, *params)
        private val instance = Curses()

        private val binaryOperationHandler = mapOf<Char, Int.(Int) -> Any>(
            '+' to Int::plus,
            '-' to Int::minus,
            '*' to Int::times,
            '/' to Int::div,
            'm' to Int::rem,
            '&' to Int::and,
            '|' to Int::or,
            '^' to Int::xor,
            '=' to Int::equals,
            '>' to { o -> this > o },
            '<' to { o -> this < o },
            'A' to { o -> this != 0 && o != 0 },
            '0' to { o -> this != 0 || o != 0 }
        )
        private val unaryOperationHandler = mapOf<Char, (Int) -> Any>(
            '!' to { i -> i == 0 },
            '~' to Int::inv
        )
        private val escapeChars = mapOf(
            'e' to 27.toChar(), 'E' to 27.toChar(),
            'n' to '\n', 'r' to '\r', 't' to '\t', 'b' to '\b', 'f' to 12.toChar(),
            's' to ' ', ':' to ':', '^' to '^', '\\' to '\\'
        )
    }

    fun tputs(cap: String, vararg params: Any): String = buildString { tputs(this, cap, *params) }
    fun tputs(out: Appendable, cap: String, vararg params: Any) {
        val len = cap.length
        var increase = 0
        var index = 0
        var exec = true
        var ifTh = IfThenStatus.None
        stack.clear()
        while (index < len) {
            var ch = cap[index++]
            when (ch) {
                '\\' -> cap[index++].apply {
                    when (this) {
                        in '0'..'7' -> {
                            var v = this - '0'
                            repeat(2) {
                                val c = cap[index++]
                                check(c in '0'..'7')
                                v = v * 8 + (c - '0')
                            }
                            out.append(v.toChar())
                        }
                        else -> escapeChars[this].let { ec ->
                            check(ec != null)
                            if (exec || this == 'n') out.append(ec)
                        }
                    }
                }
                '^' -> cap[index++].let { if (exec) out.append((it - '@').toChar()) }
                '%' -> {
                    ch = cap[index++]
                    when (ch) {
                        '%' -> if (exec) out.append('%')
                        'p' -> cap[index++].let {
                            if (exec) stack += params[it - '1'].let { x ->
                                if (increase > 0) x.toInt() + increase else x
                            }
                        }
                        'P' -> cap[index++].let {
                            when (it) {
                                in 'a'..'z' -> if (exec) dv[ch - 'a'] = stack.pop()
                                in 'A'..'Z' -> if (exec) sv[ch - 'A'] = stack.pop()
                                else -> throw IllegalArgumentException()
                            }
                        }
                        'g' -> cap[index++].let {
                            when (it) {
                                in 'a'..'z' -> if (exec) stack += dv[ch - 'a']
                                in 'A'..'Z' -> if (exec) stack += sv[ch - 'A']
                                else -> throw IllegalArgumentException()
                            }
                        }
                        '\'' -> cap[index++].let {
                            if (exec) stack += ch.toInt()
                            check(cap[index++] == '\'')
                        }
                        '{' -> cap.indexOf('}', index).let {
                            if (exec) stack += cap.substring(index, it).toInt(10)
                            index = it + 1
                        }
                        'l' -> if (exec) stack += stack.pop().toString().length
                        'i' -> increase++
                        'd' -> out.append(stack.pop().toInt().toString())
                        else -> {
                            val nextStatus =
                                IfThenStatus.handle(ch, ifTh)
                            if (nextStatus != null) {
                                ifTh = nextStatus
                                when (nextStatus) {
                                    IfThenStatus.Then -> exec = stack.pop().toInt() != 0
                                    IfThenStatus.Else -> exec = !exec
                                    IfThenStatus.None -> exec = true
                                }
                            } else if (exec) {
                                val v1 = stack.pop().toInt()
                                val unaryFunc = unaryOperationHandler[ch]
                                if (unaryFunc != null) {
                                    unaryFunc(v1)
                                } else {
                                    val binaryFunc = binaryOperationHandler[ch]
                                    checkNotNull(binaryFunc)
                                    binaryFunc.invoke(stack.pop().toInt(), v1)
                                }
                            }
                        }
                    }
                }
                '$' -> when {
                    index < len && cap[index] == '<' -> {
                        var n: Long = 0
                        while (true) {
                            ch = cap[++index]
                            if (ch == '>') break
                            if (ch in '0'..'9') n = n * 10 + (ch - '0')
                            else check(ch == '/' || ch == '*')
                        }
                        index++
                        if (out is Flushable) out.flush()
                        Thread.sleep(n)
                    }
                    exec -> out.append(ch)
                }
                else -> if (exec) out.append(ch)
            }
        }
    }

    private fun Any.toInt() = when (this) {
        is Number -> toInt()
        is Boolean -> if (this) 1 else 0
        else -> toString().toInt(10)
    }
}

interface C {

}