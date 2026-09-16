# Python Docs Disabled Test Inventory

This file tracks Python docs examples of Micronaut JMS that are present but disabled, or that deviate from the
Java example because the direct port currently fails compilation or at runtime.

## Reconciliation

- Last generated active `@Disabled` count: 0.
- Last full-suite command: `./gradlew :micronaut-docs-examples:micronaut-example-python:test -Ppython-ci`
  (the examples use the embedded `vm://` ActiveMQ "Classic" broker, no container is needed).
- Last full-suite result: build successful, 8 tests executed, 0 failures.

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets. Standard
  Micronaut and JMS annotations are generated from imports (`from micronaut.jms.annotations import JMSProducer,
  JMSListener, Queue, Topic, Message, JMSConnectionFactory`, `from micronaut.messaging.annotation import MessageBody,
  MessageHeader`).
- `@JMSProducer` interfaces are abstract classes (`ABC`) whose abstract methods have `...` bodies; `@JMSListener` beans
  are plain classes with `@Queue`/`@Topic` methods. Parameter annotations use `Annotated[str, MessageBody]` and
  `Annotated[str | None, MessageHeader("JMSCorrelationID")]`.
- Java string constants (`ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME`, `JMSHeaders.JMS_CORRELATION_ID`)
  are written as literals (`"activeMqConnectionFactory"`, `"JMSCorrelationID"`) in annotation members.
- Methods that implement or override a Java interface keep the Java (camelCase) name (`onCreated`, `handle`,
  `getOrder`); other methods are snake_case. A Python override of a Java *default* interface method
  (`JMSListenerSuccessHandler.getOrder`) needs `@Executable` to be bridged.
- Python `int` is Java `int` and `float` is `double`; the other primitive header types are declared with the boxed Java
  types (`from java.lang import Byte, Short, Long, Float`).
- Java classes are imported (`from jakarta.jms import Destination`, `from jakarta.jms import Message as JmsMessage`,
  `from org.apache.activemq import ActiveMQConnectionFactory`, `from micronaut.jms.pool import JMSConnectionPool,
  PooledConnection`) and the imported classes are used as runtime type arguments (`getBean`, `containsBean`,
  `getBeansOfType`, `isinstance`); `java.type(...)` is only used for the `jakarta.jms` *interfaces* passed to Java at
  runtime (see "java.type usages" below).
- `@JMSProducer` beans (introduction proxies), `@JMSListener` beans and handlers are injected as Python types
  (`producer: Annotated[TextProducer, Inject]`); the quick start snippet looks the producer up with
  `context.getBean(TextProducer).asPolyglotValue()` like the Java sample.

## Active `@Disabled` Tests

None.

## Workarounds Kept In Snippets

| Target | Reason |
| --- | --- |
| `io.micronaut.jms.docs.AbstractJmsSpec` | Python test classes cannot extend Python base classes, so every spec is a `@MicronautTest` class that declares the ActiveMQ "Classic" `vm://` broker configuration with `@Property` annotations (one embedded broker per spec, like the random broker name of the Java base class). |
| `io.micronaut.jms.docs.PythonRuntimeInitializer` (Java, `src/test/java`) | `@Executable(processOnStartup = true)` processors such as the JMS `@Queue`/`@Topic` listener method processors run before `@Context` beans are initialized, so a Python `@JMSListener` bean would be instantiated before the GraalPy runtime exists (`GraalPy context has not been initialized`); `PythonRuntimeInitializer` creates the GraalPy context bean when an `ExecutableMethodProcessor` is created. |
| `io.micronaut.jms.docs.Await` | Replacement for Awaitility's `await().atMost(...).until(...)` used by the Java tests. |

## Intentionally Unsupported Snippet Targets

None.

## java.type usages

Every remaining `java.type(...)` call carries a `# TODO(python)` comment naming the reason.

| Location | Reason |
| --- | --- |
| `binding/BindingSpec.py` (`ConnectionFactory`) | `containsBean(ConnectionFactory)` with the imported `jakarta.jms.ConnectionFactory` interface fails with `Unsupported operation identifier 'typeHashCode' and object '<jakarta.jms._MicronautJavaType object>'`: an imported third-party *interface* is a placeholder type, not a Java class (imported Java *classes* such as `ActiveMQConnectionFactory` or the `micronaut.jms.pool` shims work as runtime type arguments). |
| `configuration/CustomizeBrokerSpec.py` (`ConnectionFactory`) | Same: `getBeansOfType(ConnectionFactory, Qualifiers.byName(...))` fails with `'typeHashCode'`. |
| `configuration/CustomBrokerSpec.py` (`XAConnection`) | Same for the `jakarta.jms.XAConnection` interface: `isinstance(connection, XAConnection)` is always `False` and `java.instanceof(connection, XAConnection)` fails with `instanceof second argument '_MicronautJavaType' is not a Java class`. |
