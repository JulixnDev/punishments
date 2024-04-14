package live.einfachgustaf.punishments.commands

import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import live.einfachgustaf.punishments.Punishments
import net.axay.kspigot.commands.internal.ArgumentTypeUtils
import java.util.concurrent.CompletableFuture

private class DslAnnotations {
    class TopLevel {
        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
        @DslMarker
        annotation class NodeDsl
    }

    class NodeLevel {
        @DslMarker
        annotation class RunsDsl

        @DslMarker
        annotation class SuggestsDsl
    }
}

/**
 * An argument resolver extracts the argument value out of the current [CommandContext].
 */
typealias ArgumentResolver<S, T> = CommandContext<S>.() -> T

@PublishedApi
internal val argumentCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The simple argument builder is a variant of an [ArgumentCommandBuilder] lambda function
 * that supports [ArgumentResolver] (passed as `it`).
 */
typealias SimpleArgumentBuilder<Source, T> = ArgumentCommandBuilder<Source, T>.(argument: ArgumentResolver<Source, T>) -> Unit

@DslAnnotations.TopLevel.NodeDsl
abstract class CommandBuilder<Source : CommandSource, Builder : ArgumentBuilder<Source, Builder>> {

    @PublishedApi
    internal abstract val builder: Builder

    @PublishedApi
    internal val children = ArrayList<CommandBuilder<Source, *>>()

    private val onToBrigadierBuilders = ArrayList<Builder.() -> Unit>()

    /**
     * Adds execution logic to this de.hglabor.geschwindigkeit.common.command. The place where this function
     * is called matters, as this defines for which path in the de.hglabor.geschwindigkeit.common.command tree
     * this executor should be called.
     *
     * possible usage:
     * ```kt
     * de.hglabor.geschwindigkeit.common.command("mycommand") {
     *     // defining runs in the body:
     *     runs { }
     *
     *     // calling runs as an infix function directly after literal or argument:
     *     literal("subcommand") runs { }
     * }
     * ```
     *
     * Note that this function will always return 1 as the exit code.
     *
     * @see com.mojang.brigadier.builder.ArgumentBuilder.executes
     */
    @DslAnnotations.NodeLevel.RunsDsl
    inline infix fun runs(crossinline block: CommandContext<Source>.() -> Unit): CommandBuilder<Source, Builder> {
        val previousCommand = builder.command
        builder.executes {
            previousCommand?.run(it)
            block(it)
            1
        }
        return this
    }

    /**
     * Does the same as [runs] (see its docs for more information), but launches the de.hglabor.geschwindigkeit.common.command
     * logic in an async coroutine.
     *
     * @see runs
     */
    @DslAnnotations.NodeLevel.RunsDsl
    inline infix fun runsAsync(crossinline block: suspend CommandContext<Source>.() -> Unit) =
        runs {
            argumentCoroutineScope.launch {
                block(this@runs)
            }
        }


    /**
     * Adds a new subcommand / literal to this de.hglabor.geschwindigkeit.common.command.
     *
     * possible usage:
     * ```kt
     * de.hglabor.geschwindigkeit.common.command("mycommand") {
     *     literal("subcommand") {
     *         // the body of the subcommand
     *     }
     * }
     * ```
     *
     * @param name the name of the subcommand
     */
    @DslAnnotations.TopLevel.NodeDsl
    inline fun literal(name: String, builder: LiteralCommandBuilder<Source>.() -> Unit = {}) =
        LiteralCommandBuilder<Source>(name).apply(builder).also { children += it }

    /**
     * Adds a new argument to this de.hglabor.geschwindigkeit.common.command. This variant of the argument function allows you to specify
     * the [ArgumentType] in the classical Brigadier way.
     *
     * @param name the name of the argument - This will be displayed to the player, if there is enough room for the
     * tooltip.
     * @param type the type of the argument - There are predefined types like `StringArgumentType.string()` or
     * `IdentifierArgumentType.identifier()`. You can also pass a lambda, as [ArgumentType] is a functional
     * interface. For simple types, consider using the `inline reified` version of this function instead.
     */
    @DslAnnotations.TopLevel.NodeDsl
    inline fun <reified T> argument(
        name: String,
        type: ArgumentType<T>,
        builder: SimpleArgumentBuilder<Source, T> = {},
    ) =
        ArgumentCommandBuilder<Source, T>(name, type)
            .apply { builder { getArgument(name, T::class.java) } }
            .also { children += it }

