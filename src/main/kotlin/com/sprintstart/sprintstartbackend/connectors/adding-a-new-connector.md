# How to add a new connector

Adding a new connector module is very straightforward.

1. Create a module inside of this **/connectors/ directory, eg. **/connectors/github/**
2. Implement your module like you normally would
3. To make it work with the overview, I suggest creating a new \[Module\]Connector.kt file in the module's root.
4. Inside of that module, put a `@Component` annotated class, which extends `IConnector.kt`.
5. Once `IConnector.kt` is fully implemented (eg. all abstract members got values/implementations), that's it. 
The module should now automatically work with the overview, and the conenctors/sources configurations stuff.

If something is unclear or does not work, I suggest taking a look at existing implementations, eg. `GithubConnector.kt`
in the **/connectors/github/** module.
