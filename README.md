# apikana-java
The java side of [apikana](https://github.com/lbovet/apikana).

## Usage

### Create a new API project

Install apikana `npm install -g apikana`.
Run `apikana init`.

This starts an interactive wizard that lets you define the main aspects of the API project.

If you don't like `npm`, just take advantage of the provided parent pom and use this as a template:

````xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.swisspush.apikana</groupId>
        <artifactId>apikana-parent</artifactId>
        <version>0.3.3</version>
    </parent>

    <groupId>myorg.myproject</groupId>
    <artifactId>myapi</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</project>
````

Create `src/openapi/api.yaml`
````yaml
paths:
  /sample/users:
    get:
      operationId: getUser
      responses:
        200:
          description: ok
          schema:
            $ref: "#/definitions/User"
definitions:
  $ref:
    - ../ts/user.ts
````

And create `src/ts/user.ts`
````ts
import {Int} from 'apikana/default-types';

export interface User {
    id: Int
    firstName: string // The given name
    lastName: string // the family name @pattern [A-Z][a-z]*
    age?: Int
}
````

### Create the API documentation

Running `mvn install` on an API project does the following things:

- create `myapi.jar` containing the typescript models, the generated json schemas and the generated java pojos. 
- create `myapi-api.jar`, an executable jar file which opens a browser showing the HTML documentation of the API.  
- start a small HTTP server publishing the HTML documentation of the API at `http://localhost:8333`.

### Plugin documentation

There is a complete [documentation](https://nidi3.github.io/apikana-java/site/plugin-info.html) of the maven plugin.

