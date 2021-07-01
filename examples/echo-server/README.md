# Echo Server Example

This example shows how to create an Echo Server using Java and Maven.
Along with the server, there's a simple Node.js client you can use for
testing purposes.

## Running the server

First you'll have to generate a JAR file:

```
cd examples/echo-server
mvn package
```

And then execute it:

```
java -jar target/ipcsocketexample-0.1.0.jar
```

This should get your server running.

## Running the Node.js client

> If you don't have Node.js installed, please [see how to install it](https://nodejs.org/en/download/) first.

Install dependencies

```
cd examples/echo-server/nodejs-client
npm install
```

And run it:

```
npm start
```
