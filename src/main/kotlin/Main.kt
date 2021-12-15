interface Expr {
    fun eval(context: List<Map<String, Any>>): Any
}

enum class Primitive(val s: String) : Expr {
    cons("cons"), cdr("cdr"), isNull("null?"), eq("eq?"), isAtom("atom?"), isZero("zero?"), incr("incr"), decr("decr"), isNumber(
        "number?"
    );

    override fun eval(context: List<Map<String, Any>>): Any {
        TODO("Not yet implemented")
    }

    fun evalArgs(args: List<Any>): Any {
        return when (s) {
            "cons" -> {
                Pair(args[0], args[1])
            }
            "cdr" -> {
                (args as Pair<*, *>).second!!
            }
            "null?" -> {
                args[0] == "()" //TODO
            }
            "eq?" -> {
                args[0] == args[1]
            }
            "atom?" -> {
                args[0] !is Pair<*, *>
            }
            "zero?" -> {
                (args[0] as Number).toInt() == 0
            }
            "incr" -> {
                (args[0] as Number).toInt() + 1
            }
            "decr" -> {
                (args[0] as Number).toInt() - 1
            }
            "number?" -> {
                args[0] is Number
            }
            else -> {
                throw RuntimeException("Unknown Primitive: $s")
            }
        }
    }
}

class NonPrimitive(
    val innerContext: List<Map<String, Any>>, val body: Expr, val argNames: List<String>
) : Expr {

    override fun eval(context: List<Map<String, Any>>): Any {
        TODO("Not yet implemented")
    }

    fun evalArgs(argValues: List<Any>): Any {
        val zip = argNames.zip(argValues).toMap()
        return body.eval(listOf(zip) + innerContext)
    }
}

abstract class Atom : Expr {

}


abstract class SExp : Expr {}


class Identifier(val v: String) : Atom() {
    override fun eval(context: List<Map<String, Any>>): Any {
        for (map in context) {
            map[v]?.let {
                return it
            }
        }
        throw RuntimeException("unknown identifier: $v")
    }
}

class Const(val v: String) : SExp() {
    override fun eval(context: List<Map<String, Any>>): Any {
        for (map in context) {
            map[v]?.let {
                return it
            }
        }
        return if (v.toIntOrNull() != null) {
            v.toInt()
        } else if (v == "#t" || v == "else") {
            true
        } else if (v == "#f") {
            false
        } else {
            Primitive.values().firstOrNull { it.s == v } ?: throw RuntimeException("Unknown Const: $v")
        }
    }

    override fun toString(): String {
        return v
    }
}

class Quote(val v: Expr) : SExp() {
    val op = "quote"
    override fun eval(context: List<Map<String, Any>>): Any {
        return v.toString()
    }

    override fun toString(): String {
        return "($op $v)"
    }


}

class Cond(val branchs: List<Branch>) : SExp() {
    val op = "cond"

    class Branch(
        val question: Expr, val answer: Expr
    ) {

        override fun toString(): String {
            return "($question $answer)"
        }
    }

    override fun eval(context: List<Map<String, Any>>): Any {
        branchs.forEach { branch ->
            val result = branch.question.eval(context)
            if (result is Boolean && result) {
                return branch.answer.eval(context)
            }
        }
        throw RuntimeException("no answer")
    }

    override fun toString(): String {
        return "($op ${branchs.map { it.toString() }.joinToString(" ")})"
    }


}

class Lambda(val args: List<String>, val body: Expr) : SExp() {
    val op = "lambda"

    override fun eval(context: List<Map<String, Any>>): Any {
        return NonPrimitive(context, body, args)
    }

    override fun toString(): String {
        return "($op (${args.joinToString(" ")}) $body)"
    }


}

class Application(val func: Expr, val args: List<Expr>) : SExp() {
    val op = ""
    override fun eval(context: List<Map<String, Any>>): Any {
        val f = func.eval(context)
        val evaldArgs = args.map { it.eval(context) }

        return when (f) {
            is Primitive -> {
                f.evalArgs(evaldArgs)
            }
            is NonPrimitive -> {
                f.evalArgs(evaldArgs)
            }
            else -> {
                throw RuntimeException("unknown function: $f")
            }
        }
    }

    override fun toString(): String {
        return "($func ${args.joinToString(" ")})"
    }


}

fun main(args: Array<String>) {
    val tokens = string2Tokens(testArg1)
    println(tokens)
    val structure = tokens2Structure(tokens)
    val ast = structure[0]
    println(ast)
    val any2Expr = any2Expr(ast)
    println(any2Expr)
    println(any2Expr.eval(emptyList()))
}

fun any2Expr(ast: Any): Expr {
    if (ast is List<*>) {
        return when (ast[0]) {
            "quote" -> {
                Quote(any2Expr(ast[1]!!))
            }
            "cond" -> {
                Cond(ast.drop(1).map {
                    it as List<*>
                    Cond.Branch(any2Expr(it[0]!!), any2Expr(it[1]!!))
                })
            }
            "lambda" -> {
                Lambda(ast[1] as List<String>, any2Expr(ast[2]!!))
            }
            else -> {
                Application(any2Expr(ast[0]!!), ast.drop(1).map { any2Expr(it!!) })
            }
        }
    } else {
        val v = ast.toString()
        return Const(v)
    }
}

val testArg = """
    ((lambda (x) (incr x)) 7)
""".trimIndent()

val testArg1 = """
   (((lambda (le)
            ((lambda (f) (f f))
             (lambda (f)
               (le (lambda (x y) ((f f) x y))))))
          (lambda (f)
            (lambda (x y)
              (cond
                ((zero? x) y)
                (else (f (decr x) (incr y)))))))
         10 22)
""".trimIndent()

typealias Token = String

const val START_TOKEN: Token = "("
const val END_TOKEN: Token = ")"
const val DELIMITER_TOKEN: Token = " "


const val START = '('
const val END = ')'

fun string2Tokens(s: String): List<Token> {
    val tokens = ArrayList<Token>()
    var buf = ""
    s.forEach { c ->
        when (c) {
            START -> {
                tokens.add(START_TOKEN)
            }
            END -> {
                if (!buf.isBlank()) {
                    tokens.add(buf)
                    buf = ""
                }
                tokens.add(END_TOKEN)
            }
            ' ', '\t', '\n', '\r' -> {
                if (!buf.isBlank()) {
                    tokens.add(buf)
                    buf = ""
                }
                if (tokens.last() != DELIMITER_TOKEN) {
                    tokens.add(DELIMITER_TOKEN)
                }
            }
            else -> {
//                println("$c,${c.code}")
                buf += c
            }
        }
    }
    return tokens.filter { it != DELIMITER_TOKEN }
}

fun tokens2Structure(tokens: List<Token>): List<Any> {
    var stack = emptyList<Any>()
    tokens.forEach { token ->
        when (token) {
            START_TOKEN -> {
                stack = stack + token
            }
            END_TOKEN -> {
                val startIndex = stack.lastIndexOf(START_TOKEN)
                if (startIndex == -1) {
                    throw RuntimeException("")
                } else {
                    val tail = stack.subList(startIndex, stack.size).drop(1)
                    stack = stack.dropLast(stack.size - startIndex)
                    stack = stack + (tail as Any)
                }
            }
            else -> {
                stack = stack + token
            }
        }
    }
    return stack;
}