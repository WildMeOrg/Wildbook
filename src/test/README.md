# Testing in Wildbook

## JUnit for Unit Testing

Wildbook uses [JUnit 5](https://junit.org/junit5/docs/current/user-guide/) for backend unit testing. All unit tests _must run and pass_ in order for changes to be
incorporated into the Wildbook codebase. Tests should be run automatically with maven when executing `mvn clean install`.

**All new code** should have appropriate unit tests written to validate all components of the new code are working as expected. Take care to also test
invalid data cases and failure/exception cases.

Tests should be created under the `src/test/java/org/ecocean/` directories, corresponding to the java class which are testing. The test class name should follow
the convention of adding the suffix `Test` to the java class name, such as `AnnotationTest.java` for tests of code within `Annotation.java`.

Eventually, almost all Wildbook java classes will have a corresponding `*Test.java` class. If one does not exist to add new tests to, it should be created
using the above naming convention. That being said, edits to existing classes may not require full understanding of a class, while testing does. If you choose
not to make a unit test for an existing class, make a note of that choice in the PR and explain why.

There are many tutorials and guides to using JUnit online, such as [this one](https://www.vogella.com/tutorials/JUnit/article.html).

### Running tests without compiling

To test only specific test classes/methods, you can use the following with maven:

```
mvn test                                # run all tests but no compiling

mvn test -Dtest=TestClass1,TestClass2   # only these two classes

mvn test -Dtest=TestClass#method        # test a specific method
```

## Jest and React Testing Library for Frontend Unit Testing

Wildbook uses [Jest] for frontend unit testing, along with React Testing Library to help test React components. Tests should be run automatically with `npm run test`.

All new components and functions should have appropriate unit tests written to ensure they are working as expected. Be sure to test both positive and negative scenarios, including edge cases, invalid data, and failure/exception cases.

Tests should be created in the `/frontend/src/__tests__/` directory, corresponding to the components or functions being tested. The test file name should follow the convention of adding the suffix `.test.js` to the component or function name, such as `LoginPage.test.js` for tests of the `LoginPage.js` component.

### Running Tests
To run all tests, `cd` to `/frontend` and use the following command:

`npm run test`      # runs all tests
You can also run tests in watch mode to re-run tests after changes:

`npm run test -- --watch`
Running Specific Tests
You can run specific test files or even specific tests within a file using Jest's options:

`npm run test -- LoginPage.test.js `                  # run a specific test file
`npm run test -- LoginPage.test.js -t 'renders login'` # run a specific test case\

Writing Tests with React Testing Library
Tests for React components should focus on how the component behaves from the user's perspective, rather than testing implementation details. Testing Library helps you do this by providing tools to interact with your components the way users would.

## Integration Testing

TODO