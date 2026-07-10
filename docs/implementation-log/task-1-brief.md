### Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `server/pom.xml`
- Create: `server/.gitignore`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/java/com/nextstep/NextstepApplication.java`

**Interfaces:**
- Produces: 빌드 가능한 빈 Spring Boot 앱, 포트 8080.

- [ ] **Step 1: pom.xml 작성**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.nextstep</groupId>
  <artifactId>server</artifactId>
  <version>0.1.0</version>
  <name>nextstep-server</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: .gitignore 작성**

```
target/
data/
*.iml
.idea/
application-local.yml
```

- [ ] **Step 3: application.yml 작성**

```yaml
spring:
  application:
    name: nextstep-server
  datasource:
    url: jdbc:h2:file:./data/nextstep;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
  cache:
    type: caffeine
    cache-names: sameCategoryNearbyCount
    caffeine:
      spec: maximumSize=500,expireAfterWrite=10m

sangga:
  api:
    base-url: https://apis.data.go.kr/B553077/api/open/sdsc2
    service-key: ${SANGGA_SERVICE_KEY:}

server:
  port: 8080
```

- [ ] **Step 4: 메인 클래스 작성**

```java
package com.nextstep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class NextstepApplication {
    public static void main(String[] args) {
        SpringApplication.run(NextstepApplication.class, args);
    }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `cd server && mvn -q compile`
Expected: `BUILD SUCCESS`, no output beyond that (quiet mode).

- [ ] **Step 6: git 초기화 + 커밋**

```bash
cd server
git init
git add pom.xml .gitignore src
git commit -m "chore: scaffold Spring Boot project"
```

---

