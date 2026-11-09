package com.sprintstart.sprintstartbackend.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Turns the design rules into checks on the production code, each against its own list of
 * known violations (see [KnownViolations]).
 */
class CodeStructureTest {
    private val scope = Konsist.scopeFromProduction()
    private val controllers = scope.classes().filter { it.hasAnnotationWithName("RestController") }
    private val functions: List<OwnedFunction> =
        scope.functions().filter { it.isTopLevel }.map { OwnedFunction(owner = null, declaration = it) } +
            scope.classesAndInterfacesAndObjects().flatMap { owner ->
                owner.functions(includeNested = true, includeLocal = false).map { OwnedFunction(owner.name, it) }
            }

    @Test
    fun `controllers do not reach into repositories`() {
        val violations = controllers.flatMap { controller ->
            constructorTypes(controller)
                .filter { it.endsWith("Repository") }
                .map { "${controller.name} uses $it" }
        }

        KnownViolations("controller-repositories").assertMatches(
            violations,
            "A controller calls one service; the service owns the repository.",
        )
    }

    /**
     * Every module erases the user data it owns when a user is deleted. An entity outside the user
     * module that holds a user id is covered when its repository is injected into a
     * `*UserDeletedListener`; the user module deletes its own rows directly.
     */
    @Test
    fun `every entity holding a user id is erased when the user is deleted`() {
        val erasedRepositories = scope
            .classes()
            .filter { it.name.endsWith("UserDeletedListener") }
            .flatMap { constructorTypes(it) }
            .toSet()
        val missing = scope
            .classes()
            .filter { it.hasAnnotationWithName("Entity") }
            .filterNot {
                it.packagee
                    ?.name
                    .orEmpty()
                    .startsWith(USER_MODULE_PACKAGE)
            }.filter { entity -> propertyNames(entity).any { it in USER_ID_PROPERTIES } }
            .filterNot { it.name in KEPT_ON_USER_DELETION }
            .filterNot { "${it.name}Repository" in erasedRepositories }
            .map { it.name }

        assertTrue(missing.isEmpty()) {
            "Entities holding a user id that no *UserDeletedListener erases: $missing. Delete them in a " +
                "listener for UserDeletedEvent, or add them to KEPT_ON_USER_DELETION with the reason."
        }
    }

    @Test
    fun `controller endpoints do not branch`() {
        val violations = controllers.flatMap { controller ->
            controller
                .functions()
                .filter { hasBranching(it) }
                .map { "${controller.name}.${it.name}" }
        }

        KnownViolations("controller-branching").assertMatches(
            violations,
            "Two modes are two endpoints; a decision belongs in a service.",
        )
    }

    @Test
    fun `function names do not join two responsibilities`() {
        val violations = functions
            .filter { JOINED_NAME.containsMatchIn(responsibilityPart(it.declaration.name)) }
            .filterNot { it.isIn("repository") }
            .map { it.location }

        KnownViolations("joined-function-names").assertMatches(
            violations,
            "A function whose name needs `And`/`Or` does two things; split it.",
        )
    }

    @Test
    fun `services and controllers take typed ids`() {
        val violations = functions
            .filter { it.isIn("service") || it.isIn("controller") }
            .flatMap { function -> rawIdParameters(function.declaration).map { "${function.location}($it)" } }

        KnownViolations("raw-id-parameters").assertMatches(
            violations,
            "Wrap the id in a value class (`@JvmInline value class ProjectId(val value: UUID)`).",
        )
    }

    private fun propertyNames(declaration: KoClassDeclaration): List<String> =
        declaration.properties().map { it.name } +
            declaration.primaryConstructor
                ?.parameters
                .orEmpty()
                .map { it.name }

    private fun constructorTypes(declaration: KoClassDeclaration): List<String> =
        declaration.primaryConstructor
            ?.parameters
            .orEmpty()
            .map { it.type.name }

    private fun hasBranching(function: KoFunctionDeclaration): Boolean =
        BRANCH.containsMatchIn(function.text.replace(STRING_LITERAL, "\"\""))

    /** The name without a lookup key (`…ByOwnerAndName`) or an idiomatic fallback (`…OrNull`). */
    private fun responsibilityPart(name: String): String =
        name.replace(LOOKUP_KEY, "").replace(KOTLIN_IDIOM_SUFFIX, "")

    private fun rawIdParameters(function: KoFunctionDeclaration): List<String> =
        function.parameters
            .filter { it.name.endsWith("Id") && it.type.name.removeSuffix("?") in RAW_ID_TYPES }
            .map { "${it.name}: ${it.type.name}" }

    /** A function with the class, interface or object that declares it, `null` for a top-level one. */
    private class OwnedFunction(
        val owner: String?,
        val declaration: KoFunctionDeclaration,
    ) {
        /** `File.kt: Owner.function`, stable while unrelated lines change. */
        val location: String
            get() {
                val qualifier = owner?.let { "$it." }.orEmpty()
                return "${declaration.containingFile.nameWithExtension}: $qualifier${declaration.name}"
            }

        fun isIn(layer: String): Boolean = "/$layer/" in declaration.containingFile.path.replace(BACKSLASH, "/")
    }

    private companion object {
        const val BACKSLASH = "\\"
        val JOINED_NAME = Regex("""[a-z0-9](And|Or)[A-Z]""")
        val LOOKUP_KEY = Regex("""By[A-Z]\w*$""")

        // `parseOrNull`, `getOrCreate`: one responsibility with a fallback, as in the standard library.
        val KOTLIN_IDIOM_SUFFIX = Regex("""Or(Null|Else|Default|Empty|Throw|Create|Put)$""")
        val BRANCH = Regex("""\bif\s*\(|\bwhen\s*[({]|\?:""")
        val STRING_LITERAL = Regex(""""(?:\\.|[^"\\])*"""")
        val RAW_ID_TYPES = setOf("UUID", "String")
        const val USER_MODULE_PACKAGE = "com.sprintstart.sprintstartbackend.user"
        val USER_ID_PROPERTIES = setOf("userId", "hireId", "uploaderId")

        /** Entities that deliberately outlive the user their id names, with the reason. */
        val KEPT_ON_USER_DELETION = mapOf(
            "UploadedArtifact" to "uploaded documents are the team's knowledge base; the uploader stays as provenance",
        )
    }
}
