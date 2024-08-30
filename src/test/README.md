# Testing in Wildbook

## JUnit for Unit Testing

Wildbook uses [JUnit 5](https://junit.org/junit5/docs/current/user-guide/) for unit testing. All unit tests _must run and pass_ in order for changes to be
incorporated into the Wildbook codebase. Tests should be run automatically with maven when executing `mvn clean install`.

**All new code** should have appropriate unit tests written to validate all components of the new code are working as expected. Take care to also test
invalid data cases and failure/exception cases.

Tests should be created under the `src/test/java/org/ecocean/` directories, corresponding to the java class which are testing. The test class name should follow
the convention of adding the suffix `Test` to the java class name, such as `AnnotationTest.java` for tests of code within `Annotation.java`.

Ultimately, almost all Wildbook java classes will have a corresponding `*Test.java` class. If one does not exist to add new tests to, it should be created
using the above naming convention.

There are many tutorials and guides to using JUnit online, such as [this one](https://www.vogella.com/tutorials/JUnit/article.html).

### Running tests without compiling

To test only specific test classes/methods, you can use the following with maven:

```
mvn test                                # run all tests but no compiling

mvn test -Dtest=TestClass1,TestClass2   # only these two classes

mvn test -Dtest=TestClass#method        # test a specific method
```


## Integration Testing, Frontend Testing

TBD / linked elsewhere