    /**
     * Adds a new argument to this de.hglabor.geschwindigkeit.common.command. This variant of the argument function you to specifiy the
     * argument parse logic using a Kotlin lambda function ([parser]).
     *
     * @param name the name of the argument - This will be displayed to the player, if there is enough room for the
     * tooltip.
     * @param parser gives you a [StringReader], which allows you to parse the input of the user - you should return a
     * value of the given type [T], which will be the argument value
     */
    @DslAnnotations.TopLevel.NodeDsl
    inline fun <reified T> argument(
        name: String,
        crossinline parser: (StringReader) -> T,
        builder: SimpleArgumentBuilder<Source, T> = {},
    ) =
        ArgumentCommandBuilder<Source, T>(name) { parser(it) }
            .apply { builder { getArgument(name, T::class.java) } }
            .also { children += it }

    /**
     * Adds a new argument to this de.hglabor.geschwindigkeit.common.command. The [ArgumentType] will be resolved using the reified
     * type [T]. For a list of supported types, have a look at [ArgumentTypeUtils.fromReifiedType], as it is
     * the function used by this builder function.
     *
     * @param name the name of the argument - This will be displayed to the player, if there is enough room for the
     * tooltip.
     */
    @DslAnnotations.TopLevel.NodeDsl
    inline fun <reified T> argument(name: String, builder: SimpleArgumentBuilder<Source, T> = {}) =
        ArgumentCommandBuilder<Source, T>(name, ArgumentTypeUtils.fromReifiedType())
            .apply { builder { getArgument(name, T::class.java) } }
            .also { children += it }

    /**
     * Specifies that the given predicate must return true for the [Source]
     * in order for it to be able to execute this part of the de.hglabor.geschwindigkeit.common.command tree. Use
     * this function on the root de.hglabor.geschwindigkeit.common.command node to secure the whole de.hglabor.geschwindigkeit.common.command.
     */
    @DslAnnotations.NodeLevel.RunsDsl
    fun requires(predicate: (source: Source) -> Boolean): CommandBuilder<Source, Builder> {
        builder.requires(builder.requirement.and(predicate))
        return this
    }

    /**
     * This function allows you to access the regular Brigadier builder. The type of
     * `this` in its context will equal the type of [Builder].
     */
    fun brigadier(block: (@DslAnnotations.TopLevel.NodeDsl Builder).() -> Unit): CommandBuilder<Source, Builder> {
        onToBrigadierBuilders += block
        return this
    }

    /**
     * Converts this Kotlin de.hglabor.geschwindigkeit.common.command builder abstraction to an [ArgumentBuilder] of Brigadier.
     * Note that even though this function is public, you probably won't need it in most cases.
     */
    @PublishedApi
    internal fun toBrigadier(): Builder {
        onToBrigadierBuilders.forEach { it(builder) }

        children.forEach {
            @Suppress("UNCHECKED_CAST")
            builder.then(it.toBrigadier() as ArgumentBuilder<Source, *>)
        }
        return builder
    }
}

class LiteralCommandBuilder<Source : CommandSource>(
    private val name: String,
) : CommandBuilder<Source, LiteralArgumentBuilder<Source>>() {

    override val builder = LiteralArgumentBuilder.literal<Source>(name)
}

