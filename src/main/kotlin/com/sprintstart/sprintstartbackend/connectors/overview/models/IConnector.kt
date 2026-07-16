package com.sprintstart.sprintstartbackend.connectors.overview.models

/**
 * A scaffold for connectors.
 *
 * # Summary
 *
 * Defines the interface, each connector (github, jira, ...) need to implement to automagically be picked up by the
 * connector overview.
 *
 * Correctly implementing this interface involves introducing a new file `{Module}Connector.kt` to the module, and
 * setting all the values and implementing the functions. An example of this is the `GithubConnector.kt` in the GitHub
 * module. In my opinion, this file should be placed at project root.
 *
 * # This enables
 *
 * * This connector automatically being included in any summaries etc. invoked over the connector overview.
 * * Sources will automatically be patched on batch patches via the connector overview api.
 *
 * @see [com.sprintstart.sprintstartbackend.connectors.github.GithubConnector]
 */
interface IConnector {
    /**
     * The connector's id.
     *
     * If the chosen id already exists, Spring will not be able to launch (due to a init check in the module overview).
     *
     * The chosen id must:
     *
     * * Be lowercase
     * * Not contain spaces
     */
    val id: String

    /**
     * The connector's display name.
     *
     * May be chosen as wanted, no limitations here. Used for labelling in the connector list in the frontend.
     */
    val displayName: String

    /**
     * Retrieve all sources of of this connector.
     *
     * @return a list of all sources of this connector.
     */
    fun getSources(): List<ConnectorSource>

    /**
     * Method signature for patching a connector source.
     *
     * Patching in this context means changing it's status (`enabled`), and therefore controlling it's usage.
     *
     * @param source The source to patch.
     */
    fun patchSource(source: ConnectorSource, newStatus: Boolean)
}
