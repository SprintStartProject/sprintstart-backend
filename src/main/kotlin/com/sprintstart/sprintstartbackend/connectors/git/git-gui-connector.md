# The `connectors/git/` submodule

The `connectors/` module holds all kinds of extensions for this application. Many of those have the same underlying engine, for example GitHub, Bitbucket, GitLab, ... all use git under the hood.

Knowing that, we can take advantage of it by abstracting away most of the pure git logic, for all of these git GUIs to use, so they only have to worry about fetching additional resources (issues, PRs, ...) and ingestion.

## What each git UI connector needs to implement

### The sinks

Amongst others, there are `GitCommitSink.kt` and `GitFileSink` interfaces to be found under `git/utils/`. These are to be implemented by each respective git UI connector, and define events that determine behavior for the respective data type, for example `GitCommitSink::onCommit(commit: GitCommit)` is supposed to implement functionality that's triggered when a commit was successfully fetched.

### Git source URLs

`GitSourceUrls.kt` provides a public interface, which each git UI connector needs to implement to provide respectively unique URLs for REST traffic fetching and remote source construction.

### Git provider information

In `git/utils/`, there is the `GitProviderDescriptor.kt`, implemented as a data class. Each git UI connector needs to add a class, which provides a descriptor variables, that builds an object of `GitProviderDescriptor`. For an example of this, you can take a look at `connectors/git/bitbucket/utils/BitbucketGitProvider.kt`.

## How to actually use these utils

All these interfaces, implementations etc essentially just make the `GitIngestionEngine.kt` work. The `GitIngestionEngine` handles the whole process of managing and cloning a git repo from a given source, and extracting information, concretely files & commits from the locally managed copy of the repository. It currently (27.09.2026) has 4 public entry points:

* `ingestWorkingTree(coordinates, sink)`: Ingests a whole working tree into the given sink.
* `ingestFileChangesSince(coordinates, fromRevision, sink)`: Ingests all content of files in a repository, that are newer than the given revision into the given sink.
* `ingestCommitsSince(coordinates, sinceRevision, sink)`: Ingests all commits & according infos that happened since the given revision into the given sink.
* `isUpToDate(coordinates)`: Checks a given repository (destination from coordinates) if it had recent changes, returns a bool accordingly.