class ArgumentCommandBuilder<Source : CommandSource, T>(
    private val name: String,
    private val type: ArgumentType<T>,
) : CommandBuilder<Source, RequiredArgumentBuilder<Source, T>>() {

    @PublishedApi
    override val builder = RequiredArgumentBuilder.argument<Source, T>(name, type)

    @PublishedApi
    internal inline fun suggests(
        crossinline block: (context: CommandContext<Source>, builder: SuggestionsBuilder) -> CompletableFuture<Suggestions>,
    ): ArgumentCommandBuilder<Source, T> {
        builder.suggests { context, builder ->
            block(context, builder)
        }
        return this
    }

    /**
     * Suggest the value which is the result of the [suggestionBuilder].
     */
    @DslAnnotations.NodeLevel.SuggestsDsl
    inline fun suggestSingle(crossinline suggestionBuilder: (CommandContext<Source>) -> Any?) =
        suggests { context, builder ->
            builder.applyAny(suggestionBuilder(context))
            builder.buildFuture()
        }

    /**
     * Suggest the value which is the result of the [suggestionBuilder].
     * Additionaly, a separate tooltip associated with the suggestion
     * will be shown as well.
     */
    @DslAnnotations.NodeLevel.SuggestsDsl
    inline fun suggestSingleWithTooltip(crossinline suggestionBuilder: (CommandContext<Source>) -> Pair<Any, Message>?) =
        suggests { context, builder ->
            builder.applyAnyWithTooltip(suggestionBuilder(context))
            builder.buildFuture()
        }

    /**
     * Suggest the entries of the iterable which is the result of the
     * [suggestionsBuilder].
     */
    @DslAnnotations.NodeLevel.SuggestsDsl
    inline fun suggestList(crossinline suggestionsBuilder: (CommandContext<Source>) -> Iterable<Any?>?) =
        suggests { context, builder ->
            builder.applyIterable(suggestionsBuilder(context))
            builder.buildFuture()
        }

    /**
     * Suggest the entries of the iterable which is the result of the
     * [suggestionsBuilder].
     * Additionaly, a separate tooltip associated with each suggestion
     * will be shown as well.
     */
    @DslAnnotations.NodeLevel.SuggestsDsl
    inline fun suggestListWithTooltips(crossinline suggestionsBuilder: (CommandContext<Source>) -> Iterable<Pair<Any?, Message>?>?) =
        suggests { context, builder ->
            builder.applyIterableWithTooltips(suggestionsBuilder(context))
            builder.buildFuture()
        }

    @PublishedApi
    internal fun SuggestionsBuilder.applyAny(any: Any?) {
        when (any) {
            is Int -> suggest(any)
            is String -> suggest(any)
            else -> suggest(any.toString())
        }
    }


    @PublishedApi
    internal fun SuggestionsBuilder.applyAnyWithTooltip(pair: Pair<Any?, Message>?) {
        if (pair == null) return
        val (any, message) = pair
        when (any) {
            is Int -> suggest(any, message)
            is String -> suggest(any, message)
            else -> suggest(any.toString(), message)
        }
    }

    @PublishedApi
    internal fun SuggestionsBuilder.applyIterable(iterable: Iterable<Any?>?) =
        iterable?.forEach { applyAny(it) }

    @PublishedApi
    internal fun SuggestionsBuilder.applyIterableWithTooltips(iterable: Iterable<Pair<Any?, Message>?>?) =
        iterable?.forEach { applyAnyWithTooltip(it) }
}

fun CommandContext<CommandSource>.localPlayers() = (this.source as Player).currentServer.get().server.playersConnected

inline fun command(
    name: String,
    aliases: List<String> = listOf(),
    register: Boolean = true,
    builder: LiteralCommandBuilder<CommandSource>.() -> Unit = {},
): LiteralArgumentBuilder<CommandSource> =
    LiteralCommandBuilder<CommandSource>(name).apply(builder).toBrigadier().apply {
        if (register) Punishments.proxyServer.commandManager.register(BrigadierCommand(this))
        for (alias in aliases) {
            LiteralCommandBuilder<CommandSource>(alias).apply(builder).toBrigadier().apply {
                if (register) Punishments.proxyServer.commandManager.register(BrigadierCommand(this))
            }
        }
    }