// port-lint: source rolling/builder.rs
package io.github.kotlinmania.tracingappender.rolling

/**
 * Errors returned by [Builder.build] or when initializing a rolling file appender.
 */
public class InitError(
    public val context: String,
    cause: Throwable? = null,
) : Exception(if (cause != null) "$context: ${cause.message}" else context, cause) {
    public companion object {
        /**
         * Creates a function returning an [InitError] with the specified [context].
         */
        public fun ctx(context: String): (Throwable) -> InitError = { InitError(context, it) }
    }
}

/**
 * A builder for configuring [RollingFileAppender]s.
 */
public class Builder(
    public var rotation: Rotation = Rotation.NEVER,
    public var prefix: String? = null,
    public var suffix: String? = null,
    public var maxFiles: Int? = null,
) {
    /**
     * Sets the rotation strategy for log files.
     */
    public fun rotation(rotation: Rotation): Builder =
        apply {
            this.rotation = rotation
        }

    /**
     * Sets the prefix for log filenames.
     */
    public fun filenamePrefix(prefix: String): Builder =
        apply {
            this.prefix = prefix.ifEmpty { null }
        }

    /**
     * Sets the suffix for log filenames.
     */
    public fun filenameSuffix(suffix: String): Builder =
        apply {
            this.suffix = suffix.ifEmpty { null }
        }

    /**
     * Keeps the last [n] log files. If 0 is supplied, file pruning is disabled.
     */
    public fun maxLogFiles(n: Int): Builder =
        apply {
            this.maxFiles = if (n > 0) n else null
        }

    /**
     * Builds a new [RollingFileAppender] with the configured parameters.
     */
    public fun build(directory: String): RollingFileAppender = RollingFileAppender.fromBuilder(this, directory)

    public companion object {
        /**
         * Returns a new [Builder] with default parameters.
         */
        public fun new(): Builder = Builder()

        /**
         * Returns a new [Builder] with default parameters.
         */
        public fun default(): Builder = new()
    }
}
